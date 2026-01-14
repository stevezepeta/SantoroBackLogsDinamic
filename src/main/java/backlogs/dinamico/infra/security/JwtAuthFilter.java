package backlogs.dinamico.infra.security;

import backlogs.dinamico.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenService tokens;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        boolean hasBearer = header != null && header.startsWith("Bearer ");

        // Si no hay token o ya hay auth, seguimos normal
        if (!hasBearer || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String token = header.substring(7);

            // ✅ recomendado: si quieres, puedes usar verifyAccess(token) aquí
            Claims c = tokens.verify(token);

            String email  = c.getSubject();
            String name   = c.get("name", String.class);

            String uidHex = c.get("uid", String.class);

            // tenantId puede venir con nombres distintos
            String tenantHex = firstNonBlank(
                    c.get("tenantId", String.class),
                    c.get("orgId", String.class),
                    c.get("organizationId", String.class),
                    c.get("organization_id", String.class),
                    c.get("org", String.class)
            );

            // orgId explícito (si lo manejas separado)
            String orgHex = firstNonBlank(
                    c.get("organizationId", String.class),
                    c.get("orgId", String.class),
                    c.get("organization_id", String.class),
                    c.get("org", String.class)
            );

            @SuppressWarnings("unchecked")
            List<Object> rolesRaw = c.get("roles", List.class);

            // NORMALIZACIÓN DE ROLES:
            // - acepta "ADMIN" o "ROLE_ADMIN"
            // - quita espacios
            // - uppercase
            // - distinct
            List<String> roleCodes = (rolesRaw == null)
                    ? List.of()
                    : rolesRaw.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(s -> s.startsWith("ROLE_") ? s.substring("ROLE_".length()) : s)
                    .map(String::toUpperCase)
                    .distinct()
                    .toList();

            var authorities = roleCodes.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();

            ObjectId tenantId = toObjectId(tenantHex);
            ObjectId orgId    = toObjectId(orgHex);
            ObjectId userId   = toObjectId(uidHex);

            // Si tenant no vino en el token, pero ya estaba en contexto (por header)
            var prev = TenantContext.get();
            if (tenantId == null) tenantId = prev.getTenantId();
            if (orgId == null) orgId = prev.getOrganizationId();

            // Regla: no autenticamos sin tenant
            if (tenantId == null) {
                throw new JwtException("tenant_not_resolved_in_jwt");
            }

            if (orgId == null) orgId = tenantId; // compat

            // Set TenantContext (merge + preserve)
            TenantContext.set(TenantContext.Ctx.builder()
                    .tenantId(tenantId)
                    .organizationId(orgId)
                    .userId(userId)
                    .email(email)
                    .name(name)

                    // preservar lo que pudo venir antes (API key, etc.)
                    .systemId(prev.getSystemId())
                    .environmentId(prev.getEnvironmentId())
                    .dbName(prev.getDbName())
                    .collectionSuffix(prev.getCollectionSuffix())
                    .build());

            AuthUser principal = new AuthUser(
                    userId,
                    email,
                    name,
                    tenantId,
                    roleCodes
            );

            var authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            chain.doFilter(request, response);

        } catch (JwtException | IllegalArgumentException e) {
            // Token inválido -> no autenticamos; Security decidirá 401/403
            SecurityContextHolder.clearContext();
            log.debug("[JwtAuthFilter] JWT invalid: {}", e.getMessage());
            chain.doFilter(request, response);
        }
    }

    private static ObjectId toObjectId(String hex) {
        if (!StringUtils.hasText(hex)) return null;
        return ObjectId.isValid(hex) ? new ObjectId(hex) : null;
    }

    private static String firstNonBlank(String... xs) {
        if (xs == null) return null;
        for (String x : xs) {
            if (StringUtils.hasText(x)) return x.trim();
        }
        return null;
    }
}
