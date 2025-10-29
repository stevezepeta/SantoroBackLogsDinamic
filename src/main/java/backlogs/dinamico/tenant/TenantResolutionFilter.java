package backlogs.dinamico.tenant;

import backlogs.dinamico.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import io.jsonwebtoken.Jwts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    @Value("${multitenant.header.tenant:X-Tenant}")
    private String tenantHeaderName;

    @Value("${security.jwt.secret:}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String bearer = req.getHeader("Authorization");
            String tokenPreview = (bearer != null && bearer.startsWith("Bearer "))
                    ? bearer.substring(7, Math.min(bearer.length(), 7 + 20)) + "…"
                    : "none";

            log.info("[TENANT-RESOLVER] path={}, authBearer={}", req.getRequestURI(), tokenPreview);
            log.info("[JWT-PARSE] secret.len={} hash={}",
                    jwtSecret == null ? 0 : jwtSecret.getBytes(StandardCharsets.UTF_8).length,
                    jwtSecret == null ? 0 : jwtSecret.hashCode());

            // 1) Intentar Authorization: Bearer <token>
            if (bearer != null && bearer.startsWith("Bearer ") && jwtSecret != null && !jwtSecret.isBlank()) {
                String token = bearer.substring(7);
                try {
                    var claims = Jwts.parser()
                            .setSigningKey(jwtSecret.getBytes(StandardCharsets.UTF_8))
                            .parseClaimsJws(token)
                            .getBody();

                    String claimKeys = claims.keySet().stream().collect(Collectors.joining(","));
                    log.info("[TENANT-RESOLVER] JWT claims keys={}", claimKeys);

                    Object t = claims.get("tenantId");
                    if (t != null && ObjectId.isValid(t.toString())) {
                        TenantContext.setTenantIdHex(t.toString());
                        log.info("[TENANT] Resuelto desde JWT: {}", t);
                    } else {
                        log.warn("[TENANT] JWT sin claim tenantId válido. tenantId={}", t);
                    }
                } catch (Exception ex) {
                    log.error("[TENANT] Error parseando JWT: {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
                }
            }

            // 2) Fallback: header X-Tenant
            if (TenantContext.getTenantIdHex() == null) {
                String tenantHex = req.getHeader(tenantHeaderName);
                if (tenantHex != null && ObjectId.isValid(tenantHex)) {
                    TenantContext.setTenantIdHex(tenantHex);
                    log.info("[TENANT] Resuelto desde header {}: {}", tenantHeaderName, tenantHex);
                } else if (tenantHex != null) {
                    log.warn("[TENANT] Header {} presente pero inválido: {}", tenantHeaderName, tenantHex);
                }
            }

            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }
}
