package backlogs.dinamico.infra.security;

import backlogs.dinamico.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenService tokens;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);

                // Verifica el JWT
                Claims c = tokens.verify(token);

                // Extracción de claims estándar que metimos en el token
                String email   = c.getSubject();
                String name    = c.get("name", String.class);
                String uidHex  = c.get("uid", String.class);
                String tenHex  = c.get("tenantId", String.class);

                @SuppressWarnings("unchecked")
                List<String> roleCodes = c.get("roles", List.class);

                List<SimpleGrantedAuthority> authorities =
                        (roleCodes == null ? List.<String>of() : roleCodes)
                                .stream()
                                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                                .toList();

                if (StringUtils.hasText(tenHex) && ObjectId.isValid(tenHex)) {
                    TenantContext.set(new ObjectId(tenHex));
                }

                AuthUser principal = new AuthUser(
                        (uidHex != null && ObjectId.isValid(uidHex)) ? new ObjectId(uidHex) : null,
                        email,
                        name,
                        (tenHex != null && ObjectId.isValid(tenHex)) ? new ObjectId(tenHex) : null,
                        roleCodes == null ? List.of() : roleCodes
                );

                var authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            chain.doFilter(request, response);

        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
