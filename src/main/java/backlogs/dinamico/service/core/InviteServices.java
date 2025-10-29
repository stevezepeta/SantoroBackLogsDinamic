package backlogs.dinamico.service.core;

import backlogs.dinamico.infra.security.JwtTokenService;
import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.UserInvite;
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

    public UserInvite create(ObjectId tenantId,
                             String email,
                             List<String> roleCodes,
                             Duration ttl) {

        if (email == null || email.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email_required");

        String emailCi = email.trim().toLowerCase(Locale.ROOT);

        users.findByTenantIdAndEmailIgnoreCase(tenantId, email)
                .ifPresent(u -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "user_exists");
                });

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

    @Transactional
    public Map<String, Object> accept(String token, String name, String password) {

        var inv = invites.findByTokenAndStatus(token, "PENDING")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "invite_not_found"));

        if (Instant.now().isAfter(inv.getExpiresAt()))
            throw new ResponseStatusException(HttpStatus.GONE, "invite_experid");

        ObjectId tenantId = inv.getTenantId();

        var user = users.findByTenantIdAndEmailIgnoreCase(tenantId, inv.getEmail())
                .orElseGet(() -> {
                    var u = new backlogs.dinamico.model.core.User();
                    u.setTenantId(tenantId);
                    u.setEmail(inv.getEmail());
                    u.setName(name);
                    u.setPasswordHash(encoder.encode(password));
                    u.setStatus("active");

                    return users.insert(u);
                });

        // Vincular roles por codigos
        var rs = roles.findByCodeIn(inv.getRoles());
        var links = new ArrayList<backlogs.dinamico.model.core.UserRole>();
        for (Role r : rs) {
            links.add(backlogs.dinamico.model.core.UserRole.builder()
                    .tenantId(tenantId)
                    .userId(user.getId())
                    .roleId(r.getId())
                    .build());
        }
        if (!links.isEmpty()) userRoles.saveAll(links);

        inv.setStatus("ACCEPTED");
        inv.setAcceptedBy(user.getId());
        inv.setUpdateAt(Instant.now());
        invites.save(inv);

        String jwt = tokens.generate(user, rs, tenantId);

        return Map.of(
                "ok", true,
                "token", jwt,
                "user", Map.of(
                        "email", user.getEmail(),
                        "name", user.getName(),
                        "id", user.getId().toHexString()
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
