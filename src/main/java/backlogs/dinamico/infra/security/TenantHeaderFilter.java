package backlogs.dinamico.infra.security;

import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.tenant.TenantContext;
import backlogs.dinamico.tenant.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;             // <-- importante para las firmas override
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;                     // <-- import correcto

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class TenantHeaderFilter extends OncePerRequestFilter {

  private final OrganizationRepository organizationRepository;

  @Value("${multitenant.strategy:single}")        // single | collection-per-tenant | database-per-tenant
  private String strategy;

  @Value("${multitenant.base-database:logs_system}")
  private String baseDb;

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest req) {
    String path = req.getRequestURI();
    return (path == null || !path.startsWith("/api/"));
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

    TenantContext ctx = new TenantContext();

    // Lee headers/params
    String tenantHex = first(req.getHeader("X-Tenant-Id"),      req.getParameter("tenantId"));
    String domain    = first(req.getHeader("X-Org-Domain"),     req.getParameter("domain"));
    String systemHex = first(req.getHeader("X-System-Id"),      req.getParameter("systemId"));
    String envHex    = first(req.getHeader("X-Environment-Id"), req.getParameter("environmentId"));

    // Resolver tenant: primero por ObjectId válido, si no por dominio
    if (isHexObjectId(tenantHex)) {
      ctx.setTenantId(new ObjectId(tenantHex));
    } else if (notBlank(domain)) {
      organizationRepository.findByDomainIgnoreCase(domain.trim())
          .ifPresent(org -> ctx.setTenantId(org.getId()));
    }

    if (isHexObjectId(systemHex)) ctx.setSystemId(new ObjectId(systemHex));
    if (isHexObjectId(envHex))    ctx.setEnvironmentId(new ObjectId(envHex));

    // Routing según estrategia
    String st = (strategy == null ? "single" : strategy.toLowerCase());
    switch (st) {
      case "database-per-tenant" -> {
        ctx.setDbName(ctx.getTenantId() != null
            ? baseDb + "__" + ctx.getTenantId().toHexString()
            : baseDb);
      }
      case "collection-per-tenant" -> {
        ctx.setDbName(baseDb);
        if (ctx.getTenantId() != null) {
          ctx.setCollectionSuffix("__" + ctx.getTenantId().toHexString());
        }
      }
      default -> ctx.setDbName(baseDb); // single
    }

    TenantContextHolder.set(ctx);
    try {
      chain.doFilter(req, res);
    } finally {
      TenantContextHolder.clear();
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
