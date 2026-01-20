package backlogs.dinamico.service.catalog;

import backlogs.dinamico.model.ingest.ApiKey;
import backlogs.dinamico.repository.catalog.ApiKeyRep;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import static backlogs.dinamico.security.KeyHasher.sha256b64;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRep repo;

    public record CreatedKey(ApiKey apiKey, String plainKey) {}

    // Creando un API KEY nueva y regresar el plaintext solo una vez
    @Transactional
    public CreatedKey create(
            String name,
            ObjectId systemId,
            ObjectId environmentId,
            List<String> scopes,
            Long ttlDays,
            Long rotateDays
    ) {

        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        if (!StringUtils.hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name_required");
        }

        List<String> normalizedScopes = normalizeScopes(scopes);

        // Generar plaintext
        String plain = generatePlainKey();
        String hash = sha256b64(plain);

        Instant now = Instant.now();

        Instant expiresAt = null;
        if (ttlDays != null && rotateDays != null && rotateDays > ttlDays) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rotateDays_cannot_be_greater_than_ttlDays");
        }

        Instant rotatesAt = null;
        if (rotateDays != null && rotateDays > 0) {
            rotatesAt = now.plus(rotateDays, ChronoUnit.DAYS);
        }

        ApiKey key = ApiKey.builder()
                .tenantId(tenantId)
                .systemId(systemId)
                .environmentId(environmentId)
                .name(name.trim())
                .keyHash(hash)
                .scopes(normalizedScopes)
                .status("active")
                .lastUsedAt(null)
                .expiresAt(expiresAt)
                .rotatesAt(rotatesAt)
                .build();

        ApiKey saved = repo.save(key);
        return new CreatedKey(saved, plain);
    }

    public List<ApiKey> listMine() {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");
        return repo.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public ApiKey disable(ObjectId id) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        ApiKey key = repo.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "api_key_not_resolved"));

        key.setStatus("disabled");
        return repo.save(key);
    }

    // Rota desactiva la anterior y crea otra con el mismo "shape"
    @Transactional
    public CreatedKey rotate(ObjectId id) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        ApiKey old = repo.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "api_key_not_resolved"));

        // Desactivar el anterior
        old.setStatus("rotated");
        repo.save(old);

        // Crear nueva con mismo config
        return create(
                old.getName() + " (rotated)",
                old.getSystemId(),
                old.getEnvironmentId(),
                old.getScopes(),
                null,
                null
        );
    }

    // ---------- helpers ----------
    private static List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null) return List.of("LOGS_INGEST");

        return scopes.stream()
                .filter(StringUtils::hasText)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static String generatePlainKey() {
        // Formato: bk_<base64url>
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return "bk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

}
