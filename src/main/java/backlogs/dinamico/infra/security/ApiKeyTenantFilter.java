package backlogs.dinamico.infra.security;

import backlogs.dinamico.model.ingest.ApiKey;
import backlogs.dinamico.repository.catalog.ApiKeyRep;
import backlogs.dinamico.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static backlogs.dinamico.security.KeyHasher.sha256b64;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(
        prefix = "multitenant",
        name = "require-api-key",
        havingValue = "true",
        matchIfMissing = false
)
@RequiredArgsConstructor
public class ApiKeyTenantFilter extends OncePerRequestFilter {

  private final ApiKeyRep apiKeyRepo;
  private final JwtTokenService jwtTokenService;

  @Value("${multitenant.strategy:collection-per-tenant}") // single | collection-per-tenant | database-per-tenant
  private String strategy;

  @Value("${multitenant.base-database:logs_system}")
  private String baseDb;

  @Value("${multitenant.header.tenant:X-Tenant}")
  private String tenantHeaderName;

  @Value("${backlogs.allow-public-create-organization:true}")
  private boolean allowPublicCreateOrg;

  // Rutas que NO deben pasar por este filtro
  private static final List<RequestMatcher> EXCLUDED = List.of(
          new AntPathRequestMatcher("/error"),
          new AntPathRequestMatcher("/actuator/**"),
          new AntPathRequestMatcher("/api/auth/**"),
          new AntPathRequestMatcher("/ws/**")
  );

  // Bypass explícito para crear organizations SIN API-KEY
  private static final RequestMatcher CREATE_ORG_POST =
          new AntPathRequestMatcher("/api/catalogs/organizations", "POST");

  // Ingest endpoints (API-KEY o JWT)
  private static final RequestMatcher INGEST_POST =
          new AntPathRequestMatcher("/api/ingest/**", "POST");

  private static final RequestMatcher FINGERPRINT_POST =
          new AntPathRequestMatcher("/api/fingerprint/**", "POST");

  // endpoint universal de ingesta
  private static final RequestMatcher LOG_EVENTS_POST =
          new AntPathRequestMatcher("/api/logs/events", "POST");

  private static final RequestMatcher LOGS_POST =
          new AntPathRequestMatcher("/api/logs", "POST");

  @PostConstruct
  void onInit() {
    log.warn("[ApiKeyTenantFilter] Registrado y ACTIVO (multitenant.require-api-key=true).");
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();

    if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;

    for (RequestMatcher m : EXCLUDED) {
      if (m.matches(request)) {
        log.debug("[ApiKeyTenantFilter] skipping path={} (EXCLUDED)", path);
        return true;
      }
    }

    if (allowPublicCreateOrg && CREATE_ORG_POST.matches(request)) {
      log.debug("[ApiKeyTenantFilter] skipping path={} (CREATE_ORG_POST)", path);
      return true;
    }

    return false;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
          throws ServletException, IOException {

    final String path = req.getRequestURI();
    final String bearerToken = resolveBearerToken(req);

    log.info("[ApiKeyTenantFilter] path={}, tenantCtx={}, bearer={}, xTenant={}",
            path,
            TenantContext.getTenantIdHex(),
            (bearerToken != null) ? "yes" : "no",
            req.getHeader(tenantHeaderName)
    );

    // 0) Si ya viene tenant resuelto (por otro filtro), solo aplica strategy y sigue
    if (TenantContext.getTenantId() != null) {
      applyStrategy(TenantContext.getTenantId());
      chain.doFilter(req, res);
      return;
    }

    // 1) Si viene Bearer: resolver tenant desde JWT (NO pedir X-Api-Key)
    if (bearerToken != null) {
      ObjectId tenantId = tryResolveTenantFromJwt(bearerToken);
      if (tenantId == null) {
        unauthorized(res, "tenant_not_resolved");
        return;
      }

      boolean weSet = false;
      try {
        TenantContext.set(TenantContext.Ctx.builder()
                .tenantId(tenantId)
                .build());
        weSet = true;

        applyStrategy(tenantId);
        chain.doFilter(req, res);
      } finally {
        if (weSet) TenantContext.clear();
      }
      return;
    }

    // 2) Si es endpoint de ingesta -> exigir API KEY (solo si NO hay Bearer)
    boolean isIngest =
            INGEST_POST.matches(req)
                    || FINGERPRINT_POST.matches(req)
                    || LOGS_POST.matches(req)
                    || LOG_EVENTS_POST.matches(req);

    if (isIngest) {
      String apiKeyPlain = firstNonBlank(req.getHeader("X-Api-Key"), req.getHeader("X-API-Key"));
      if (!StringUtils.hasText(apiKeyPlain)) {
        unauthorized(res, "Missing X-Api-Key");
        return;
      }

      String hash = sha256b64(apiKeyPlain);
      var opt = apiKeyRepo.findByKeyHashAndStatus(hash, "active");
      if (opt.isEmpty()) {
        unauthorized(res, "Invalid API key");
        return;
      }

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

      boolean weSet = false;
      try {
        TenantContext.set(TenantContext.Ctx.builder()
                .tenantId(key.getTenantId())
                .systemId(key.getSystemId())
                .environmentId(key.getEnvironmentId())
                .build());
        weSet = true;

        applyStrategy(key.getTenantId());
        chain.doFilter(req, res);
      } finally {
        if (weSet) TenantContext.clear();
      }
      return;
    }

    // 3) No es ingesta, no hay Bearer, no hay tenant header: deja pasar (Security decide)
    chain.doFilter(req, res);
  }

  // ---------- JWT tenant resolver (usa tu JwtTokenService actual) ----------
  private ObjectId tryResolveTenantFromJwt(String token) {
    try {
      Claims claims = jwtTokenService.verifyAccess(token);

      Object raw =
              claims.get("tenantId") != null ? claims.get("tenantId") :
                      claims.get("tenant_id") != null ? claims.get("tenant_id") :
                              claims.get("orgId") != null ? claims.get("orgId") :
                                      claims.get("organizationId");

      if (raw == null) return null;

      String hex = String.valueOf(raw);
      if (!ObjectId.isValid(hex)) return null;

      return new ObjectId(hex);

    } catch (JwtException e) {
      log.warn("[ApiKeyTenantFilter] JWT inválido: {}", e.getMessage());
      return null;
    } catch (Exception e) {
      log.warn("[ApiKeyTenantFilter] Error leyendo JWT: {}", e.getMessage());
      return null;
    }
  }

  private static String resolveBearerToken(HttpServletRequest req) {
    String auth = req.getHeader("Authorization");
    if (auth == null) return null;
    if (!auth.startsWith("Bearer ")) return null;
    String token = auth.substring("Bearer ".length()).trim();
    return token.isEmpty() ? null : token;
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    if (b != null && !b.isBlank()) return b;
    return null;
  }

  private void applyStrategy(ObjectId tenantId) {
    if (tenantId == null) return;

    switch (strategy.toLowerCase()) {
      case "database-per-tenant" ->
              TenantContext.setDbName(baseDb + "__" + tenantId.toHexString());
      case "collection-per-tenant" ->
              TenantContext.setCollectionSuffix("__" + tenantId.toHexString());
      default -> {
        // single: sin cambios
      }
    }
  }

  private void unauthorized(HttpServletResponse res, String msg) throws IOException {
    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
    res.setContentType("application/json");
    res.getWriter().write("{\"ok\":false,\"error\":\"unauthorized\",\"message\":\"" + msg + "\"}");
    res.getWriter().flush();
  }
}
