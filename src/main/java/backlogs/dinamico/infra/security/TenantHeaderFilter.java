package backlogs.dinamico.infra.security;

import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class TenantHeaderFilter extends OncePerRequestFilter {

  private final OrganizationRepository organizationRepository;

  @Value("${multitenant.strategy:single}")           // single | collection-per-tenant | database-per-tenant
  private String strategy;

  @Value("${multitenant.base-database:logs_system}")
  private String baseDb;

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest req) {
    String path = req.getRequestURI();
    return path == null || !path.startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req,
                                  HttpServletResponse res,
                                  FilterChain chain)
          throws ServletException, IOException {

    if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
      chain.doFilter(req, res);
      return;
    }

    // 1) Leer headers/params
    String tenantHex = coalesce(
            req.getHeader("X-Tenant"),
            req.getHeader("X-Tenant-Id"),
            req.getParameter("tenantId")
    );

    String orgCode = coalesce(
            req.getHeader("X-Org-Code"),
            req.getHeader("X-Org-Slug"),
            req.getHeader("X-Org-Domain"),
            req.getParameter("org"),
            req.getParameter("orgCode"),
            req.getParameter("domain")
    );

    String systemHex = coalesce(req.getHeader("X-System-Id"),      req.getParameter("systemId"));
    String envHex    = coalesce(req.getHeader("X-Environment-Id"), req.getParameter("environmentId"));

    ObjectId tenantId = null;
    if (isHexObjectId(tenantHex)) {
      tenantId = new ObjectId(tenantHex);
    } else if (hasText(orgCode)) {
      String code = orgCode.trim().toLowerCase();
      tenantId = organizationRepository.findByCodeIgnoreCase(code)
              .or(() -> organizationRepository.findBySlug(code))
              .or(() -> organizationRepository.findByDomainIgnoreCase(code))
              .map(org -> org.getId())
              .orElse(null);
    }

    ObjectId systemId = isHexObjectId(systemHex) ? new ObjectId(systemHex) : null;
    ObjectId envId    = isHexObjectId(envHex)    ? new ObjectId(envHex)    : null;

    // 3) Resolver estrategia de particionado
    String st = strategy == null ? "single" : strategy.toLowerCase();

    String dbName = baseDb;
    String collectionSuffix = null;

    switch (st) {
      case "database-per-tenant" -> dbName = (tenantId != null) ? baseDb + "__" + tenantId.toHexString() : baseDb;
      case "collection-per-tenant" -> {
        dbName = baseDb;
        collectionSuffix = (tenantId != null) ? "__" + tenantId.toHexString() : null;
      }
      default -> dbName = baseDb;
    }

    try {
      TenantContext.set(TenantContext.Ctx.builder()
              .tenantId(tenantId)
              .systemId(systemId)
              .environmentId(envId)
              .dbName(dbName)
              .collectionSuffix(collectionSuffix)
              .build());

      chain.doFilter(req, res);
    } finally {
      TenantContext.clear();
    }
  }

  // -------- helpers --------
  private static String coalesce(String... values) {
    if (values == null) return null;
    for (String v : values) if (v != null && !v.isBlank()) return v;
    return null;
  }
  private static boolean isHexObjectId(String v) {
    return v != null && v.matches("^[a-fA-F0-9]{24}$");
  }

  private static boolean hasText(String v) {
    return v != null && !v.isBlank();
  }
}
