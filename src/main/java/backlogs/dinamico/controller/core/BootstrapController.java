package backlogs.dinamico.controller.core;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.model.core.Organization;
import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.model.core.UserRole;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

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

    // recomendado: validar que el tenant exista
    private final OrganizationRepository orgRepo;

    // opcional recomendado: evita que cualquiera inicialice con solo saber el tenantId
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

        // (opcional) proteger bootstrap con secreto
        if (StringUtils.hasText(bootstrapSecret)) {
            if (!StringUtils.hasText(xSecret) || !bootstrapSecret.equals(xSecret)) {
                throw new ResponseStatusException(UNAUTHORIZED, "invalid_bootstrap_secret");
            }
        }

        // validar que exista la organización
        Organization org = orgRepo.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "tenant_not_found"));

        // evitar re-inicialización
        long c = userRepo.countByTenantId(tenantId);
        if (c > 0) {
            throw new ResponseStatusException(FORBIDDEN, "tenant_already_initialized");
        }

        try {
            // crear roles base (idempotente)
            List<Role> baseRoles = ensureBaseRoles(tenantId);

            Role adminRole = baseRoles.stream()
                    .filter(r -> "ADMIN".equals(r.getCode()))
                    .findFirst()
                    .orElseThrow();

            // crear usuario admin
            User user = User.builder()
                    .tenantId(tenantId)
                    .email(req.email().trim().toLowerCase())
                    .name(req.name().trim())
                    .passwordHash(passwordEncoder.encode(req.password()))
                    .status("active")
                    .build();

            user = userRepo.save(user);

            // vincular rol ADMIN (y opcionalmente TENANT_OWNER)
            userRoleRepo.save(UserRole.builder()
                    .tenantId(tenantId)
                    .userId(user.getId())
                    .roleId(adminRole.getId())
                    .build());

            var ownerOpt = baseRoles.stream()
                    .filter(r -> "TENANT_OWNER".equals(r.getCode()))
                    .findFirst();

            if (ownerOpt.isPresent()) {
                Role ownerRole = ownerOpt.get();
                userRoleRepo.save(UserRole.builder()
                        .tenantId(tenantId)
                        .userId(user.getId())
                        .roleId(ownerRole.getId())
                        .build());
            }


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
                    "roles", baseRoles.stream().map(Role::getCode).toList()
            );

            return ResponseEntity.ok(ApiResponse.ok("Tenant inicializado", "bootstrap_admin", data));

        } catch (DuplicateKeyException dup) {
            // si tienes índices unique, esto evita carreras
            throw new ResponseStatusException(CONFLICT, "duplicate_key_bootstrap");
        }
    }

    private List<Role> ensureBaseRoles(ObjectId tenantId) {
        return List.of(
                ensureRole(tenantId, "ADMIN", "Administrador", "Administrador del tenant"),
                ensureRole(tenantId, "TENANT_OWNER", "Propietario", "Owner del tenant (gestión de usuarios/roles)"),
                ensureRole(tenantId, "EXEC", "Ejecutivo", "Dashboard ejecutivo y KPIs"),
                ensureRole(tenantId, "OPS", "Operación", "Operación y monitoreo"),
                ensureRole(tenantId, "SUPPORT", "Soporte", "Soporte y troubleshooting"),
                ensureRole(tenantId, "AUDITOR", "Auditor", "Consulta y auditoría")
        );
    }

    private Role ensureRole(ObjectId tenantId, String code, String name, String description) {
        return roleRepo.findByTenantIdAndCode(tenantId, code)
                .orElseGet(() -> roleRepo.save(Role.builder()
                        .tenantId(tenantId)
                        .code(code)
                        .name(name)
                        .description(description)
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
