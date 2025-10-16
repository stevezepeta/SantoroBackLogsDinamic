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
  protected void doFilterInternal(@NonNull HttpServletRequest req,
                                  @NonNull HttpServletResponse res,
                                  @NonNull FilterChain chain)
          throws ServletException, IOException {

    if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
      chain.doFilter(req, res);
      return;
    }

    // 1) Leer headers/params
    String tenantHex = first(req.getHeader("X-Tenant-Id"),      req.getParameter("tenantId"));
    String domain    = first(req.getHeader("X-Org-Domain"),     req.getParameter("domain"));
    String systemHex = first(req.getHeader("X-System-Id"),      req.getParameter("systemId"));
    String envHex    = first(req.getHeader("X-Environment-Id"), req.getParameter("environmentId"));

    // 2) Resolver IDs
    ObjectId tenantId = null;
    if (isHexObjectId(tenantHex)) {
      tenantId = new ObjectId(tenantHex);
    } else if (notBlank(domain)) {
      tenantId = organizationRepository.findByDomainIgnoreCase(domain.trim())
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
      case "database-per-tenant" -> {
        dbName = (tenantId != null) ? baseDb + "__" + tenantId.toHexString() : baseDb;
      }
      case "collection-per-tenant" -> {
        dbName = baseDb;
        collectionSuffix = (tenantId != null) ? "__" + tenantId.toHexString() : null;
      }
      default -> dbName = baseDb; // single/shared
    }

    try {
      // 4) Fijar el contexto por request
      TenantContext.set(TenantContext.Ctx.builder()
              .tenantId(tenantId)
              .systemId(systemId)
              .environmentId(envId)
              .dbName(dbName)
              .collectionSuffix(collectionSuffix)
              .build());

      chain.doFilter(req, res);
    } finally {
      // 5) Limpiar SIEMPRE
      TenantContext.clear();
    }
  }

  // -------- helpers --------
  private static String first(String a, String b) {
    return (a != null && !a.isBlank()) ? a : (b != null && !b.isBlank() ? b : null);
  }

  private static boolean isHexObjectId(String v) {
    return v != null && v.matches("^[a-fA-F0-9]{24}$");
  }

  private static boolean notBlank(String v) {
    return v != null && !v.isBlank();
  }
}
