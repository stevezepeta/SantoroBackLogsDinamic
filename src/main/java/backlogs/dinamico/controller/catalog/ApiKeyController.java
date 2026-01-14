package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.model.ingest.ApiKey;
import backlogs.dinamico.repository.catalog.ApiKeyRep;
import backlogs.dinamico.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

@RestController
@RequestMapping("/api/catalogs/api-keys")
@CrossOrigin
@RequiredArgsConstructor
public class ApiKeyController {

  private final ApiKeyRep repo;

  @Data
  public static class CreateApiKeyReq {
    @NotBlank private String name;
    @NotBlank private String systemId;
    @NotBlank private String environmentId;
    @NotEmpty private List<String> scopes;
    private Instant expiresAt;

    // opcionales (los estabas mandando en Postman)
    private String systemTag;
    private String environmentTag;
  }

  private static final SecureRandom RNG = new SecureRandom();

  private static String newPlainKey(String systemTag, String environmentTag) {
    byte[] bytes = new byte[32];
    RNG.nextBytes(bytes);

    String suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    String sys = (systemTag == null || systemTag.isBlank()) ? "svc" : systemTag.trim();
    String env = (environmentTag == null || environmentTag.isBlank()) ? "prod" : environmentTag.trim();

    return sys + "_" + env + "_" + suffix;
  }

  private static String sha256b64(String s) {
    try {
      var md = MessageDigest.getInstance("SHA-256");
      return Base64.getEncoder().encodeToString(md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String fingerprint(String keyHash) {
    if (keyHash == null || keyHash.length() < 6) return "***";
    return "..." + keyHash.substring(keyHash.length() - 6);
  }

  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public ResponseEntity<ApiResponse<Map<String, Object>>> create(
          @AuthenticationPrincipal AuthUser me,
          @Valid @RequestBody CreateApiKeyReq body
  ) {
    if (me == null || me.tenantId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);

    // IMPORTANTE: asegurar TenantContext para repos/tenant-mongoTemplate
    ObjectId prev = TenantContext.getTenantId();
    try {
      TenantContext.setTenantId(me.tenantId());

      String plain = newPlainKey(body.getSystemTag(), body.getEnvironmentTag());
      String hash = sha256b64(plain);

      ApiKey doc = new ApiKey();
      doc.setId(null);
      doc.setTenantId(me.tenantId());
      doc.setName(body.getName());
      doc.setSystemId(new ObjectId(body.getSystemId()));
      doc.setEnvironmentId(new ObjectId(body.getEnvironmentId()));
      doc.setScopes(body.getScopes());
      doc.setKeyHash(hash);
      doc.setStatus("active");
      doc.setCreatedAt(Instant.now());
      doc.setExpiresAt(body.getExpiresAt());

      doc = repo.save(doc);

      return ResponseEntity.status(HttpStatus.CREATED).body(
              ApiResponse.created(
                      "ApiKey creada",
                      "/api/catalogs/api-keys",
                      Map.ofEntries(
                              entry("id", doc.getId().toHexString()),
                              entry("plain", plain), // devuélvelo solo aquí; guárdalo porque NO lo podrás recuperar
                              entry("name", doc.getName()),
                              entry("tenantId", doc.getTenantId().toHexString()),
                              entry("systemId", doc.getSystemId().toHexString()),
                              entry("environmentId", doc.getEnvironmentId().toHexString()),
                              entry("scopes", doc.getScopes()),
                              entry("status", doc.getStatus()),
                              entry("expiresAt", doc.getExpiresAt()),
                              entry("createdAt", doc.getCreatedAt()),
                              entry("fingerprint", fingerprint(doc.getKeyHash()))
                      )
              )
      );
    } finally {
      // restaurar contexto previo
      if (prev == null) TenantContext.clear();
      else TenantContext.setTenantId(prev);
    }
  }

  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<Map<String, Object>>> get(@AuthenticationPrincipal AuthUser me, @PathVariable ObjectId id) {
    if (me == null || me.tenantId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);

    ObjectId prev = TenantContext.getTenantId();
    try {
      TenantContext.setTenantId(me.tenantId());

      ApiKey doc = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

      return ResponseEntity.ok(ApiResponse.ok(
              "Detalle ApiKey",
              "/api/catalogs/api-keys/" + id,
              Map.ofEntries(
                      entry("id", doc.getId().toHexString()),
                      entry("tenantId", doc.getTenantId().toHexString()),
                      entry("name", doc.getName()),
                      entry("systemId", doc.getSystemId().toHexString()),
                      entry("environmentId", doc.getEnvironmentId().toHexString()),
                      entry("scopes", doc.getScopes()),
                      entry("status", doc.getStatus()),
                      entry("expiresAt", doc.getExpiresAt()),
                      entry("createdAt", doc.getCreatedAt()),
                      entry("lastUsedAt", doc.getLastUsedAt()),
                      entry("fingerprint", fingerprint(doc.getKeyHash()))
              )
      ));
    } finally {
      if (prev == null) TenantContext.clear();
      else TenantContext.setTenantId(prev);
    }
  }

  @PreAuthorize("hasTenantPermission('apikeys:manage')")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable ObjectId id) {
    if (me == null || me.tenantId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);

    ObjectId prev = TenantContext.getTenantId();
    try {
      TenantContext.setTenantId(me.tenantId());

      if (!repo.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      repo.deleteById(id);
    } finally {
      if (prev == null) TenantContext.clear();
      else TenantContext.setTenantId(prev);
    }
  }
}
