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

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.issuer:backlogs}")
    private String issuer;

    @Value("${security.jwt.ttl-minutes:120}")
    private long ttlMinutes;

    private Key key;

    @PostConstruct
    void init() {

        log.info("[JWT-SIGN] secret.len={} hash={}",
                secret.getBytes(StandardCharsets.UTF_8).length, secret.hashCode());

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("security.jwt.secret is required");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        int len = secret.getBytes(StandardCharsets.UTF_8).length;
        if (len < 32) {
            log.warn("[JWT] security.jwt.secret es corto ({} bytes). Se recomienda >= 32 bytes para HS256.", len);
        }
        log.info("[JWT] HS256 key inicializada ({} bytes)", len);
    }

    /** Genera un JWT para login del usuario */
    public String generate(User user, List<Role> roles, ObjectId tenantId) {
        Instant now = Instant.now();

        List<String> roleCodes = (roles == null)
                ? List.of()
                : roles.stream().map(Role::getCode).toList();

        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(user.getEmail())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .claim("uid", user.getId() == null ? null : user.getId().toHexString())
                .claim("tenantId", tenantId == null ? null : tenantId.toHexString())
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("roles", roleCodes)
                // jjwt 0.9.x => algoritmo PRIMERO, luego la llave
                .signWith(SignatureAlgorithm.HS256, key)
                .compact();
    }

    /** Verifica un JWT y retorna sus claims */
    public Claims verify(String token) throws JwtException {
        return Jwts.parser()
                .setSigningKey(key)
                .parseClaimsJws(token)
                .getBody();
    }

    /** Genera un token de invitación */
    public String createInvite(ObjectId tenantId,
                               String email,
                               List<String> roles,
                               long ttlHours) {

        Instant now = Instant.now();

        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(email)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(ttlHours, ChronoUnit.HOURS)))
                .claim("kind", "invite")
                .claim("tenantId", tenantId == null ? null : tenantId.toHexString())
                .claim("roles", roles == null ? List.of() : roles)
                .signWith(SignatureAlgorithm.HS256, key)
                .compact();
    }

    /** Verifica y valida que el token sea de tipo "invite" */
    public Claims verifyInvite(String inviteToken) {
        Claims c = verify(inviteToken);
        String kind = c.get("kind", String.class);
        if (!"invite".equals(kind)) {
            throw new JwtException("invalid_invite_token");
        }
        return c;
    }
}
