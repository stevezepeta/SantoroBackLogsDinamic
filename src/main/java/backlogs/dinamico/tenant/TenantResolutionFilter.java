package backlogs.dinamico.tenant;

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
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    @Value("${multitenant.header.tenant:X-Tenant}")
    private String tenantHeaderName;

    // Fallback opcional (útil si tu front manda org explícito)
    @Value("${multitenant.header.organization:X-Organization-Id}")
    private String organizationHeaderName;

    // Variantes comunes (por si Postman/clients mandan distinto)
    private static final String[] TENANT_HEADERS = {"X-Tenant", "X-Tenant-Id"};
    private static final String[] ORG_HEADERS = {"X-Organization-Id", "X-Org-Id", "X-Org"};

    private static final String[] PUBLIC_PATHS = {
            "/error",
            "/actuator",
            "/api/auth",
            "/ws"
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // OPTIONS siempre bypass
        if (HttpMethod.OPTIONS.matches(method)) return true;

        // Rutas públicas por prefijo
        for (String p : PUBLIC_PATHS) {
            if (uri.startsWith(p)) return true;
        }

        // Crear organizations público
        if ("/api/catalogs/organizations".equals(uri) && HttpMethod.POST.matches(method)) {
            return true;
        }

        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        try {
            // 1) Resolver tenantId desde headers (NO JWT aquí)
            if (TenantContext.getTenantId() == null) {
                String tenantHex = firstNonBlank(
                        req.getHeader(tenantHeaderName),
                        headerAny(req, TENANT_HEADERS)
                );

                if (StringUtils.hasText(tenantHex) && ObjectId.isValid(tenantHex)) {
                    TenantContext.setTenantIdHex(tenantHex);
                    log.debug("[TENANT] resolved from header: {}", tenantHex);
                } else if (StringUtils.hasText(tenantHex)) {
                    log.warn("[TENANT] header present but invalid ObjectId: {}", tenantHex);
                }
            }

            // 2) Resolver organizationId desde headers
            if (TenantContext.getOrganizationId() == null) {
                String orgHex = firstNonBlank(
                        req.getHeader(organizationHeaderName),
                        headerAny(req, ORG_HEADERS)
                );

                if (StringUtils.hasText(orgHex) && ObjectId.isValid(orgHex)) {
                    TenantContext.setOrganizationIdHex(orgHex);
                    log.debug("[ORG] resolved from header: {}", orgHex);
                } else if (StringUtils.hasText(orgHex)) {
                    log.warn("[ORG] header present but invalid ObjectId: {}", orgHex);
                }
            }

            // 3) Compat: si no hay organizationId pero sí tenantId, usa el mismo
            if (TenantContext.getOrganizationId() == null && TenantContext.getTenantId() != null) {
                TenantContext.setOrganizationId(TenantContext.getTenantId());
            }

            chain.doFilter(req, res);

        } finally {
            // Limpieza (ThreadLocal) al terminar el request completo
            TenantContext.clear();
        }
    }

    private static String headerAny(HttpServletRequest req, String[] names) {
        for (String n : names) {
            String v = req.getHeader(n);
            if (StringUtils.hasText(v)) return v;
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) return a.trim();
        if (StringUtils.hasText(b)) return b.trim();
        return null;
    }
}
