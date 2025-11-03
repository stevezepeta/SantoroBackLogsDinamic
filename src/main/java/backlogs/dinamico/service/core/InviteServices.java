package backlogs.dinamico.service.core;

import backlogs.dinamico.infra.security.JwtTokenService;
import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.model.core.UserInvite;
import backlogs.dinamico.model.core.UserRole;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserInviteRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InviteServices {

    private final UserInviteRepository invites;
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder encoder;
    private final JwtTokenService tokens;

    // Se esta creando una invitacion PENDING
    public UserInvite create(ObjectId tenantId,
                             String email,
                             List<String> roleCodes,
                             Duration ttl) {

        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email_required");
        }

        String emailNorm = email.trim();
        String emailCi = email.trim().toLowerCase(Locale.ROOT);

        // No se permite invitar usuarios existentes de la misma organization
        users.findByTenantIdAndEmailIgnoreCase(tenantId, emailNorm)
                .ifPresent(u -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "user_exists");
                });

        // No se permite una segunda invitacion pendiente para el mismo email
        invites.findFirstByTenantIdAndEmailCiAndStatus(tenantId, emailCi, "PENDING")
                .ifPresent(I -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "invite_already_sent");
                });

        String token = randomToken();
        Instant now = Instant.now();

        var inv = UserInvite.builder()
                .tenantId(tenantId)
                .email(email.trim())
                .emailCi(emailCi)
                .roles((roleCodes == null || roleCodes.isEmpty()) ? List.of("AGENT") : roleCodes)
                .token(token)
                .expiresAt(now.plus(ttl == null ? Duration.ofHours(24) : ttl))
                .status("PENDING")
                .createdAt(now)
                .updateAt(now)
                .build();

        return invites.save(inv);
    }

    // Acepta invitacion pendiente
    @Transactional
    public Map<String, Object> accept(String token, String name, String password) {

        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token_required");
        }
        if (!StringUtils.hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name_required");
        }
        if (!StringUtils.hasText(password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password_required");
        }

        var inv = invites.findByTokenAndStatus(token, "PENDING")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "invite_not_found"));

        if (Instant.now().isAfter(inv.getExpiresAt())) {

            // Se puede borrar/archivar la invitacion
            inv.setStatus("EXPIRED");
            inv.setUpdateAt(Instant.now());
            invites.save(inv);

            throw new ResponseStatusException(HttpStatus.GONE, "invite_experid");
        }

        ObjectId tenantId = inv.getTenantId();

        // Crear o recuperar usuarios de la misma organization
        User user = users.findByTenantIdAndEmailIgnoreCase(tenantId, inv.getEmail())
                .orElseGet(() -> {
                    var u = new User();
                    u.setTenantId(tenantId);
                    u.setEmail(inv.getEmail());
                    u.setName(name.trim());
                    u.setPasswordHash(encoder.encode(password));
                    u.setStatus("active");
                    u.setCreatedAt(Instant.now());
                    u.setUpdatedAt(Instant.now());
                    return users.insert(u);
                });

        // Obtencion de roles por Tenant + code
        List<String> codes = Optional.ofNullable(inv.getRoles())
                .filter(list -> !list.isEmpty())
                .orElseGet(() -> List.of("VIEWER"));

        List<Role> rs = roles.findByTenantIdAndCodeIn(tenantId, codes);

        if (rs.isEmpty()) {
            rs = roles.findByTenantIdAndCodeIn(tenantId, List.of("VIEWER"));
        }

        for (Role r : rs) {
            boolean exists = userRoles.existsByTenantIdAndUserIdAndRoleId(tenantId, user.getId(), r.getId());
            if (!exists) {
                var link = new backlogs.dinamico.model.core.UserRole();
                link.setTenantId(tenantId);
                link.setUserId(user.getId());
                link.setRoleId(r.getId());
                // Si tu BaseEntity maneja fechas por callback/auditing, puedes omitir estas dos líneas.
                link.setCreatedAt(Instant.now());
                link.setUpdatedAt(Instant.now());
                userRoles.save(link);
            }
        }

        // Se marca la invitacion como aceptada
        inv.setStatus("ACCEPTED");
        inv.setAcceptedBy(user.getId());
        inv.setUpdateAt(Instant.now());
        invites.save(inv);

        // Se emite el JWT de sesion
        String jwt = tokens.generate(user, rs, tenantId);

        return Map.of(
                "token", jwt,
                "user", Map.of(
                        "id", user.getId().toHexString(),
                        "email", user.getEmail(),
                        "name", user.getName()
                ),
                "roles", rs.stream().map(Role::getCode).toList(),
                "tenantId", tenantId.toHexString()
        );

    }

    private String randomToken() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

}
