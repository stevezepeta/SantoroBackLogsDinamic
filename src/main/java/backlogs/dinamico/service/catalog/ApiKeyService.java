package backlogs.dinamico.service.catalog;

import backlogs.dinamico.api.dto.catalog.ApiKeyView;
import backlogs.dinamico.model.ingest.ApiKey;
import backlogs.dinamico.repository.catalog.ApiKeyRep;
import backlogs.dinamico.tenant.TenantContext;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

        if (ttlDays != null && ttlDays > 0 && rotateDays != null && rotateDays > ttlDays) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rotateDays_cannot_be_greater_than_ttlDays");
        }

        Instant expiresAt = null;
        if (ttlDays != null && ttlDays > 0) {
            expiresAt = now.plus(ttlDays, ChronoUnit.DAYS);
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

    @Transactional
    public ApiKey renew(ObjectId id, Long ttlDays, Long rotateDays) {

        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        ApiKey key = repo.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "api_key_not_resolved"));

        // Si quieres bloquear renovaciones cuando ya fue "rotated" (o revocada) puedes hacerlo:
         if ("rotated".equalsIgnoreCase(key.getStatus())) {
             throw new ResponseStatusException(HttpStatus.CONFLICT, "api_key_rotated_cannot_be_renewed");
         }

        Instant now = Instant.now();

        // Defaults (si no mandan nada en el body)
        long ttl = (ttlDays != null && ttlDays > 0) ? ttlDays : 90L;
        Long rot = (rotateDays != null && rotateDays > 0) ? rotateDays : null;

        if (rot != null && rot > ttl) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rotateDays_cannot_be_greater_than_ttlDays");
        }

        key.setStatus("active");
        key.setExpiresAt(now.plus(ttl, ChronoUnit.DAYS));

        if (rot != null) {
            key.setRotatesAt(now.plus(rot, ChronoUnit.DAYS));
        } else {
            key.setRotatesAt(null);
        }

        return repo.save(key);
    }

    @Transactional(readOnly = true)
    public Page<ApiKeyView> listMine(String status, String q, int page, int size) {

        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");

        if (page < 0) page = 0;
        if (size < 1) size = 10;
        if (size > 200) size = 200;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ApiKey> keys;

        boolean hasStatus = StringUtils.hasText(status);
        boolean hasQ = StringUtils.hasText(q);

        if (hasStatus && hasQ) {
            keys = repo.findByTenantIdAndStatusAndNameContainingIgnoreCase(tenantId, status.trim(), q.trim(), pageable);
        } else if (hasStatus) {
            keys = repo.findByTenantIdAndStatus(tenantId, status.trim(), pageable);
        } else if (hasQ) {
            keys = repo.findByTenantIdAndNameContainingIgnoreCase(tenantId, q.trim(), pageable);
        } else {
            keys = repo.findByTenantId(tenantId, pageable);
        }

        return keys.map(this::toView);
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

    private ApiKeyView toView(ApiKey k) {
        return new ApiKeyView(
                k.getId() != null ? k.getId().toHexString() : null,
                k.getName(),
                k.getStatus(),
                k.getScopes(),
                k.getSystemId() != null ? k.getSystemId().toHexString() : null,
                k.getEnvironmentId() != null ? k.getEnvironmentId().toHexString() : null,
                k.getCreatedAt(),
                k.getUpdatedAt(),
                k.getLastUsedAt(),
                k.getExpiresAt(),
                k.getRotatesAt()
        );
    }

}
