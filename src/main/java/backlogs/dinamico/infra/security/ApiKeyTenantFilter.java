package backlogs.dinamico.infra.security;

import backlogs.dinamico.model.ingest.ApiKey;
import backlogs.dinamico.repository.catalog.ApiKeyRep;
import backlogs.dinamico.tenant.TenantContext;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;

import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static backlogs.dinamico.security.KeyHasher.sha256b64;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(prefix = "multitenant", name = "require-api-key", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class ApiKeyTenantFilter extends OncePerRequestFilter {

  private final ApiKeyRep apiKeyRepo;

  @Value("${multitenant.strategy:collection-per-tenant}") // single | collection-per-tenant | database-per-tenant
  private String strategy;

  @Value("${multitenant.base-database:logs_system}")
  private String baseDb;

  @Value("${multitenant.header.tenant:X-Tenant}")
  private String tenantHeaderName;

  // Rutas que NO deben pasar por este filtro
  private static final List<RequestMatcher> EXCLUDED = List.of(
          new AntPathRequestMatcher("/error"),
          new AntPathRequestMatcher("/actuator/**"),
          new AntPathRequestMatcher("/api/auth/**")
  );

  @PostConstruct
  void onInit() {
    log.warn("[ApiKeyTenantFilter] Registrado y ACTIVO (multitenant.require-api-key=true).");
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;
    for (RequestMatcher m : EXCLUDED) if (m.matches(request)) return true;
    return false; // filtra el resto
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
          throws ServletException, IOException {

    log.info("[ApiKeyTenantFilter] path={}, tenantCtx={}, auth={}, xTenant={}",
            req.getRequestURI(),
            TenantContext.getTenantIdHex(),
            req.getHeader("Authorization") != null ? "Bearer..." : "null",
            req.getHeader(tenantHeaderName));

    final String path = req.getRequestURI();

    // 1) Si ya hay tenant resuelto (por JWT u otro filtro), sigue
    if (TenantContext.getTenantId() != null) {
      chain.doFilter(req, res);
      return;
    }

    // 2) Ingesta por API-KEY
    if (path != null && (
            path.startsWith("/api/ingest/") ||
            path.startsWith("/api/fingerprint")

    )) {

      // Aceptar ambas variantes del header
      String apiKeyPlain = firstNonBlank(req.getHeader("X-Api-Key"), req.getHeader("X-API-Key"));
      if (apiKeyPlain == null) {
        unauthorized(res, "Missing X-Api-Key");
        return;
      }

      // buscar por HASH + status
      String hash = sha256b64(apiKeyPlain);
      var opt = apiKeyRepo.findByKeyHashAndStatus(hash, "active");
      if (opt.isEmpty()) { unauthorized(res, "Invalid API key"); return; }

      ApiKey key = opt.get();

      // expiración / rotación
      var now = Instant.now();
      if (key.getExpiresAt() != null && now.isAfter(key.getExpiresAt())) {
        unauthorized(res, "API key expired");
        return;
      }
      if (key.getRotatesAt() != null && now.isAfter(key.getRotatesAt())) {
        unauthorized(res, "API key requires rotation");
        return;
      }

      try {
        // fijar contexto desde la API key
        TenantContext.set(TenantContext.Ctx.builder()
                .tenantId(key.getTenantId())
                .systemId(key.getSystemId())                 // *_Id (ObjectId)
                .environmentId(key.getEnvironmentId())
                .build());

        // particionado por tenant
        switch (strategy.toLowerCase()) {
          case "database-per-tenant" ->
                  TenantContext.setDbName(baseDb + "__" + key.getTenantId().toHexString());
          case "collection-per-tenant" ->
                  TenantContext.setCollectionSuffix("__" + key.getTenantId().toHexString());
          default -> { /* single: sin cambios */ }
        }

        chain.doFilter(req, res);
      } finally {
        TenantContext.clear();
      }
      return;
    }

    // 3) Fallback: aceptar X-Tenant (hex) si lo envían directamente
    String tenantHex = req.getHeader(tenantHeaderName);
    if (tenantHex != null && ObjectId.isValid(tenantHex)) {
      try {
        TenantContext.setTenantIdHex(tenantHex);
        chain.doFilter(req, res);
      } finally {
        TenantContext.clear();
      }
      return;
    }

    log.warn("[ApiKeyTenantFilter] missing_tenant -> path={}, authHeader={}, xTenant={}, tenantCtxNow={}",
            req.getRequestURI(),
            req.getHeader("Authorization") != null ? "present" : "absent",
            tenantHex,
            TenantContext.getTenantIdHex());

    badRequest(res, "missing_tenant");

  }

  // ----------- Helper ----------------
  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    if (b != null && !b.isBlank()) return b;

    return null;
  }

  private static String sha256b64(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return Base64.getEncoder().encodeToString(md.digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) { throw new RuntimeException(e); }
  }

  private void unauthorized(HttpServletResponse res, String msg) throws IOException {
    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
    res.setContentType("application/json");
    res.getWriter().write("{\"ok\":false,\"error\":\"unauthorized\",\"message\":\"" + msg + "\"}");
    res.getWriter().flush();
  }

  private void badRequest(HttpServletResponse res, String msg) throws IOException {
    res.reset();
    res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
    res.setContentType("application/json");
    res.getWriter().write("{\"ok\":false,\"error\":\"" + msg + "\"}");
    res.getWriter().flush();
  }
}
