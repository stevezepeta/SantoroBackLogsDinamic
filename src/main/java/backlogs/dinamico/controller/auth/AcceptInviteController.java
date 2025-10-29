package backlogs.dinamico.controller.auth;

import backlogs.dinamico.infra.security.JwtTokenService;
import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.model.core.UserRole;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AcceptInviteController {

    private final JwtTokenService tokens;
    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/accept-invite")
    public ResponseEntity<?> accept(@RequestBody AcceptInviteReq  req) {

        // verificacion de la invitacion del token
        Claims c = tokens.verifyInvite(req.token());
        String email = c.getSubject();
        String tenHex = c.get("tenant", String.class);
        @SuppressWarnings("unchecked")
        List<String> rolesCodes = c.get("roles", List.class);
        ObjectId tenantId = new ObjectId(tenHex);

        User user = userRepo.findByTenantIdAndEmailIgnoreCase(tenantId, email)
                .orElseGet(() -> User.builder()
                        .tenantId(tenantId)
                        .email(email)
                        .build());

        user.setName(req.name());
        user.setStatus("active");
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user = userRepo.save(user);

        // Asignacion de roles
        userRoleRepo.deleteByTenantIdAndUserId(tenantId, user.getId());

        List<Role> roles = roleRepo.findByCodeIn(rolesCodes);
        for (Role r : roles) {
            userRoleRepo.save(UserRole.builder()
                    .tenantId(tenantId)
                    .userId(user.getId())
                    .roleId(r.getId())
                    .build());
        }

        // Token de sesion
        String loginJwt = tokens.generate(user, roles, tenantId);

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "user", Map.of(
                        "id",  user.getId().toHexString(),
                        "email", user.getEmail(),
                        "name", user.getName()
                ),
                "roles", rolesCodes == null ? List.of() : rolesCodes,
                "tenantId", tenantId.toHexString(),
                "token", loginJwt
        ));

    }

    public record AcceptInviteReq(String token, String name, String password) {

    }

}
