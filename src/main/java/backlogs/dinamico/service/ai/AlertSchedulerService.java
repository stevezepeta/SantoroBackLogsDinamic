package backlogs.dinamico.service.ai;

import backlogs.dinamico.model.ai.AlertSentRecord;
import backlogs.dinamico.model.core.Organization;
import backlogs.dinamico.repository.ai.AlertSentRepository;
import backlogs.dinamico.service.ai.dto.SummaryInsightsDto;
import backlogs.dinamico.service.email.AlertEmailNotifier;
import backlogs.dinamico.service.email.AlertReportPdfService;
import backlogs.dinamico.service.ai.EvaDeepAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertSchedulerService {

    private final MongoTemplate         mongoTemplate;
    private final HourlySummaryService  hourlySummaryService;
    private final SummaryInsightsService summaryInsightsService;
    private final AlertEmailNotifier    alertEmailNotifier;
    private final AlertReportPdfService pdfService;
    private final EvaDeepAnalysisService deepAnalysisService;
    private final AlertSentRepository   alertSentRepo;

    private final SimpMessagingTemplate wsTemplate;

    private static final DateTimeFormatter DAY_KEY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(ZoneId.of("America/Mexico_City"));

    // ── Corre cada hora ───────────────────────────────────────────────────────
    @Scheduled(fixedDelay = 60 * 60 * 1000) // cada 60 minutos
    public void runHourlyAlertCheck() {
        log.info("[AlertScheduler] Iniciando ciclo...");

        List<Organization> orgs = mongoTemplate.find(
                new Query(Criteria.where("status").ne("disabled")),
                Organization.class, "organizations"
        );

        log.info("[AlertScheduler] Organizaciones encontradas: {}", orgs.size());

        for (Organization org : orgs) {
            ObjectId tenantId = org.getId();
            if (tenantId == null) continue;
            log.info("[AlertScheduler] Procesando org: {} ({})", org.getName(), tenantId);
            try {
                processOrganization(tenantId, org);
            } catch (Exception e) {
                log.error("[AlertScheduler] Error: {}", e.getMessage());
            }
        }

        log.info("[AlertScheduler] Ciclo completado.");
    }

    private void processOrganization(ObjectId tenantId, Organization org) {
        // 2. Descubrir sistemas activos de las últimas 2 horas
        Instant since = Instant.now().minus(2, ChronoUnit.HOURS);

        List<String> systems = mongoTemplate.aggregate(
                        Aggregation.newAggregation(
                                Aggregation.match(
                                        Criteria.where("tenant_id").is(tenantId)
                                                .and("eventTime").gte(since)),
                                Aggregation.group("system"),
                                Aggregation.project().and("_id").as("system")
                        ),
                        "log_events", Document.class
                ).getMappedResults().stream()
                .map(d -> d.getString("system"))
                .filter(s -> s != null && !s.isBlank())
                .toList();

        if (systems.isEmpty()) {
            log.debug("[AlertScheduler] Sin actividad reciente para tenant {}", tenantId);
            return;
        }

        String tz      = org.getSettings() != null && org.getSettings().getTimezone() != null
                ? "America/" + org.getSettings().getTimezone() : "America/Mexico_City";
        String dayKey  = DAY_KEY_FMT.format(Instant.now());

        for (String system : systems) {
            try {
                processSystem(tenantId, system, tz, dayKey);
            } catch (Exception e) {
                log.error("[AlertScheduler] Error procesando system {}: {}", system, e.getMessage());
            }
        }
    }

    private void processSystem(ObjectId tenantId, String system,
                               String tz, String dayKey) {
        // Calcular resumen de las últimas 24 horas para este sistema
        var hourly = hourlySummaryService.buildHourlySummary(
                tenantId, 24, tz, null, null, system);
        SummaryInsightsDto insights = summaryInsightsService.fromHourly(tenantId, hourly);

        // Solo continuar si hay WARN o CRIT
        if (!"CRIT".equalsIgnoreCase(insights.status)
                && !"WARN".equalsIgnoreCase(insights.status)) return;

        // Verificar si ya se envió reporte hoy para este sistema
        var existing = alertSentRepo.findByTenantIdAndSystemAndDayKey(
                tenantId, system, dayKey);

        boolean shouldSend = false;
        String  reportType = "DAILY";

        if (existing.isEmpty()) {
            // Primera alerta del día → enviar siempre
            shouldSend = true;
        } else {
            // Ya se envió hoy → verificar escalada
            // Solo escalar si el errorRate subió más de 15 puntos porcentuales
            double prevRate = existing.get().getErrorRate();
            double currRate = insights.errorRate;
            double delta    = currRate - prevRate;

            if (delta >= 0.15) {
                shouldSend = true;
                reportType = "ESCALATION";
                log.info("[AlertScheduler] Escalada detectada para {} — prevRate={}, currRate={}",
                        system, prevRate, currRate);
            }
        }

        if (!shouldSend) {
            log.debug("[AlertScheduler] Sin cambio significativo para {} — omitiendo envío", system);
            return;
        }

        // Generar análisis IA
        Instant from = Instant.now().minus(24, ChronoUnit.HOURS);
        Instant to   = Instant.now();

        backlogs.dinamico.service.ai.dto.DailyManagerSummaryDto miniSummary =
                new backlogs.dinamico.service.ai.dto.DailyManagerSummaryDto();
        miniSummary.total     = insights.total;
        miniSummary.errorRate = insights.errorRate;
        miniSummary.status    = insights.status;
        miniSummary.topOutcomeRange    = insights.topOutcomeRange != null
                ? insights.topOutcomeRange.stream()
                .map(i -> { var t = new backlogs.dinamico.service.ai.dto.SummaryInsightsDto.TopItem(i.name, i.count);
                    return new backlogs.dinamico.service.ai.dto.SummaryInsightsDto.TopItem(i.name, i.count); })
                .collect(java.util.stream.Collectors.toList())
                : null;

        EvaDeepAnalysisService.DeepAnalysisResult aiAnalysis =
                deepAnalysisService.analyzeFromInsights(tenantId, system, from, to, insights);

        // Generar PDF
        byte[] pdf = pdfService.generateReport(tenantId, system, insights, aiAnalysis);
        if (pdf == null) {
            log.error("[AlertScheduler] PDF nulo para {} — omitiendo envío y registro", system);
            return;
        }

        // Enviar correo con PDF
        alertEmailNotifier.sendReportEmail(tenantId, system, insights, pdf, reportType);

        final String finalReportType = reportType;
        final double finalErrorRate = insights.errorRate;
        final long finalTotal = insights.total;

        // Guardar registro de envío
        AlertSentRecord sentRecord = alertSentRepo
                .findByTenantIdAndSystemAndDayKey(tenantId, system, dayKey)
                .map(rec -> {
                    rec.setSentAt(Instant.now());
                    rec.setErrorRate(finalErrorRate);
                    rec.setType(finalReportType);
                    return rec;
                })
                .orElseGet(() -> AlertSentRecord.builder()
                        .tenantId(tenantId)
                        .system(system)
                        .dayKey(dayKey)
                        .sentAt(Instant.now())
                        .errorRate(finalErrorRate)
                        .total(finalTotal)
                        .type(finalReportType)
                        .build()
                );

        alertSentRepo.save(sentRecord);

        // Notificación WebSocket si es CRIT
        if ("CRIT".equalsIgnoreCase(insights.status)) {
            String topic = "/topic/alerts/" + tenantId.toHexString();
            Map<String, Object> alertMsg = new java.util.HashMap<>();
            alertMsg.put("type",      "ALERT_CRIT");
            alertMsg.put("system",    system);
            alertMsg.put("status",    insights.status);
            alertMsg.put("errorRate", insights.errorRate);
            alertMsg.put("total",     insights.total);
            alertMsg.put("message",   "Sistema " + system + " en estado CRÍTICO — Error rate: "
                    + String.format(java.util.Locale.ROOT, "%.1f%%", insights.errorRate * 100));
            alertMsg.put("timestamp", java.time.Instant.now().toString());
            try {
                wsTemplate.convertAndSend(topic, alertMsg);
                log.info("[AlertScheduler] WS CRIT enviado a {}", topic);
            } catch (Exception e) {
                log.warn("[AlertScheduler] Error enviando WS alert: {}", e.getMessage());
            }
        }

        log.info("[AlertScheduler] Reporte {} enviado para sistema {} (tenant {})",
                reportType, system, tenantId);
    }
}