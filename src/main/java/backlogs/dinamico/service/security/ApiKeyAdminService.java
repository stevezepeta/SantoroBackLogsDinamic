package backlogs.dinamico.service.security;

import backlogs.dinamico.model.ingest.ApiKey;
import backlogs.dinamico.repository.catalog.ApiKeyRep;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;


@Service
@RequiredArgsConstructor
public class ApiKeyAdminService {

    private final ApiKeyRep repo;

    public record Generated(String id, String plain) {

    }

    public Generated create(ObjectId tenantId,
                            String name,
                            String systemIdStr,
                            String environmentIdStr,
                            List<String> scopes,
                            Instant expiresAt) {

        ObjectId systemId = new ObjectId(systemIdStr);
        ObjectId environmentId = new ObjectId(environmentIdStr);

        String plain = generatePlainKey("svc", "prod", name);
        String hash = sha256b64(plain);

        ApiKey doc = new ApiKey();
        doc.setTenantId(tenantId);
        doc.setName(name);
        doc.setSystemId(systemId);
        doc.setEnvironmentId(environmentId);
        doc.setScopes(scopes);
        doc.setKeyHash(hash);
        doc.setStatus("active");
        doc.setCreatedAt(Instant.now());
        doc.setExpiresAt(expiresAt);

        doc = repo.save(doc);
        return new Generated(doc.getId().toHexString(), plain);

    }

    public Generated rotate(ObjectId apiKeyId) {

        ApiKey doc = repo.findById(apiKeyId).orElseThrow();
        String plain = generatePlainKey("svc", "prod", doc.getName());
        String hash  = sha256b64(plain);
        doc.setKeyHash(hash);
        doc.setStatus("active");
        repo.save(doc);

        return new Generated(doc.getId().toHexString(), plain);
    }

    /* helpers */
    private static String generatePlainKey(String system, String env, String name) {
        var rnd = SecureRandomHolder.INSTANCE;
        byte[] bytes = new byte[32]; rnd.nextBytes(bytes);
        String suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String prefix = (system == null ? "svc" : system) + "_" + (env == null ? "prod" : env);
        return prefix + "_" + suffix; // ej: elyctis_prod_xxx...
    }

    private static String sha256b64(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static final class SecureRandomHolder {
        static final java.security.SecureRandom INSTANCE = new java.security.SecureRandom();
    }


}
