package backlogs.dinamico.security;

import backlogs.dinamico.model.ingest.ApiKey;
import backlogs.dinamico.repository.catalog.ApiKeyRep;
import backlogs.dinamico.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyRep repo;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        // Si ya viene Bearer JWT, dejamos que lo maneje el filtro de JWT
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            chain.doFilter(req, res);
            return;
        }

        // Si no hay X-Api-Key, seguimos la cadena (otros filtros decidirán)
        String apiKey = req.getHeader("X-Api-Key");
        if (apiKey == null || apiKey.isBlank()) {
            chain.doFilter(req, res);
            return;
        }

        var hash = KeyHasher.sha256b64(apiKey);
        var opt = repo.findByKeyHashAndStatus(hash, "active");
        if (opt.isEmpty()) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid_api_key");
            return;
        }

        ApiKey key = opt.get();

        // Inyecta tenant en el contexto
        ObjectId tenantId = key.getTenantId();
        TenantContext.setTenantId(tenantId);

        // Authorities desde scopes
        var authorities = key.getScopes() == null
                ? java.util.List.<SimpleGrantedAuthority>of()
                : key.getScopes().stream()
                .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                .collect(Collectors.toList());

        // Authentication "pre-autenticado" basado en ApiKey
        var authToken = new AbstractAuthenticationToken(authorities) {
            @Override public Object getCredentials() { return "apiKey"; }
            @Override public Object getPrincipal() { return key.getName(); }
        };
        authToken.setAuthenticated(true);

        SecurityContextHolder.getContext().setAuthentication(authToken);

        try {
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }

    }
}
