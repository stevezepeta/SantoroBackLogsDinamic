package backlogs.dinamico.controller.admin;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.InviteCreateRequest;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.model.core.RoleCode;
import backlogs.dinamico.model.core.UserInvite;
import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.service.core.InviteServices;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
                """
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/invites")
@RequiredArgsConstructor
public class AdminInviteController {

    private final InviteServices invites;
    private final OrganizationRepository orgRepo;


    @Operation(
            summary = "Crear invitación",
            description = "Genera token + link de invitación para el usuario, con TTL configurable.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = InviteCreateRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Invite VIEWER (systems opcional)",
                                            value = """
                                                    {
                                                      "email": "viewer@empresa.com",
                                                      "roles": ["VIEWER"],
                                                      "ttlHours": 48
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Invite SYSTEM_MANAGER (systems obligatorio)",
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
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Invitación creada",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = InviteCreatedEnvelope.class),
                            examples = @ExampleObject(
                                    name = "Created",
                                    value = """
                                            {
                                              "ok": true,
                                              "code": "created",
                                              "message": "Invitación generada",
                                              "path": "admin_invite_created",
                                              "timestamp": "2026-02-09T20:10:10.123Z",
                                              "data": {
                                                "organization": { "id": "696a7730dc3d6cd1487cdd3e", "name": "Empresa X" },
                                                "email": "viewer@empresa.com",
                                                "roles": ["VIEWER"],
                                                "systems": [],
                                                "inviteToken": "AbCdEf123...",
                                                "inviteLink": "https://tu-ui/invite?token=AbCdEf123...",
                                                "expiresAt": "2026-02-11T20:10:10.123Z"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos (validación)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "success": false,
                                              "error": "Datos Invalidos",
                                              "fields": {
                                                "roles": "Solo se permite un rol por invitacion",
                                                "systemsValidForRole": "systems es obligatorio (mínimo 1) cuando el rol es SYSTEM_MANAGER"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Usuario ya existe o invitación ya enviada",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "ok": false,
                                              "code": "conflict",
                                              "message": "invite_already_sent",
                                              "path": "admin_invite_created",
                                              "timestamp": "2026-02-09T20:10:10.123Z",
                                              "data": null
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "No autenticado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('PERM_USERS_MANAGE') or hasAuthority('PERM_ROLES_ASSIGN')")
    public ApiResponse<Map<String, Object>> createInvite(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            @AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody InviteCreateRequest req
    ) {
        // TTL default 48h si no viene
        long ttl = (req.getTtlHours() == null || req.getTtlHours() <= 0) ? 48L : req.getTtlHours().longValue();

        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);

        RoleCode role = req.getRoles().get(0);

        List<String> roles = List.of(role.name());

        // systems dinámicos: normaliza a UPPER + trim + distinct
        List<String> systems = (req.getSystems() == null)
                ? List.of()
                : req.getSystems().stream()
                .filter(StringUtils::hasText)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        UserInvite inv = invites.create(
                me.getTenantId(),
                email,
                roles,
                systems,
                Duration.ofHours(ttl)
        );

        Map<String, Object> orgBlock = orgRepo.findById(me.getTenantId())
                .<Map<String, Object>>map(o -> Map.of(
                        "id", o.getId().toHexString(),
                        "name", o.getName()
                ))
                .orElseGet(() -> Map.of(
                        "id", me.getTenantId().toHexString(),
                        "name", "(unknown)"
                ));

        String inviteLink = invites.buildInviteLink(inv);

        Map<String, Object> data = Map.of(
                "organization", orgBlock,
                "email",        inv.getEmail(),
                "roles",        inv.getRoles(),
                "systems",      inv.getSystems(),
                "inviteToken",  inv.getToken(),
                "inviteLink",   inviteLink,
                "expiresAt",    inv.getExpiresAt()
        );

        return ApiResponse.created("Invitación generada", "admin_invite_created", data);
    }


    // ======= Swagger Schemas (solo para documentación) =======
    @Schema(name = "InviteCreatedEnvelope", description = "Respuesta estándar al crear invitación")
    public static class InviteCreatedEnvelope {
        @Schema(example = "true") public boolean ok;
        @Schema(example = "created") public String code;
        @Schema(example = "Invitación generada") public String message;
        @Schema(example = "admin_invite_created") public String path;
        @Schema(example = "2026-02-09T20:10:10.123Z") public String timestamp;
        public InviteCreatedData data;
    }

    @Schema(name = "InviteCreatedData", description = "Datos de invitación generada")
    public static class InviteCreatedData {
        public OrganizationBlock organization;
        @Schema(example = "viewer@empresa.com") public String email;
        public List<String> roles;
        public List<String> systems;
        @Schema(example = "AbCdEf123...") public String inviteToken;
        @Schema(example = "https://tu-ui/invite?token=AbCdEf123...") public String inviteLink;
        @Schema(example = "2026-02-11T20:10:10.123Z") public String expiresAt;
    }

    @Schema(name = "OrganizationBlock", description = "Tenant / Organización")
    public static class OrganizationBlock {
        @Schema(example = "696a7730dc3d6cd1487cdd3e") public String id;
        @Schema(example = "Empresa X") public String name;
    }
}
