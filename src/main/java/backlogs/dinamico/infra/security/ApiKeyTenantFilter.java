package backlogs.dinamico.infra.security;

import backlogs.dinamico.model.ingest.ApiKey;
import backlogs.dinamico.repository.catalog.ApiKeyRep;
import backlogs.dinamico.tenant.TenantContext;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static backlogs.dinamico.security.KeyHasher.sha256b64;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class ApiKeyTenantFilter extends OncePerRequestFilter {

  private final ApiKeyRep apiKeyRepo;

  @Value("${multitenant.require-api-key:true}")
  private boolean requireApiKey;

  @Value("${multitenant.strategy:collection-per-tenant}")
  private String strategy;

  @Value("${multitenant.base-database:logs_system}")
  private String baseDb;

  @Value("${backlogs.allow-public-create-organization:true}")
  private boolean allowPublicCreateOrg;

  @PostConstruct
  void onInit() {
    log.warn("[ApiKeyTenantFilter] Registrado. requireApiKey={}, strategy={}", requireApiKey, strategy);
  }

  // ✅ detección robusta (sin depender de /**)
  private static boolean isIngest(HttpServletRequest req) {
    if (!HttpMethod.POST.matches(req.getMethod())) return false;

    String uri = req.getRequestURI();
    return uri.startsWith("/api/logs/events")
            || uri.equals("/api/logs") || uri.startsWith("/api/logs/")     // si algún día tienes /api/logs/bulk
            || uri.startsWith("/api/ingest/")
            || uri.startsWith("/api/fingerprint/");
  }

  private static boolean isExcluded(HttpServletRequest req) {
    String uri = req.getRequestURI();
    return uri.startsWith("/error")
            || uri.startsWith("/actuator/")
            || uri.startsWith("/api/auth/")
            || uri.startsWith("/ws/");
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    if (HttpMethod.OPTIONS.matches(req.getMethod())) return true;

    // No estorbar en rutas públicas
    if (isExcluded(req)) return true;

    // permitir crear org sin api-key
    if (allowPublicCreateOrg
            && HttpMethod.POST.matches(req.getMethod())
            && "/api/catalogs/organizations".equals(req.getRequestURI())) {
      return true;
    }

    // ✅ SOLO aplicar a ingesta
    return !isIngest(req);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req,
                                  HttpServletResponse res,
                                  FilterChain chain) throws ServletException, IOException {

    final String path = req.getRequestURI();

    // 🔥 ESTE LOG debe salir SÍ o SÍ cuando pegues a /api/logs/events
    log.info("[ApiKeyTenantFilter] ENTER path={}, method={}, requireApiKey={}",
            path, req.getMethod(), requireApiKey);

    if (!requireApiKey) {
      chain.doFilter(req, res);
      return;
    }

    // Lee header api key (case variants)
    String apiKeyPlain = firstNonBlank(req.getHeader("X-Api-Key"), req.getHeader("X-API-Key"), req.getHeader("x-api-key"));

    log.info("[ApiKeyTenantFilter] ingest={}, xApiKeyPresent={}",
            true, StringUtils.hasText(apiKeyPlain));

    if (!StringUtils.hasText(apiKeyPlain)) {
      unauthorized(res, "missing_x_api_key");
      return;
    }

    // Buscar hash activo
    String hash = sha256b64(apiKeyPlain);
    var opt = apiKeyRepo.findByKeyHashAndStatus(hash, "active");
    if (opt.isEmpty()) {
      unauthorized(res, "invalid_api_key");
      return;
    }

    ApiKey key = opt.get();
    Instant now = Instant.now();

    // expiración / rotación
    if (key.getExpiresAt() != null && now.isAfter(key.getExpiresAt())) {
      unauthorized(res, "api_key_expired");
      return;
    }
    if (key.getRotatesAt() != null && now.isAfter(key.getRotatesAt())) {
      unauthorized(res, "api_key_requires_rotation");
      return;
    }

    // scope LOGS_INGEST requerido
    if (key.getScopes() != null && !key.getScopes().isEmpty()) {
      boolean okScope = key.getScopes().stream()
              .filter(StringUtils::hasText)
              .map(s -> s.trim().toUpperCase(Locale.ROOT))
              .anyMatch(s -> s.equals("LOGS_INGEST") || s.equals("INGEST") || s.equals("ALL"));

      if (!okScope) {
        unauthorized(res, "missing_scope_logs_ingest");
        return;
      }
    }

    // lastUsedAt best effort
    try {
      key.setLastUsedAt(now);
      apiKeyRepo.save(key);
    } catch (Exception ignore) {}

    // ✅ 1) TenantContext para multitenancy
    TenantContext.setForIngest(key.getTenantId(), key.getSystemId(), key.getEnvironmentId());
    applyStrategy(key.getTenantId());

    // ✅ 2) Authentication para que NO sea anonymous en tu controller
    var principal = new ApiKeyPrincipal(
            key.getId(),
            key.getTenantId(),
            key.getSystemId(),
            key.getEnvironmentId(),
            key.getScopes()
    );

    var auth = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(
                    new SimpleGrantedAuthority("ROLE_API_KEY"),
                    new SimpleGrantedAuthority("PERM_LOG_INGEST")
            )
    );
    SecurityContextHolder.getContext().setAuthentication(auth);

    try {
      chain.doFilter(req, res);
    } finally {
      SecurityContextHolder.clearContext();
      TenantContext.clear();
    }
  }

  private void applyStrategy(ObjectId tenantId) {
    if (tenantId == null) return;

    switch (strategy.toLowerCase()) {
      case "database-per-tenant" ->
              TenantContext.setDbName(baseDb + "__" + tenantId.toHexString());
      case "collection-per-tenant" ->
              TenantContext.setCollectionSuffix("__" + tenantId.toHexString());
      default -> {
        // single
      }
    }
  }

  private static String firstNonBlank(String... xs) {
    if (xs == null) return null;
    for (String x : xs) {
      if (x != null && !x.isBlank()) return x.trim();
    }
    return null;
  }

  private void unauthorized(HttpServletResponse res, String msg) throws IOException {
    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
    res.setContentType("application/json");
    res.getWriter().write("{\"ok\":false,\"code\":\"unauthorized\",\"message\":\"" + msg + "\"}");
    res.getWriter().flush();
  }

  @Getter
  public static class ApiKeyPrincipal {
    private final ObjectId apiKeyId;
    private final ObjectId tenantId;
    private final ObjectId systemId;
    private final ObjectId environmentId;
    private final List<String> scopes;

    public ApiKeyPrincipal(ObjectId apiKeyId, ObjectId tenantId, ObjectId systemId, ObjectId environmentId, List<String> scopes) {
      this.apiKeyId = apiKeyId;
      this.tenantId = tenantId;
      this.systemId = systemId;
      this.environmentId = environmentId;
      this.scopes = scopes;
    }
  }
}
