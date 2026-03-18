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
    private final UserRepository       users;
    private final RoleRepository       roles;
    private final UserRoleRepository   userRoles;
    private final PasswordEncoder      encoder;
    private final JwtTokenService      tokens;
    private final AuthorizationContextService authz;

    @Value("${app.frontend.base-url:http://187.188.66.56:8032}")
    private String frontendBaseUrl;

    // ── Crear invitación PENDING ───────────────────────────────────────────────

    public UserInvite create(ObjectId tenantId,
                             String email,
                             List<String> roleCodes,
                             List<String> systems,
                             Duration ttl) {
        return create(tenantId, email, roleCodes, systems, ttl, null);
    }

    /**
     * Sobrecarga que acepta logFilters — usar esta cuando el superAdmin
     * quiere restringir la visibilidad de logs del VIEWER invitado.
     */
    public UserInvite create(ObjectId tenantId,
                             String email,
                             List<String> roleCodes,
                             List<String> systems,
                             Duration ttl,
                             UserRole.LogFilter logFilters) {

        if (tenantId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant_required");
        if (!StringUtils.hasText(email))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email_required");

        String emailNorm = email.trim();
        String emailCi   = emailNorm.toLowerCase(Locale.ROOT);

        users.findByTenantIdAndEmailIgnoreCase(tenantId, emailNorm)
                .ifPresent(u -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "user_exists"); });

        invites.findFirstByTenantIdAndEmailCiAndStatus(tenantId, emailCi, "PENDING")
                .ifPresent(i -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "invite_already_sent"); });

        RoleCode role     = parseSingleRole(roleCodes);
        List<String> safeRoles = List.of(role.name());

        // ── FIX 1: systems se respetan para TODOS los roles ───────────────────
        List<String> safeSystems = normalizeSystems(systems);

        // SYSTEM_MANAGER REQUIERE al menos 1 system
        if (role == RoleCode.SYSTEM_MANAGER && safeSystems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "systems_required_for_system_manager");
        }
        // VIEWER y otros roles: se guardan los systems que vengan (puede ser vacío = todos)
        // NO se borran

        // ── TTL ───────────────────────────────────────────────────────────────
        Duration effectiveTtl = (ttl == null) ? Duration.ofHours(48) : ttl;
        if (effectiveTtl.isZero() || effectiveTtl.isNegative())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ttl_invalid");
        if (effectiveTtl.compareTo(Duration.ofDays(30)) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ttl_too_large");

        String token = randomToken();
        Instant now  = Instant.now();

        // ── FIX 2: logFilters se guarda en el UserInvite ──────────────────────
        var inv = UserInvite.builder()
                .tenantId(tenantId)
                .email(emailNorm)
                .emailCi(emailCi)
                .roles(safeRoles)
                .systems(safeSystems)
                .logFilters(logFilters)     // ← nuevo campo
                .token(token)
                .expiresAt(now.plus(effectiveTtl))
                .status("PENDING")
                .createdAt(now)
                .updateAt(now)
                .build();

        UserInvite saved = invites.save(inv);

        String inviteLink = buildInviteLink(saved);
        log.info("[INVITE] email={} tenant={} roles={} systems={} logFilters={} token={} link={}",
                emailCi, tenantId.toHexString(), safeRoles, safeSystems,
                logFilters != null ? "present" : "none", token, inviteLink);

        return saved;
    }

    // ── Aceptar invitación ────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> accept(String token, String name, String password) {

        if (!StringUtils.hasText(token))    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token_required");
        if (!StringUtils.hasText(name))     throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name_required");
        if (!StringUtils.hasText(password)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password_required");

        var inv = invites.findByTokenAndStatus(token, "PENDING")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "invite_not_found"));

        if (Instant.now().isAfter(inv.getExpiresAt())) {
            inv.setStatus("EXPIRED");
            inv.setUpdateAt(Instant.now());
            invites.save(inv);
            throw new ResponseStatusException(HttpStatus.GONE, "invite_expired");
        }

        ObjectId tenantId = inv.getTenantId();

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

        List<RoleCode> codes = Optional.ofNullable(inv.getRoles()).orElseGet(List::of)
                .stream()
                .filter(StringUtils::hasText)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .map(RoleCode::valueOf)
                .distinct()
                .toList();

        List<Role> rs = roles.findByTenantIdAndCodeIn(tenantId, codes);
        if (rs.isEmpty()) {
            rs = roles.findByTenantIdAndCodeIn(tenantId, List.of(RoleCode.VIEWER));
        }

        List<String> invSystems = Optional.ofNullable(inv.getSystems()).orElseGet(List::of)
                .stream()
                .filter(StringUtils::hasText)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        boolean orgWide = rs.stream().anyMatch(Role::isOrgWide);

        // ── FIX: si el invite tiene systems específicos, tienen prioridad ─────
        // El superAdmin eligió restringir a TRUSTVALUE → ignorar orgWide del rol
        boolean hasInviteSystems = !invSystems.isEmpty();
        List<String> finalSystems = hasInviteSystems
                ? invSystems           // sistemas específicos del invite → prioridad
                : (orgWide ? List.of() : invSystems);  // comportamiento original

        // ── FIX 2 (cont.): logFilters se copia del invite al UserRole ─────────
        UserRole.LogFilter logFilters = inv.getLogFilters();

        for (Role r : rs) {
            boolean exists = userRoles.existsByTenantIdAndUserIdAndRoleId(
                    tenantId, user.getId(), r.getId());
            if (!exists) {
                var link = new UserRole();
                link.setTenantId(tenantId);
                link.setUserId(user.getId());
                link.setRoleId(r.getId());
                link.setCreatedAt(Instant.now());
                link.setUpdatedAt(Instant.now());

                // Systems: respetar lo que viene de la invitación
                if (r.isSystemScoped() || hasInviteSystems) {
                    link.setAllowedSystems(new HashSet<>(finalSystems));
                }

                // LogFilters: solo aplicar si hay filtros y no es orgWide sin restricción
                if (!finalSystems.isEmpty() || !orgWide) {
                    if (logFilters != null && !logFilters.isEmpty()) {
                        link.setLogFilters(logFilters);
                    }
                }

                userRoles.save(link);
            }
        }

        inv.setStatus("ACCEPTED");
        inv.setAcceptedBy(user.getId());
        inv.setUpdateAt(Instant.now());
        invites.save(inv);

        AuthorizationContext ctx = authz.build(tenantId, user.getId());
        String access  = tokens.generateAccess(user, tenantId, ctx);
        String refresh = tokens.generateRefresh(user, tenantId, ctx);

        return Map.of(
                "accessToken",  access,
                "refreshToken", refresh,
                "tokenType",    "Bearer",
                "user", Map.of(
                        "id",    user.getId().toHexString(),
                        "email", user.getEmail(),
                        "name",  user.getName()
                ),
                "authz", Map.of(
                        "roles",       ctx.getRoles(),
                        "permissions", ctx.getPermissions(),
                        "orgWide",     ctx.isOrgWide(),
                        "systems",     ctx.getAllowedSystems(),
                        "ver",         1
                ),
                "tenantId", tenantId.toHexString()
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public String buildInviteLink(UserInvite invite) {
        if (invite == null || !StringUtils.hasText(invite.getToken())) return null;
        return frontendBaseUrl + "/accept-invite?token=" + invite.getToken();
    }

    private RoleCode parseSingleRole(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role_required");

        List<String> cleaned = roleCodes.stream()
                .filter(StringUtils::hasText)
                .map(r -> r.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        if (cleaned.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role_required");
        if (cleaned.size() != 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "only_one_role_allowed");

        try {
            return RoleCode.valueOf(cleaned.get(0));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role_invalid");
        }
    }

    private List<String> normalizeSystems(List<String> systems) {
        if (systems == null) return List.of();
        return systems.stream()
                .filter(StringUtils::hasText)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .map(s -> {
                    if (!s.matches("^[A-Z0-9][A-Z0-9_\\-]{0,99}$"))
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "system_code_invalid");
                    return s;
                })
                .toList();
    }

    private String randomToken() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}