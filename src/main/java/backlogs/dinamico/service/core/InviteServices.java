package backlogs.dinamico.service.core;

import backlogs.dinamico.infra.security.JwtTokenService;
import backlogs.dinamico.model.core.*;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserInviteRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import backlogs.dinamico.security.auth.AuthorizationContext;
import backlogs.dinamico.security.auth.AuthorizationContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteServices {

    private final UserInviteRepository invites;
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder encoder;
    private final JwtTokenService tokens;

    private final AuthorizationContextService authz;

    @Value("${app.frontend.base-url:http://187.188.66.56:8032}")
    private String frontendBaseUrl;

    // Se esta creando una invitacion PENDING
    public UserInvite create(ObjectId tenantId,
                             String email,
                             List<String> roleCodes,
                             List<String> systems,
                             Duration ttl) {

        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email_required");
        }

        String emailNorm = email.trim();
        String emailCi = emailNorm.toLowerCase(Locale.ROOT);

        users.findByTenantIdAndEmailIgnoreCase(tenantId, emailNorm)
                .ifPresent(u -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "user_exists"); });

        invites.findFirstByTenantIdAndEmailCiAndStatus(tenantId, emailCi, "PENDING")
                .ifPresent(I -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "invite_already_sent"); });

        List<String> safeRoles = (roleCodes == null || roleCodes.isEmpty()) ? List.of("AGENT") : roleCodes;

        List<String> safeSystems = (systems == null) ? List.of() : systems.stream()
                .filter(StringUtils::hasText)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        String token = randomToken();
        Instant now = Instant.now();

        var inv = UserInvite.builder()
                .tenantId(tenantId)
                .email(emailNorm)
                .emailCi(emailCi)
                .roles(safeRoles)
                .systems(safeSystems) // NUEVO
                .token(token)
                .expiresAt(now.plus(ttl == null ? Duration.ofHours(24) : ttl))
                .status("PENDING")
                .createdAt(now)
                .updateAt(now)
                .build();

        UserInvite saved = invites.save(inv);

        String inviteLink = buildInviteLink(saved);
        log.info("[INVITE] Invitación creada email={} tenant={} roles={} systems={} token={} link={}",
                emailCi, tenantId.toHexString(), safeRoles, safeSystems, token, inviteLink);

        return saved;
    }

    // Se contruye el link completo
    public String buildInviteLink(UserInvite invite) {
        if (invite == null || !StringUtils.hasText(invite.getToken())) {
            return null;
        }

        return frontendBaseUrl + "/accept-invite?token=" + invite.getToken();
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

        List<RoleCode> codes = Optional.ofNullable(inv.getRoles())
                .orElseGet(List::of)
                .stream()
                .filter(StringUtils::hasText)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .map(RoleCode::valueOf)   // <-- convierte a enum
                .distinct()
                .toList();

        List<Role> rs = roles.findByTenantIdAndCodeIn(tenantId, codes);

        if (rs.isEmpty()) {
            rs = roles.findByTenantIdAndCodeIn(tenantId, List.of(RoleCode.VIEWER));
        }

        // systems que vienen en la invitación
        List<String> invSystems = Optional.ofNullable(inv.getSystems())
                .orElseGet(List::of)
                .stream()
                .filter(StringUtils::hasText)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        // si alguno de los roles es orgWide => systems no aplican
        boolean orgWide = rs.stream().anyMatch(Role::isOrgWide);
        List<String> finalSystems = orgWide ? List.of() : invSystems;

        for (Role r : rs) {
            boolean exists = userRoles.existsByTenantIdAndUserIdAndRoleId(tenantId, user.getId(), r.getId());
            if (!exists) {
                var link = new UserRole();
                link.setTenantId(tenantId);
                link.setUserId(user.getId());
                link.setRoleId(r.getId());
                if (r.isSystemScoped()) {
                    link.setAllowedSystems(new HashSet<>(finalSystems)); // ya normalizado y vacío si orgWide
                }
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

        AuthorizationContext ctx = authz.build(tenantId, user.getId());
        String access = tokens.generateAccess(user, tenantId, ctx);
        String refresh = tokens.generateRefresh(user, tenantId, ctx); // si lo tienes

        return Map.of(
                "accessToken", access,
                "refreshToken", refresh,
                "tokenType", "Bearer",
                "user", Map.of(
                        "id", user.getId().toHexString(),
                        "email", user.getEmail(),
                        "name", user.getName()
                ),
                "authz", Map.of(
                        "roles", ctx.getRoles(),
                        "permissions", ctx.getPermissions(),
                        "orgWide", ctx.isOrgWide(),
                        "systems", ctx.getAllowedSystems(),
                        "ver", 1
                ),
                "tenantId", tenantId.toHexString()
        );

    }

    private String randomToken() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

}
