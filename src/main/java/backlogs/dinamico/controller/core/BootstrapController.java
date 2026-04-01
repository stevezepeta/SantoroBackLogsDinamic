package backlogs.dinamico.controller.core;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.model.core.*;
import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping(value = "/api/core", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class BootstrapController {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationRepository orgRepo;

    @Value("${security.bootstrap.secret:}")
    private String bootstrapSecret;

    @PostMapping("/bootstrap-admin")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bootstrapAdmin(
            @RequestHeader(name = "X-Tenant", required = false) String xTenant,
            @RequestHeader(name = "X-Tenant-Id", required = false) String xTenantId,
            @RequestHeader(name = "X-Bootstrap-Secret", required = false) String xSecret,
            @Valid @RequestBody BootstrapReq req
    ) {
        ObjectId tenantId = parseTenantId(firstNonBlank(xTenant, xTenantId));

        if (StringUtils.hasText(bootstrapSecret)) {
            if (!StringUtils.hasText(xSecret) || !bootstrapSecret.equals(xSecret)) {
                throw new ResponseStatusException(UNAUTHORIZED, "invalid_bootstrap_secret");
            }
        }

        Organization org = orgRepo.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "tenant_not_found"));

        long c = userRepo.countByTenantId(tenantId);
        if (c > 0) {
            throw new ResponseStatusException(FORBIDDEN, "tenant_already_initialized");
        }

        try {
            List<Role> baseRoles = ensureBaseRoles(tenantId);

            Role ownerRole = baseRoles.stream()
                    .filter(r -> r.getCode() == RoleCode.ORG_OWNER)
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(INTERNAL_SERVER_ERROR, "missing_role_org_owner"));

            Role adminRole = baseRoles.stream()
                    .filter(r -> r.getCode() == RoleCode.ORG_ADMIN)
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(INTERNAL_SERVER_ERROR, "missing_role_org_admin"));

            User user = User.builder()
                    .tenantId(tenantId)
                    .email(req.email().trim().toLowerCase())
                    .name(req.name().trim())
                    .passwordHash(passwordEncoder.encode(req.password()))
                    .status("active")
                    .build();

            user = userRepo.save(user);

            // ORG_OWNER
            userRoleRepo.save(UserRole.builder()
                    .tenantId(tenantId)
                    .userId(user.getId())
                    .roleId(ownerRole.getId())
                    .allowedSystems(Set.of()) // orgWide => vacío
                    .build());

            // ORG_ADMIN (opcional, pero útil)
            userRoleRepo.save(UserRole.builder()
                    .tenantId(tenantId)
                    .userId(user.getId())
                    .roleId(adminRole.getId())
                    .allowedSystems(Set.of())
                    .build());

            Map<String, Object> data = Map.of(
                    "organization", Map.of(
                            "id", org.getId().toHexString(),
                            "name", org.getName()
                    ),
                    "user", Map.of(
                            "id", user.getId().toHexString(),
                            "email", user.getEmail(),
                            "name", user.getName()
                    ),
                    "rolesCreated", baseRoles.stream().map(r -> r.getCode().name()).toList()
            );

            return ResponseEntity.ok(ApiResponse.ok("Tenant inicializado", "bootstrap_admin", data));

        } catch (DuplicateKeyException dup) {
            throw new ResponseStatusException(CONFLICT, "duplicate_key_bootstrap");
        }
    }

    private List<Role> ensureBaseRoles(ObjectId tenantId) {
        return List.of(
                ensureRole(tenantId,
                        RoleCode.ORG_OWNER,
                        "Owner",
                        "Dueño del tenant (gestión total)",
                        true,
                        false,
                        Set.of(
                                PermissionCode.USERS_MANAGE,
                                PermissionCode.ROLES_ASSIGN,
                                PermissionCode.SETTINGS_MANAGE,
                                PermissionCode.LOG_READ,
                                PermissionCode.LOG_EXPORT
                        )
                ),
                ensureRole(tenantId,
                        RoleCode.ORG_ADMIN,
                        "Admin",
                        "Admin del tenant (operación + catálogos)",
                        true,
                        false,
                        Set.of(
                                PermissionCode.SETTINGS_MANAGE,
                                PermissionCode.LOG_READ,
                                PermissionCode.LOG_EXPORT
                        )
                ),
                ensureRole(tenantId,
                        RoleCode.EXEC,
                        "Ejecutivo",
                        "Dashboard ejecutivo y KPIs",
                        true,
                        false,
                        Set.of(PermissionCode.LOG_READ, PermissionCode.LOG_EXPORT)
                ),
                ensureRole(tenantId,
                        RoleCode.SUPPORT,
                        "Soporte",
                        "Soporte y troubleshooting",
                        true,
                        false,
                        Set.of(PermissionCode.LOG_READ)
                ),
                ensureRole(tenantId,
                        RoleCode.AUDITOR,
                        "Auditor",
                        "Consulta y auditoría",
                        true,
                        false,
                        Set.of(PermissionCode.LOG_READ, PermissionCode.LOG_EXPORT)
                ),
                ensureRole(tenantId,
                        RoleCode.SYSTEM_MANAGER,
                        "Jefe de sistema",
                        "Solo logs de systems asignados",
                        false,
                        true,
                        Set.of(PermissionCode.LOG_READ, PermissionCode.LOG_EXPORT)
                ),
                ensureRole(tenantId,
                        RoleCode.VIEWER,
                        "Viewer",
                        "Solo lectura (sin export)",
                        true,
                        false,
                        Set.of(PermissionCode.LOG_READ)
                ),
                ensureRole(tenantId,
                        RoleCode.SUPPORT_TI,
                        "Soporte TI",
                        "Soporte técnico plataforma (catálogos + troubleshooting)",
                        true,
                        false,
                        Set.of(
                                PermissionCode.SETTINGS_MANAGE,
                                PermissionCode.LOG_READ
                        )
                )

                );
    }

    private Role ensureRole(ObjectId tenantId,
                            RoleCode code,
                            String name,
                            String description,
                            boolean orgWide,
                            boolean systemScoped,
                            Set<PermissionCode> perms) {

        return roleRepo.findByTenantIdAndCode(tenantId, code)
                .orElseGet(() -> roleRepo.save(Role.builder()
                        .tenantId(tenantId)
                        .code(code)
                        .name(name)
                        .description(description)
                        .orgWide(orgWide)
                        .systemScoped(systemScoped)
                        .permissions(perms)
                        .build()));
    }

    private static ObjectId parseTenantId(String tenantHex) {
        if (!StringUtils.hasText(tenantHex) || !ObjectId.isValid(tenantHex)) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid_tenant_header");
        }
        return new ObjectId(tenantHex.trim());
    }

    private static String firstNonBlank(String... xs) {
        if (xs == null) return null;
        for (String x : xs) {
            if (StringUtils.hasText(x)) return x.trim();
        }
        return null;
    }

    public record BootstrapReq(
            @NotBlank @Email String email,
            @NotBlank String name,
            @NotBlank @Size(min = 8) String password
    ) {}
}
