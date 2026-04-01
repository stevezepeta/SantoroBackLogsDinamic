package backlogs.dinamico.controller.admin;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.InviteCreateRequest;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.model.core.RoleCode;
import backlogs.dinamico.model.core.UserInvite;
import backlogs.dinamico.model.core.UserRole;
import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.service.core.InviteServices;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Tag(
        name = "Admin - Invites",
        description = """
                Gestión de invitaciones para usuarios.

                **Reglas**
                - La invitación siempre se crea en estado `PENDING`.
                - Solo se permite **1 rol** por invitación.
                - Si el rol es **SYSTEM_MANAGER**, se requiere `systems` con mínimo 1 elemento.
                - Para **VIEWER**, se pueden restringir los logs visibles con `logFilters`.
                """
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/invites")
@RequiredArgsConstructor
public class AdminInviteController {

    private final InviteServices       invites;
    private final OrganizationRepository orgRepo;

    @Operation(
            summary = "Crear invitación",
            description = "Genera token + link de invitación. Para VIEWER se puede incluir `logFilters`.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = InviteCreateRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Invite VIEWER con filtros de logs",
                                            value = """
                                                    {
                                                      "email": "cliente@empresa.com",
                                                      "roles": ["VIEWER"],
                                                      "systems": ["TRUSTVALUE"],
                                                      "ttlHours": 48,
                                                      "logFilters": {
                                                        "allowedOutcomes":    ["SUCCESS", "APPROVED"],
                                                        "allowedStatuses":    ["OK"],
                                                        "allowedSeverities":  ["INFO"],
                                                        "allowedEventTypes":  []
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Invite VIEWER sin filtros (ve todo)",
                                            value = """
                                                    {
                                                      "email": "viewer@empresa.com",
                                                      "roles": ["VIEWER"],
                                                      "systems": ["TRUSTVALUE"],
                                                      "ttlHours": 48
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Invite SYSTEM_MANAGER",
                                            value = """
                                                    {
                                                      "email": "manager@empresa.com",
                                                      "roles": ["SYSTEM_MANAGER"],
                                                      "systems": ["PASSPORT", "ELYCTIS"],
                                                      "ttlHours": 48
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    )
    @PostMapping
    @PreAuthorize("hasAuthority('PERM_USERS_MANAGE') or hasAuthority('PERM_ROLES_ASSIGN')")
    public ApiResponse<Map<String, Object>> createInvite(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            @AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody InviteCreateRequest req
    ) {
        long ttl = (req.getTtlHours() == null || req.getTtlHours() <= 0)
                ? 48L : req.getTtlHours().longValue();

        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);

        RoleCode role = req.getRoles().get(0);
        List<String> roles = List.of(role.name());

        List<String> systems = (req.getSystems() == null) ? List.of()
                : req.getSystems().stream()
                .filter(StringUtils::hasText)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        // ── logFilters: mapear desde el request ───────────────────────────────
        UserRole.LogFilter logFilters = buildLogFilters(req);

        UserInvite inv = invites.create(
                me.getTenantId(),
                email,
                roles,
                systems,
                Duration.ofHours(ttl),
                logFilters          // ← nuevo parámetro
        );

        Map<String, Object> orgBlock = orgRepo.findById(me.getTenantId())
                .<Map<String, Object>>map(o -> Map.of(
                        "id",   o.getId().toHexString(),
                        "name", o.getName()
                ))
                .orElseGet(() -> Map.of(
                        "id",   me.getTenantId().toHexString(),
                        "name", "(unknown)"
                ));

        // Respuesta limpia — sin inviteToken ni inviteLink (el OTP llega al email)
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("organization", orgBlock);
        data.put("email",        inv.getEmail());
        data.put("roles",        inv.getRoles());
        data.put("systems",      inv.getSystems());
        data.put("logFilters",   inv.getLogFilters() != null ? Map.of(
                "allowedOutcomes",   inv.getLogFilters().getAllowedOutcomes(),
                "allowedStatuses",   inv.getLogFilters().getAllowedStatuses(),
                "allowedSeverities", inv.getLogFilters().getAllowedSeverities(),
                "allowedEventTypes", inv.getLogFilters().getAllowedEventTypes()
        ) : null);
        data.put("expiresAt",    inv.getExpiresAt());
        data.put("message",      "Código de verificación enviado al email del invitado.");

        return ApiResponse.created("Invitación generada", "admin_invite_created", data);
    }

    // ── Helper: construir LogFilter desde el request ──────────────────────────

    private static UserRole.LogFilter buildLogFilters(InviteCreateRequest req) {
        if (req.getLogFilters() == null) return null;

        InviteCreateRequest.LogFilterRequest lf = req.getLogFilters();

        // Si todos los campos están vacíos → no hay restricción
        boolean hasAny = isNotEmpty(lf.getAllowedOutcomes())
                || isNotEmpty(lf.getAllowedStatuses())
                || isNotEmpty(lf.getAllowedSeverities())
                || isNotEmpty(lf.getAllowedEventTypes());

        if (!hasAny) return null;

        return UserRole.LogFilter.builder()
                .allowedOutcomes(toUpperSet(lf.getAllowedOutcomes()))
                .allowedStatuses(toUpperSet(lf.getAllowedStatuses()))
                .allowedSeverities(toUpperSet(lf.getAllowedSeverities()))
                .allowedEventTypes(toUpperSet(lf.getAllowedEventTypes()))
                .build();
    }

    private static java.util.Set<String> toUpperSet(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return new java.util.HashSet<>();
        return list.stream()
                .filter(StringUtils::hasText)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
    }

    private static boolean isNotEmpty(java.util.List<String> list) {
        return list != null && !list.isEmpty();
    }

    // ── Swagger schemas ───────────────────────────────────────────────────────

    @Schema(name = "InviteCreatedEnvelope")
    public static class InviteCreatedEnvelope {
        @Schema(example = "true")                    public boolean ok;
        @Schema(example = "created")                 public String code;
        @Schema(example = "Invitación generada")     public String message;
        @Schema(example = "admin_invite_created")    public String path;
        @Schema(example = "2026-02-09T20:10:10.123Z") public String timestamp;
        public InviteCreatedData data;
    }

    @Schema(name = "InviteCreatedData")
    public static class InviteCreatedData {
        public OrganizationBlock organization;
        @Schema(example = "viewer@empresa.com") public String email;
        public List<String> roles;
        public List<String> systems;
        public Object logFilters;
        @Schema(example = "AbCdEf123...") public String inviteToken;
        @Schema(example = "https://tu-ui/invite?token=AbCdEf123...") public String inviteLink;
        @Schema(example = "2026-02-11T20:10:10.123Z") public String expiresAt;
    }

    @Schema(name = "OrganizationBlock")
    public static class OrganizationBlock {
        @Schema(example = "696a7730dc3d6cd1487cdd3e") public String id;
        @Schema(example = "Empresa X")                public String name;
    }
}