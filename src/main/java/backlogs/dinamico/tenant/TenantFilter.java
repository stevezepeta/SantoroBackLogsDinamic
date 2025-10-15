package backlogs.dinamico.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            String tenantHex = req.getHeader("X-Tenant"); // o resolver subdominio
            if (tenantHex == null || !ObjectId.isValid(tenantHex)) {
                res.sendError(400, "X-Tenant inválido o ausente");
                return;
            }
            TenantContext.set(tenantHex);
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }

}
