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

    @Value("${multitenant.header.organization:X-Organization-Id}")
    private String organizationHeaderName;

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

        if (HttpMethod.OPTIONS.matches(method)) return true;

        for (String p : PUBLIC_PATHS) {
            if (uri.startsWith(p)) return true;
        }

        if ("/api/catalogs/organizations".equals(uri) && HttpMethod.POST.matches(method)) {
            return true;
        }

        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        try {
            // ---------- TENANT ----------
            if (TenantContext.getTenantId() == null) {
                String tenantHex = firstNonBlank(
                        req.getHeader(tenantHeaderName),
                        headerAny(req, TENANT_HEADERS)
                );

                if (StringUtils.hasText(tenantHex)) {
                    if (ObjectId.isValid(tenantHex)) {
                        TenantContext.setTenantId(new ObjectId(tenantHex));
                        log.debug("[TENANT] resolved from header: {}", tenantHex);
                    } else {
                        log.warn("[TENANT] header present but invalid ObjectId: {}", tenantHex);
                    }
                }
            }

            // ---------- ORGANIZATION ----------
            if (TenantContext.getOrganizationId() == null) {
                String orgHex = firstNonBlank(
                        req.getHeader(organizationHeaderName),
                        headerAny(req, ORG_HEADERS)
                );

                if (StringUtils.hasText(orgHex)) {
                    if (ObjectId.isValid(orgHex)) {
                        TenantContext.setOrganizationId(new ObjectId(orgHex));
                        log.debug("[ORG] resolved from header: {}", orgHex);
                    } else {
                        log.warn("[ORG] header present but invalid ObjectId: {}", orgHex);
                    }
                }
            }

            // Compatibilidad: si no hay org, usa tenant
            if (TenantContext.getOrganizationId() == null && TenantContext.getTenantId() != null) {
                TenantContext.setOrganizationId(TenantContext.getTenantId());
            }

            chain.doFilter(req, res);

        } finally {
            TenantContext.clear();
        }
    }

    private static String headerAny(HttpServletRequest req, String[] names) {
        for (String n : names) {
            String v = req.getHeader(n);
            if (StringUtils.hasText(v)) return v.trim();
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) return a.trim();
        if (StringUtils.hasText(b)) return b.trim();
        return null;
    }
}
