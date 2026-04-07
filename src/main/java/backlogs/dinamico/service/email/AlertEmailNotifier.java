package backlogs.dinamico.service.email;

import backlogs.dinamico.model.core.RoleCode;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.service.ai.dto.AlertEmailDto;
import backlogs.dinamico.service.ai.dto.SummaryInsightsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEmailNotifier {

    private final MongoTemplate mongoTemplate;
    private final EmailSenderPort emailSender;

    private static final Set<String> NOTIFY_ROLES =
            Set.of(RoleCode.ORG_OWNER.name(), RoleCode.ORG_ADMIN.name());

    // ── Throttle: máximo 1 correo por tenant cada 30 minutos ─────────────────
    private final java.util.concurrent.ConcurrentHashMap<ObjectId, Instant> lastSentAt
            = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long THROTTLE_MINUTES = 30;

    @Async
    public void notifyAlert(ObjectId tenantId, SummaryInsightsDto out) {
        if (tenantId == null || out == null) return;
        if (!"CRIT".equalsIgnoreCase(out.status) && !"WARN".equalsIgnoreCase(out.status)) return;

        // ── Throttle check ────────────────────────────────────────────────────
        Instant last = lastSentAt.get(tenantId);
        if (last != null && Instant.now().isBefore(last.plusSeconds(THROTTLE_MINUTES * 60))) {
            log.debug("[AlertEmail] Throttle activo para tenant {} — próximo envío en {} min",
                    tenantId,
                    java.time.Duration.between(Instant.now(),
                            last.plusSeconds(THROTTLE_MINUTES * 60)).toMinutes());
            return;
        }
        lastSentAt.put(tenantId, Instant.now());

        try {
            // 1. Encontrar roleIds de ORG_OWNER y ORG_ADMIN del tenant
            List<ObjectId> roleIds = mongoTemplate.find(
                            new Query(Criteria.where("tenant_id").is(tenantId)
                                    .and("code").in(NOTIFY_ROLES)),
                            org.bson.Document.class, "roles"
                    ).stream()
                        .map(d -> d.getObjectId("_id"))
                        .filter(java.util.Objects::nonNull)
                        .toList();

            if (roleIds.isEmpty()) {
                log.debug("[AlertEmail] No hay roles ORG_OWNER/ORG_ADMIN para tenant {}", tenantId);
                return;
            }

            // 2. Encontrar userIds asignados a esos roles
            List<ObjectId> userIds = mongoTemplate.find(
                            new Query(Criteria.where("tenant_id").is(tenantId)
                                    .and("role_id").in(roleIds)),
                            org.bson.Document.class, "user_roles"
                    ).stream()
                        .map(d -> d.getObjectId("user_id"))
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList();

            if (userIds.isEmpty()) {
                log.debug("[AlertEmail] No hay usuarios con roles admin para tenant {}", tenantId);
                return;
            }

            // 3. Obtener emails de usuarios activos
            List<User> users = mongoTemplate.find(
                    new Query(Criteria.where("_id").in(userIds)
                            .and("tenant_id").is(tenantId)
                            .and("status").is("active")),
                    User.class, "users"
            );

            if (users.isEmpty()) {
                log.debug("[AlertEmail] No hay usuarios activos para notificar en tenant {}", tenantId);
                return;
            }

            AlertEmailDto alertDto = new AlertEmailDto(
                    out.status,
                    out.granularity,
                    out.fromLocal,
                    out.toLocal,
                    out.total,
                    out.errorRate,
                    firstTopName(out.topSystemsRange),
                    firstTopCount(out.topSystemsRange),
                    firstTopKey(out.topErrorsRange),
                    out.warnings
            );

            // 5. Enviar a cada usuario
            for (User user : users) {
                if (user.getEmail() == null || user.getEmail().isBlank()) continue;
                try {
                    emailSender.sendAlertNotification(user.getEmail(), user.getName(), alertDto);
                    log.info("[AlertEmail] Notificación enviada a {} (tenant {})",
                            user.getEmail(), tenantId);
                } catch (Exception e) {
                    log.error("[AlertEmail] Error enviando a {}: {}", user.getEmail(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("[AlertEmail] Error general en notifyAlert: {}", e.getMessage());
        }
    }

    // Helpers ------------------------
    private String firstTopName(List<SummaryInsightsDto.TopItem> list) {
        if (list == null || list.isEmpty()) return "N/A";
        return list.get(0).name != null ? list.get(0).name : "N/A";
    }

    private long firstTopCount(List<SummaryInsightsDto.TopItem> list) {
        if (list == null || list.isEmpty()) return 0L;
        return list.get(0).count;
    }

    private String firstTopKey(List<SummaryInsightsDto.TopError> list) {
        if (list == null || list.isEmpty()) return null;
        return list.get(0).key;
    }

}
