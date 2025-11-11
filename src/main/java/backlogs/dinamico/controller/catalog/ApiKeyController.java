package backlogs.dinamico.controller.catalog;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.model.ingest.ApiKey;              // <-- usa la entidad de ingest
import backlogs.dinamico.repository.catalog.ApiKeyRep;     // tu repo (ver nota al final)
import backlogs.dinamico.tenant.TenantContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
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

  // --------- DTOs -------------
  @Data
  public static class CreateApiKeyReq {

    private String name;
    private String systemId;
    private String environmentId;
    private List<String> scopes;
    private Instant expiresAt; //

  }

  // --------- Helpers ----------
  private static String newPlainKey(String systemTag, String environmentTag, String name) {
    byte[] bytes = new byte[32];

    new SecureRandom().nextBytes(bytes);
    String suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    String sys = (systemTag == null || systemTag.isBlank()) ? "svc" : systemTag;
    String env = (environmentTag == null || environmentTag.isBlank()) ? "prod" : environmentTag;

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

    return "..."+keyHash.substring(keyHash.length()-6);
  }


  // ---------- Endpoints ---------------
  // Crea un API: guarda el HASH, devuelve el valor plano una sola vez
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody CreateApiKeyReq body) {

    ObjectId tenantId = TenantContext.getTenantId();
    if (tenantId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);

    // Generacion del plain
    String plain = newPlainKey("elyctis", "prod", body.getName());
    String hash = sha256b64(plain);

    // Se construyen las entidades
    ApiKey doc = new ApiKey();
    doc.setId(null);
    doc.setTenantId(tenantId);
    doc.setName(body.getName());
    doc.setSystemId(new ObjectId(body.getSystemId()));
    doc.setEnvironmentId(new ObjectId(body.getEnvironmentId()));
    doc.setScopes(body.getScopes());
    doc.setKeyHash(hash);
    doc. setStatus("active");
    doc.setCreatedAt(Instant.now());
    doc.setExpiresAt(body.getExpiresAt());

    doc = repo.save(doc);

    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(
                    "ApiKey creada",
                    "/api/catalogs/api-key",
                    Map.ofEntries(
                            entry("id", doc.getId().toHexString()),
                            entry("plain", plain),
                            entry("name", doc.getName()),
                            entry("systemId", doc.getSystemId()),
                            entry("environmentId", doc.getEnvironmentId()),
                            entry("scopes", doc.getScopes()),
                            entry("fingerprint", fingerprint(doc.getKeyHash()))
                    )
            ));

  }


    // Detalle de ApiKey
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable ObjectId id) {
      ApiKey doc = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

      return ResponseEntity.ok(ApiResponse.ok(
              "Detalle ApiKey",
              "/api/catalogs/api-keys/" + id,
              Map.ofEntries(
                      entry("id", doc.getId().toHexString()),
                      entry("tenantId", doc.getTenantId()),
                      entry("name", doc.getName()),
                      entry("systemId", doc.getSystemId()),
                      entry("environmentId", doc.getEnvironmentId()),
                      entry("scopes", doc.getScopes()),
                      entry("status", doc.getStatus()),
                      entry("expiresAt", doc.getExpiresAt()),
                      entry("createdAt", doc.getCreatedAt()),
                      entry("lastUsedAt", doc.getLastUsedAt()),
                      entry("fingerprint", fingerprint(doc.getKeyHash()))
              )
      ));
    }

    /** Revocar/Eliminar */
    @PreAuthorize("hasTenantPermission('apikeys:manage')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable ObjectId id) {
      if (!repo.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      repo.deleteById(id);
    }

}
