package backlogs.dinamico.infra.security;

import backlogs.dinamico.repository.catalog.ApiKeyRepository;
import backlogs.dinamico.tenant.TenantContext;
import backlogs.dinamico.tenant.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;


@Component
@ConditionalOnProperty(prefix = "multitenant", name = "require-api-key", havingValue = "true")
@RequiredArgsConstructor
public class ApiKeyTenantFilter extends OncePerRequestFilter {

  private final ApiKeyRepository apiKeyRepo;

  @Value("${multitenant.strategy}")        private String strategy;   
  @Value("${multitenant.base-database}")   private String baseDb;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;
    String path = request.getRequestURI();
    return path == null || !path.startsWith("/api/ingest/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {

    String apiKey = req.getHeader("X-API-Key");
    if (apiKey == null || apiKey.isBlank()) {
      unauthorized(res, "Missing X-API-Key");
      return;
    }

    var opt = apiKeyRepo.findByKeyAndStatus(apiKey, "active");
    if (opt.isEmpty()) {
      unauthorized(res, "Invalid API key");
      return;
    }

    var key = opt.get();
    if (key.getRotatesAt() != null && Instant.now().isAfter(key.getRotatesAt())) {
      unauthorized(res, "API key expired");
      return;
    }

    TenantContext ctx = new TenantContext();
    ctx.setTenantId(key.getTenantId());
    ctx.setSystemId(key.getSystemId());
    ctx.setEnvironmentId(key.getEnvironmentId());

    switch (strategy) {
      case "database-per-tenant" -> {
        ctx.setDbName(baseDb + "__" + key.getTenantId().toHexString());
      }
      case "collection-per-tenant" -> {
        ctx.setDbName(baseDb);
        ctx.setCollectionSuffix("__" + key.getTenantId().toHexString());
      }
      default -> {
        ctx.setDbName(baseDb);
      }
    }

    TenantContextHolder.set(ctx);
    try {
      chain.doFilter(req, res);
    } finally {
      TenantContextHolder.clear();
    }
  }

  private void unauthorized(HttpServletResponse res, String msg) throws IOException {
    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
    res.setContentType("application/json");
    res.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"" + msg + "\"}");
    res.getWriter().flush();
  }
}
