package backlogs.dinamico.service.core;

import backlogs.dinamico.api.dto.OrgAdminBootstrapReq;
import backlogs.dinamico.model.core.*;
import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class OrganizationAdminService {

    private final OrganizationRepository orgRepo;
    private final RoleRepository roleRepo;
    private final UserRepository userRepo;
    private final UserRoleRepository userRoleRepo;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Map<String, Object> bootstrapOrganizationAdmin(ObjectId orgId, OrgAdminBootstrapReq body) {

        // Validar org
        Organization org = orgRepo.findById(orgId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Organization_not_found"));

        if (body.getEmail() == null || body.getEmail().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "email_required");
        }

        String name = Optional.ofNullable(body.getName()).filter(s -> !s.isBlank()).orElse("Organization Admin");
        String rawPassword = Optional.ofNullable(body.getPassword()).filter(s -> !s.isBlank()).orElse("Temp#12345");

        // Fijar el tenant
        try {
            TenantContext.setTenantId(org.getId());

            // Se aseguran los roles
//            ensureBaseRoles(org.getId());

            String email = Optional.ofNullable(body.getEmail())
                    .map(s -> s.trim().toLowerCase())
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "email_required"));

            // Crear o recuperar un usuario admin
            User admin = userRepo.findByTenantIdAndEmailIgnoreCase(org.getId(), body.getEmail())
                    .orElseGet(() -> {
                        var u = new User();
                        u.setTenantId(org.getId());
                        u.setEmail(body.getEmail().trim().toLowerCase());
                        u.setName(name);
                        u.setStatus("active");
                        u.setPasswordHash(passwordEncoder.encode(rawPassword));
                        u.setCreatedAt(Instant.now());
                        u.setUpdatedAt(Instant.now());
                        return userRepo.save(u);
                    });

            // Asegurar asignacion de un Admin
            Role adminRole = roleRepo.findByTenantIdAndCode(org.getId(), RoleCode.ORG_ADMIN)
                    .orElseThrow(() -> new ResponseStatusException(INTERNAL_SERVER_ERROR, "admin_role_missing"));

            boolean hasAdmin = userRoleRepo.existsByTenantIdAndUserIdAndRoleId(org.getId(), admin.getId(), adminRole.getId());
            if (!hasAdmin) {
                var ur = new UserRole();
                ur.setTenantId(org.getId());
                ur.setUserId(admin.getId());
                ur.setRoleId(adminRole.getId());
                ur.setCreatedAt(Instant.now());
                ur.setUpdatedAt(Instant.now());
                userRoleRepo.save(ur);
            }

            // Respuesta
            return Map.of(
                    "organizationId", org.getId().toHexString(),
                    "adminUser", Map.of(
                            "id", admin.getId().toHexString(),
                            "email", admin.getEmail(),
                            "name", admin.getName()
                    ),
                    "rolesEnsured", List.of("ADMIN","VIEWER","AUDITOR","AGENT"),
                    "tempPasswordHint", body.getPassword() == null || body.getPassword().isBlank() ? "Temp#12345" : "(provided)"
            );

        } finally {
            TenantContext.clear();
        }
    }

//    private void ensureBaseRoles(ObjectId tenantId) {
//        ensureRole(tenantId, "ADMIN",  "ADMIN",  "Administrador del tenant");
//        ensureRole(tenantId, "VIEWER", "VIEWER", "Solo lectura de logs");
//        ensureRole(tenantId, "AUDITOR","AUDITOR","Lectura + exportaciones");
//        ensureRole(tenantId, "AGENT",  "AGENT",  "Operador biométrico");
//    }

    private void ensureRole(ObjectId tenantId, RoleCode code, String name, String description) {
        roleRepo.findByTenantIdAndCode(tenantId, code).orElseGet(() -> {
            var r = new Role();
            r.setTenantId(tenantId);
            r.setCode(code);
            r.setName(name);
            r.setDescription(description);
            r.setCreatedAt(Instant.now());
            r.setUpdatedAt(Instant.now());
            return roleRepo.save(r);
        });
    }

}
