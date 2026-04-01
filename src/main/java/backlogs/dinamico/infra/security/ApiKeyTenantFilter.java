package backlogs.dinamico.infra.security;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.config.RateLimitProperties;
import backlogs.dinamico.model.core.RateLimitPolicy;
import backlogs.dinamico.model.ingest.ApiKey;
import backlogs.dinamico.repository.catalog.ApiKeyRep;
import backlogs.dinamico.service.catalog.RateLimitPolicyService;
import backlogs.dinamico.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
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
import java.time.Duration;
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
  private final ObjectMapper objectMapper;

  private final RateLimitPolicyService rateLimitPolicyService;
  private final RateLimitProperties rateLimitProps;

  @Value("${multitenant.require-api-key:true}")
  private boolean requireApiKey;

  @Value("${multitenant.strategy:collection-per-tenant}")
  private String strategy;

  @Value("${multitenant.base-database:logs_system}")
  private String baseDb;

  @Value("${backlogs.allow-public-create-organization:true}")
  private boolean allowPublicCreateOrg;

  // Caches con TTL (en vez de ConcurrentHashMap infinito)
  private Cache<String, Bucket> apiKeyBuckets;
  private Cache<String, Bucket> tenantBuckets;

  @PostConstruct
  void onInit() {
    apiKeyBuckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(rateLimitProps.getCache().getTtlMinutes()))
            .maximumSize(rateLimitProps.getCache().getMaxSize())
            .build();

    tenantBuckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(rateLimitProps.getCache().getTtlMinutes()))
            .maximumSize(rateLimitProps.getCache().getMaxSize())
            .build();

    log.warn("[RateLimitProps] enabled={}, apiKey={} / {}/{}, tenant={} / {}/{}, cache ttlMin={}, maxSize={}",
            rateLimitProps.isEnabled(),
            rateLimitProps.getApiKey().getCapacity(),
            rateLimitProps.getApiKey().getRefillTokens(),
            rateLimitProps.getApiKey().getRefillSeconds(),
            rateLimitProps.getTenant().getCapacity(),
            rateLimitProps.getTenant().getRefillTokens(),
            rateLimitProps.getTenant().getRefillSeconds(),
            rateLimitProps.getCache().getTtlMinutes(),
            rateLimitProps.getCache().getMaxSize()
    );
  }

  private static boolean isIngest(HttpServletRequest req) {
    if (!HttpMethod.POST.matches(req.getMethod())) return false;

    String uri = req.getRequestURI();
    return uri.startsWith("/api/logs/events")
            || uri.equals("/api/logs") || uri.startsWith("/api/logs/")
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
    if (isExcluded(req)) return true;

    if (allowPublicCreateOrg
            && HttpMethod.POST.matches(req.getMethod())
            && "/api/catalogs/organizations".equals(req.getRequestURI())) {
      return true;
    }

    return !isIngest(req);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req,
                                  HttpServletResponse res,
                                  FilterChain chain) throws ServletException, IOException {

    final String path = req.getRequestURI();
    log.info("[ApiKeyTenantFilter] ENTER path={}, method={}, requireApiKey={}", path, req.getMethod(), requireApiKey);

    if (!requireApiKey) {
      chain.doFilter(req, res);
      return;
    }

    String apiKeyPlain = firstNonBlank(
            req.getHeader("X-Api-Key"),
            req.getHeader("X-API-Key"),
            req.getHeader("x-api-key")
    );

    boolean ingest = isIngest(req);
    log.info("[ApiKeyTenantFilter] ingest={}, xApiKeyPresent={}", ingest, StringUtils.hasText(apiKeyPlain));

    if (!StringUtils.hasText(apiKeyPlain)) {
      unauthorized(res, "missing_x_api_key", "Falta header X-Api-Key");
      return;
    }

    String hash = sha256b64(apiKeyPlain);
    var opt = apiKeyRepo.findByKeyHashAndStatus(hash, "active");
    if (opt.isEmpty()) {
      unauthorized(res, "invalid_api_key", "API Key inválida");
      return;
    }

    ApiKey key = opt.get();
    Instant now = Instant.now();

    // Expirado = bloqueo duro
    if (key.getExpiresAt() != null && now.isAfter(key.getExpiresAt())) {
      unauthorized(res, "api_key_expired", "API Key expirada",
              java.util.Map.of("expiresAt", key.getExpiresAt(), "rotatesAt", key.getRotatesAt()));
      return;
    }

    // Rotación vencida = warning (no bloquea)
    boolean requiresRotation = key.getRotatesAt() != null && now.isAfter(key.getRotatesAt());
    if (requiresRotation) {
      log.warn("[ApiKeyTenantFilter] API key requires renewal/rotation. tenant={}, apiKeyId={}, rotatesAt={}, expiresAt={}",
              key.getTenantId(), key.getId(), key.getRotatesAt(), key.getExpiresAt());
      TenantContext.setRequiresRotation(true);
    }

    // scope LOGS_INGEST requerido
    if (key.getScopes() != null && !key.getScopes().isEmpty()) {
      boolean okScope = key.getScopes().stream()
              .filter(StringUtils::hasText)
              .map(s -> s.trim().toUpperCase(Locale.ROOT))
              .anyMatch(s -> s.equals("LOGS_INGEST") || s.equals("INGEST") || s.equals("ALL"));

      if (!okScope) {
        unauthorized(res, "missing_scope_logs_ingest", "Falta scope LOGS_INGEST");
        return;
      }
    }

    TenantContext.setForIngest(key.getTenantId(), key.getSystemId(), key.getEnvironmentId());
    applyStrategy(key.getTenantId());

    // --------------------- RATE LIMIT DIAGNOSTIC LOGS -------------------------
    boolean rlEnabled = rateLimitProps != null && rateLimitProps.isEnabled();

    log.warn("[RL] switches: rateLimitProps.enabled={}, tenantId={}, apiKeyId={}",
            rlEnabled,
            (key.getTenantId() == null ? "null" : key.getTenantId().toHexString()),
            (key.getId() == null ? "null" : key.getId().toHexString())
    );

    // --------------------- RATE LIMIT (DOBLE CAPA) -------------------------
    if (rlEnabled) {
      log.warn("[RL] ENTER rateLimit block");

      RateLimitPolicy fallback = fallbackPolicy(); // ahora es conservador
      RateLimitPolicy policy = rateLimitPolicyService.resolve(key.getTenantId(), fallback);

      if (policy == null) {
        log.warn("[RL] resolved policy = NULL -> skip");
      } else if (!policy.isEnabled()) {
        log.warn("[RL] policy.enabled=false -> skip");
      } else {

        RateLimitPolicy.BucketPolicy ak = policy.getApiKey();
        RateLimitPolicy.BucketPolicy tp = policy.getTenant();

        log.warn("[RL] policy.enabled=true, ak={}/{}/{}, tp={}/{}/{}, fallback ak={}/{}/{}, fallback tp={}/{}/{}",
                safeCap(ak), safeRefillTokens(ak), safeRefillSeconds(ak),
                safeCap(tp), safeRefillTokens(tp), safeRefillSeconds(tp),
                safeCap(fallback.getApiKey()), safeRefillTokens(fallback.getApiKey()), safeRefillSeconds(fallback.getApiKey()),
                safeCap(fallback.getTenant()), safeRefillTokens(fallback.getTenant()), safeRefillSeconds(fallback.getTenant())
        );

        String tenantHex = (key.getTenantId() != null) ? key.getTenantId().toHexString() : "no_tenant";
        String tenantBucketKey = tenantHex + ":" + sig(tp);
        String apiKeyBucketKey = hash + ":" + sig(ak);

        log.warn("[RL] bucketKeys: tenantBucketKey={}, apiKeyBucketKey={}", tenantBucketKey, apiKeyBucketKey);

        Bucket apiKeyBucket = apiKeyBuckets.get(apiKeyBucketKey, k -> buildBucket(ak));
        Bucket tenantBucket = tenantBuckets.get(tenantBucketKey, k -> buildBucket(tp));

        ConsumptionProbe pApiKey = apiKeyBucket.tryConsumeAndReturnRemaining(1);
        log.warn("[RL] consume apiKey: consumed={}, remaining={}, waitNanos={}",
                pApiKey.isConsumed(), pApiKey.getRemainingTokens(), pApiKey.getNanosToWaitForRefill());

        if (!pApiKey.isConsumed()) {
          log.warn("[RL] 429 by APIKEY bucket waitNanos={}", pApiKey.getNanosToWaitForRefill());
          reply429(res, pApiKey.getNanosToWaitForRefill());
          return;
        }

        ConsumptionProbe pTenant = tenantBucket.tryConsumeAndReturnRemaining(1);
        log.warn("[RL] consume tenant: consumed={}, remaining={}, waitNanos={}",
                pTenant.isConsumed(), pTenant.getRemainingTokens(), pTenant.getNanosToWaitForRefill());

        if (!pTenant.isConsumed()) {
          log.warn("[RL] 429 by TENANT bucket waitNanos={}", pTenant.getNanosToWaitForRefill());
          reply429(res, pTenant.getNanosToWaitForRefill());
          return;
        }

        res.setHeader("X-RateLimit-Remaining-Tenant", String.valueOf(pTenant.getRemainingTokens()));
        res.setHeader("X-RateLimit-Remaining-ApiKey", String.valueOf(pApiKey.getRemainingTokens()));
        res.setHeader("X-RateLimit-Policy", "tenant+apikey");

        log.warn("[RL] OK -> headers set: remTenant={}, remApiKey={}",
                pTenant.getRemainingTokens(), pApiKey.getRemainingTokens());
      }
    } else {
      log.warn("[RL] SKIP rateLimit block because rateLimitProps.enabled=false or props missing");
    }

    // lastUsedAt best effort
    try {
      key.setLastUsedAt(now);
      apiKeyRepo.save(key);
    } catch (Exception ignore) {}

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

  // --------------------- RATE LIMIT HELPER'S --------------------------------
  private RateLimitPolicy fallbackPolicy() {
    RateLimitPolicy p = new RateLimitPolicy();

    // Si props no están, fallback ULTRA conservador
    if (rateLimitProps == null) {
      p.setEnabled(true);
      p.setApiKey(defaultBucketPolicy(3, 3, 60));
      p.setTenant(defaultBucketPolicy(5, 5, 60));
      return p;
    }

    p.setEnabled(rateLimitProps.isEnabled());

    // Usa lo del YAML, pero si viene null o inválido aplica defaults CONSERVADORES
    p.setApiKey(toBucketPolicySafe(rateLimitProps.getApiKey(), 3, 3, 60));
    p.setTenant(toBucketPolicySafe(rateLimitProps.getTenant(), 5, 5, 60));

    return p;
  }


  private static RateLimitPolicy.BucketPolicy toBucketPolicySafe(
          backlogs.dinamico.config.RateLimitProperties.Bucket src,
          long defCap,
          long defRefillTokens,
          long defRefillSeconds
  ) {
    long cap = defCap;
    long refillTokens = defRefillTokens;
    long refillSeconds = defRefillSeconds;

    if (src != null) {
      cap = src.getCapacity();
      refillTokens = src.getRefillTokens();
      refillSeconds = src.getRefillSeconds();
    }

    // Clamps (nunca 0/negativo)
    cap = Math.max(1, cap);
    refillTokens = Math.max(1, refillTokens);
    refillSeconds = Math.max(1, refillSeconds);

    RateLimitPolicy.BucketPolicy out = new RateLimitPolicy.BucketPolicy();
    out.setCapacity(cap);
    out.setRefillTokens(refillTokens);
    out.setRefillSeconds(refillSeconds);
    return out;
  }

  private static RateLimitPolicy.BucketPolicy defaultBucketPolicy(long cap, long refillTokens, long refillSeconds) {
    RateLimitPolicy.BucketPolicy out = new RateLimitPolicy.BucketPolicy();
    out.setCapacity(Math.max(1, cap));
    out.setRefillTokens(Math.max(1, refillTokens));
    out.setRefillSeconds(Math.max(1, refillSeconds));
    return out;
  }

  private static String sig(RateLimitPolicy.BucketPolicy bp) {
    if (bp == null) return "null";

    return (bp.getCapacity() + ":" + bp.getRefillTokens() + ":" + bp.getRefillSeconds());
  }

  private Bucket newBucketApiKey(RateLimitPolicy.BucketPolicy bp) {
    if (bp == null) bp = toBucket(rateLimitProps.getApiKey());
    return buildBucket(bp);
  }

  private Bucket newBucketTenant(RateLimitPolicy.BucketPolicy bp) {
    if (bp == null) bp = toBucket(rateLimitProps.getTenant());
    return buildBucket(bp);
  }

  private static RateLimitPolicy.BucketPolicy toBucket(RateLimitProperties.Bucket cfg) {
    var bp = new RateLimitPolicy.BucketPolicy();
    bp.setCapacity(cfg.getCapacity());
    bp.setRefillTokens(cfg.getRefillTokens());
    bp.setRefillSeconds(cfg.getRefillSeconds());
    return bp;
  }

  private Bucket buildBucket(RateLimitPolicy.BucketPolicy bp) {
    if (bp == null) {
      bp = defaultBucketPolicy(1, 1, 60);
    }

    long cap = Math.max(1, bp.getCapacity());
    long refillTokens = Math.max(1, bp.getRefillTokens());
    long refillSeconds = Math.max(1, bp.getRefillSeconds());

    Bandwidth limit = Bandwidth.classic(
            cap,
            Refill.intervally(refillTokens, Duration.ofSeconds(refillSeconds))
    );
    return Bucket.builder().addLimit(limit).build();
  }

  private void reply429(HttpServletResponse res, long waitNanos) throws IOException {
    long retryAfterSeconds = Math.max(1, Duration.ofNanos(waitNanos).getSeconds());
    res.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
    res.setHeader("X-RateLimit-Remaining", "0");

    tooManyRequests(res,
            "rate_limited",
            "Rate limit excedido. Intenta de nuevo en " + retryAfterSeconds + "s",
            retryAfterSeconds
    );
  }

  // ----------------------- HELPER'S -------------------------
  private static long safeCap(RateLimitPolicy.BucketPolicy bp) { return bp == null ? -1 : bp.getCapacity(); }
  private static long safeRefillTokens(RateLimitPolicy.BucketPolicy bp) { return bp == null ? -1 : bp.getRefillTokens(); }
  private static long safeRefillSeconds(RateLimitPolicy.BucketPolicy bp) { return bp == null ? -1 : bp.getRefillSeconds(); }

  // ----------------------- MULTITENANCY ---------------------
  private void applyStrategy(ObjectId tenantId) {
    if (tenantId == null) return;

    switch (strategy.toLowerCase()) {
      case "database-per-tenant" ->
              TenantContext.setDbName(baseDb + "__" + tenantId.toHexString());
      case "collection-per-tenant" ->
              TenantContext.setCollectionSuffix("__" + tenantId.toHexString());
      default -> { }
    }
  }

  private static String firstNonBlank(String... xs) {
    if (xs == null) return null;
    for (String x : xs) {
      if (x != null && !x.isBlank()) return x.trim();
    }
    return null;
  }


  // -------------------------- RESPONSE ---------------------------
  private void unauthorized(HttpServletResponse res, String code, String message) throws IOException {
    unauthorized(res, code, message, null);
  }

  private void unauthorized(HttpServletResponse res, String code, String message, Object data) throws IOException {
    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
    res.setContentType("application/json");

    var body = ApiResponse.error(code, message, null, data);
    res.getWriter().write(objectMapper.writeValueAsString(body));
    res.getWriter().flush();
  }

  private void tooManyRequests(HttpServletResponse res, String code, String message, long retryAfterSeconds) throws IOException {
    res.setStatus(429);
    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
    res.setContentType("application/json");

    var data = java.util.Map.of("retryAfterSeconds", retryAfterSeconds);
    var body = ApiResponse.error(code, message, null, data);
    res.getWriter().write(objectMapper.writeValueAsString(body));
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
