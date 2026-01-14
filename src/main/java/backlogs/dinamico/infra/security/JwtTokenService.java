package backlogs.dinamico.infra.security;

import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final String CLAIM_KIND = "kind";
    private static final String KIND_ACCESS = "access";
    private static final String KIND_REFRESH = "refresh";
    private static final String KIND_INVITE = "invite";

    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_ORG_ID = "orgId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLES = "roles";

    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.issuer:backlogs}")
    private String issuer;

    @Value("${security.jwt.ttl-minutes:120}")
    private long ttlMinutes;

    @Value("${security.jwt.refresh-ttl-minutes:4320}") // 3 dias
    private long refreshTtlMinutes;

    private Key key;

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("security.jwt.secret is required");
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);

        log.info("[JWT-SIGN] secret.len={} hash={}", secretBytes.length, secret.hashCode());

        this.key = new SecretKeySpec(secretBytes, "HmacSHA256");

        if (secretBytes.length < 32) {
            log.warn("[JWT] security.jwt.secret es corto ({} bytes). Recomendado >= 32 bytes para HS256.", secretBytes.length);
        }
        log.info("[JWT] HS256 key inicializada ({} bytes)", secretBytes.length);
    }

    // ========= ACCESS =========
    public String generate(User user, List<Role> roles, ObjectId tenantId) {
        Instant now = Instant.now();

        List<String> roleCodes = (roles == null)
                ? List.of()
                : roles.stream().map(Role::getCode).toList();

        String uid = user.getId() == null ? null : user.getId().toHexString();
        String ten = tenantId == null ? null : tenantId.toHexString();

        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(user.getEmail())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .claim(CLAIM_KIND, KIND_ACCESS)
                .claim(CLAIM_UID, uid)
                .claim(CLAIM_TENANT_ID, ten)
                .claim(CLAIM_ORG_ID, ten)
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_NAME, user.getName())
                .claim(CLAIM_ROLES, roleCodes)
                .signWith(SignatureAlgorithm.HS256, key)
                .compact();
    }

    /** Verifica solo ACCESS tokens (evita que refresh se use como access). */
    public Claims verifyAccess(String token) throws JwtException {
        Claims c = verify(token);
        String kind = c.get(CLAIM_KIND, String.class);
        if (!KIND_ACCESS.equals(kind)) {
            throw new JwtException("invalid_access_token_kind");
        }
        return c;
    }

    // ========= VERIFY (genérico) =========
    public Claims verify(String token) throws JwtException {
        return Jwts.parser()
                .setSigningKey(key)
                .requireIssuer(issuer) // importante
                .parseClaimsJws(token)
                .getBody();
    }

    // ========= REFRESH =========
    public String generateRefresh(User user, List<Role> roles, ObjectId tenantId) {
        Instant now = Instant.now();

        List<String> roleCodes = (roles == null)
                ? List.of()
                : roles.stream().map(Role::getCode).toList();

        String uid = user.getId() == null ? null : user.getId().toHexString();
        String ten = tenantId == null ? null : tenantId.toHexString();

        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(user.getEmail())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(refreshTtlMinutes, ChronoUnit.MINUTES)))
                .claim(CLAIM_KIND, KIND_REFRESH)
                .claim(CLAIM_UID, uid)
                .claim(CLAIM_TENANT_ID, ten)
                .claim(CLAIM_ORG_ID, ten)
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_NAME, user.getName())
                .claim(CLAIM_ROLES, roleCodes)
                .signWith(SignatureAlgorithm.HS256, key)
                .compact();
    }

    public Claims verifyRefresh(String token) throws JwtException {
        Claims c = verify(token);
        String kind = c.get(CLAIM_KIND, String.class);
        if (!KIND_REFRESH.equals(kind)) {
            throw new JwtException("invalid_refresh_token");
        }
        return c;
    }

    // ========= INVITE =========
    public String generateInviteJwt(ObjectId tenantId,
                                    String email,
                                    List<String> roles,
                                    String rawToken,
                                    Instant expiresAt) {

        Instant now = Instant.now();
        Instant exp = (expiresAt != null && expiresAt.isAfter(now))
                ? expiresAt
                : now.plus(48, ChronoUnit.HOURS);

        List<String> safeRoles = (roles == null) ? List.of() : roles;
        String ten = tenantId == null ? null : tenantId.toHexString();

        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(email)
                .setIssuedAt(Date.from(now))          // FIX
                .setExpiration(Date.from(exp))
                .claim(CLAIM_KIND, KIND_INVITE)       // FIX
                .addClaims(Map.of(
                        CLAIM_TENANT_ID, ten,         // FIX (antes "tenant")
                        CLAIM_ORG_ID, ten,
                        CLAIM_ROLES, safeRoles,
                        "tok", rawToken               // recomendación: mejor guardar hash en vez de raw
                ))
                .signWith(SignatureAlgorithm.HS256, key)
                .compact();
    }

    /** Genera un token de invitación */
    public String createInvite(ObjectId tenantId,
                               String email,
                               List<String> roles,
                               long ttlHours) {

        Instant now = Instant.now();
        String ten = tenantId == null ? null : tenantId.toHexString();

        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(email)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(ttlHours, ChronoUnit.HOURS)))
                .claim(CLAIM_KIND, KIND_INVITE)
                .claim(CLAIM_TENANT_ID, ten)
                .claim(CLAIM_ORG_ID, ten)
                .claim(CLAIM_ROLES, roles == null ? List.of() : roles)
                .signWith(SignatureAlgorithm.HS256, key)
                .compact();
    }

    public Claims verifyInvite(String inviteToken) {
        Claims c = verify(inviteToken);
        String kind = c.get(CLAIM_KIND, String.class);
        if (!KIND_INVITE.equals(kind)) {
            throw new JwtException("invalid_invite_token");
        }
        return c;
    }
}
