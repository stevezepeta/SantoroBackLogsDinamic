package backlogs.dinamico.service.notifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Gestiona los tokens FCM de los usuarios en MongoDB.
 * Un usuario puede tener múltiples tokens (varios dispositivos).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmTokenService {

    private final MongoTemplate mongoTemplate;
    private static final String COLLECTION = "fcm_tokens";

    /**
     * Registra o actualiza el token FCM de un usuario.
     */
    public void saveToken(ObjectId tenantId, ObjectId userId, String token) {
        if (token == null || token.isBlank()) return;

        Query query = new Query(new Criteria().andOperator(
                Criteria.where("tenantId").is(tenantId),
                Criteria.where("userId").is(userId),
                Criteria.where("token").is(token)
        ));

        Update update = new Update()
                .set("tenantId",  tenantId)
                .set("userId",    userId)
                .set("token",     token)
                .set("updatedAt", Instant.now());

        mongoTemplate.upsert(query, update, COLLECTION);
        log.info("[FCM] Token guardado para user: {}", userId);
    }

    /**
     * Obtiene todos los tokens activos de un tenant.
     */
    public List<String> getTokensByTenant(ObjectId tenantId) {
        Query query = new Query(Criteria.where("tenantId").is(tenantId));
        return mongoTemplate.find(query, org.bson.Document.class, COLLECTION)
                .stream()
                .map(d -> d.getString("token"))
                .filter(t -> t != null && !t.isBlank())
                .toList();
    }

    /**
     * Elimina un token inválido.
     */
    public void deleteToken(String token) {
        mongoTemplate.remove(
                new Query(Criteria.where("token").is(token)), COLLECTION);
    }
}