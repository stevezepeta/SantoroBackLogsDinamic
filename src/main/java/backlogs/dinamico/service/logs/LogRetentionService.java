package backlogs.dinamico.service.logs;

import backlogs.dinamico.model.core.Organization;
import backlogs.dinamico.repository.core.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Elimina logs individuales (log_events) más antiguos que retentionDays.
 * Las métricas agregadas (ai_metric_records) y alertas (ai_alerts) NO se tocan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogRetentionService {

    private static final int DEFAULT_RETENTION_DAYS = 90;

    private final OrganizationRepository orgRepo;
    private final MongoTemplate          mongoTemplate;

    /**
     * Ejecuta la retención para todas las organizaciones activas.
     * Retorna el total de logs eliminados en este ciclo.
     */
    public long runRetention() {
        List<Organization> orgs = orgRepo.findByStatusNot("disabled");
        if (orgs == null || orgs.isEmpty()) {
            log.info("[Retention] Sin organizaciones activas.");
            return 0L;
        }

        long totalDeleted = 0L;

        for (Organization org : orgs) {
            try {
                long deleted = retainOrg(org);
                totalDeleted += deleted;
            } catch (Exception e) {
                log.error("[Retention] Error procesando org {}: {}",
                        org.getId(), e.getMessage());
            }
        }

        return totalDeleted;
    }

    /**
     * Ejecuta la retención para una organización específica.
     * Útil para pruebas o ejecución manual.
     */
    public long retainOrg(ObjectId tenantId) {
        Organization org = orgRepo.findById(tenantId).orElse(null);
        if (org == null) {
            log.warn("[Retention] Org no encontrada: {}", tenantId);
            return 0L;
        }
        return retainOrg(org);
    }

    // ── Lógica por organización ───────────────────────────────────────────────

    private long retainOrg(Organization org) {
        int retentionDays = resolveRetentionDays(org);
        Instant cutoff    = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        log.info("[Retention] Org: {} ({}) — retención: {} días — cutoff: {}",
                org.getName(), org.getId(), retentionDays, cutoff);

        // Solo eliminar log_events — las métricas y alertas se conservan
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("tenant_id").is(org.getId()),
                Criteria.where("eventTime").lt(cutoff)
        ));

        // Contar antes de eliminar para el log
        long count = mongoTemplate.count(query, "log_events");

        if (count == 0) {
            log.info("[Retention] Org {} — sin logs para eliminar.", org.getName());
            return 0L;
        }

        // Eliminar en lotes de 1000 para no bloquear MongoDB
        long deleted = 0L;
        int  batchSize = 1000;

        while (deleted < count) {
            Query batchQuery = new Query(new Criteria().andOperator(
                    Criteria.where("tenant_id").is(org.getId()),
                    Criteria.where("eventTime").lt(cutoff)
            )).limit(batchSize);

            long batchDeleted = mongoTemplate
                    .remove(batchQuery, "log_events")
                    .getDeletedCount();

            deleted += batchDeleted;

            if (batchDeleted == 0) break; // por seguridad

            log.debug("[Retention] Org {} — lote eliminado: {} (total: {}/{})",
                    org.getName(), batchDeleted, deleted, count);

            // Pequeña pausa entre lotes para no saturar MongoDB
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }

        log.info("[Retention] Org {} — {} logs eliminados (>{} días).",
                org.getName(), deleted, retentionDays);

        return deleted;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int resolveRetentionDays(Organization org) {
        try {
            if (org.getSettings() != null
                    && org.getSettings().getRetentionDays() != null
                    && org.getSettings().getRetentionDays() > 0) {
                return org.getSettings().getRetentionDays();
            }
        } catch (Exception ignored) {}
        return DEFAULT_RETENTION_DAYS;
    }
}