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

    /**
     * Envía el reporte PDF por correo a ORG_OWNER y ORG_ADMIN.
     * Llamado exclusivamente desde AlertSchedulerService.
     */
    @Async
    public void sendReportEmail(
            ObjectId           tenantId,
            String             system,
            SummaryInsightsDto out,
            byte[]             pdfBytes,
            String             reportType
    ) {
        if (tenantId == null || pdfBytes == null) return;

        try {
            List<ObjectId> roleIds = mongoTemplate.find(
                            new Query(Criteria.where("tenant_id").is(tenantId)
                                    .and("code").in(NOTIFY_ROLES)),
                            org.bson.Document.class, "roles"
                    ).stream().map(d -> d.getObjectId("_id"))
                    .filter(java.util.Objects::nonNull).toList();

            if (roleIds.isEmpty()) return;

            List<ObjectId> userIds = mongoTemplate.find(
                            new Query(Criteria.where("tenant_id").is(tenantId)
                                    .and("role_id").in(roleIds)),
                            org.bson.Document.class, "user_roles"
                    ).stream().map(d -> d.getObjectId("user_id"))
                    .filter(java.util.Objects::nonNull).distinct().toList();

            if (userIds.isEmpty()) return;

            List<backlogs.dinamico.model.core.User> users = mongoTemplate.find(
                    new Query(Criteria.where("_id").in(userIds)
                            .and("tenant_id").is(tenantId)
                            .and("status").is("active")),
                    backlogs.dinamico.model.core.User.class, "users"
            );

            boolean isEscalation = "ESCALATION".equalsIgnoreCase(reportType);
            String subject = isEscalation
                    ? "🚨 [DataLogs] ESCALADA — " + system + " · errorRate "
                    + String.format("%.1f%%", out.errorRate * 100)
                    : "🔴 [DataLogs] Reporte diario de alerta — " + system;

            String fileName = "reporte-alerta-" + system.toLowerCase()
                    .replace("_", "-") + "-"
                    + java.time.LocalDate.now() + ".pdf";

            for (backlogs.dinamico.model.core.User user : users) {
                if (user.getEmail() == null || user.getEmail().isBlank()) continue;
                try {
                    emailSender.sendAlertWithPdf(
                            user.getEmail(), user.getName(),
                            subject, buildEmailBody(system, out, reportType),
                            pdfBytes, fileName);
                    log.info("[AlertEmail] Reporte PDF enviado a {} para sistema {}",
                            user.getEmail(), system);
                } catch (Exception e) {
                    log.error("[AlertEmail] Error enviando a {}: {}", user.getEmail(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[AlertEmail] Error general en sendReportEmail: {}", e.getMessage());
        }
    }

    private String buildEmailBody(String system, SummaryInsightsDto out, String reportType) {
        boolean isEscalation = "ESCALATION".equalsIgnoreCase(reportType);
        String intro = isEscalation
                ? "Se detectó un <strong>incremento crítico</strong> en el error rate de <strong>"
                + system + "</strong>. Se adjunta el reporte de escalada."
                : "Se adjunta el reporte diario de alerta para el sistema <strong>"
                + system + "</strong>.";

        return String.format("""
            <p style='font-size:14px;color:#374151;line-height:1.6;margin:0 0 16px'>%s</p>
            <p style='font-size:13px;color:#6b7280;margin:0'>
              Total eventos: <strong>%s</strong> &nbsp;·&nbsp;
              Error rate: <strong style='color:#dc2626'>%.1f%%</strong>
            </p>
            """, intro, out.total, out.errorRate * 100);
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
