package backlogs.dinamico.controller.core;

import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.model.core.UserRole;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/core")
@RequiredArgsConstructor
@Valid
public class BootstrapController {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/bootstrap-admin")
    public ResponseEntity<?> bootstrapAdmin(@RequestHeader("X-Tenant")ObjectId tenantId,
                                            @Valid @RequestBody BootstrapReq req) {

        long c = userRepo.countByTenantId(tenantId);
        if (c > 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("ok", false, "reason", "tenant_already_initialized"));
        }

        // Creando el rol de Admin
        Role admin = roleRepo.findByTenantIdAndCode(tenantId, "ADMIN")
                .orElseGet(() -> roleRepo.save(
                        Role.builder()
                                .tenantId(tenantId)
                                .code("ADMIN")
                                .name("ADMIN")
                                .description("Administrador del tenant")
                                .build()
                ));

        // Se crea el usuario Admin
        User user = User.builder()
                .tenantId(tenantId)
                .email(req.email().trim().toLowerCase())
                .name(req.name().trim())
                .passwordHash(passwordEncoder.encode(req.password()))
                .status("active")
                .build();

        user = userRepo.save(user);


        // Se vincula el admin
        userRoleRepo.save(UserRole.builder()
                .tenantId(tenantId)
                .userId(user.getId())
                .roleId(admin.getId())
                .build());

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "user", Map.of("id", user.getId(), "email", user.getEmail(), "name", user.getName()),
                "roles", new String[]{"ADMIN"}
        ));


    }

    public record BootstrapReq(
            @NotBlank
            @Email
            String email,
            @NotBlank
            String name,
            @NotBlank
            String password
    ) {

    }

}
