package backlogs.dinamico.service.ai;

import backlogs.dinamico.api.dto.EvaStreamRequest;
import backlogs.dinamico.model.ai.AiAlertRecord;
import backlogs.dinamico.repository.ai.AiAlertRepository;
import backlogs.dinamico.service.ai.dto.DailyManagerSummaryDto;
import backlogs.dinamico.service.ai.dto.SummaryInsightsDto;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class EvaStreamService {

    private static final DateTimeFormatter FMT_LOCAL =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AiDailyManagerService dailyManagerService;
    private final AiAlertRepository     alertRepo;
    private final MongoTemplate         mongoTemplate;

    private final EvaDeepAnalysisService deepAnalysisService;

    /**
     * @param req     Parámetros de la petición (incluye tenantId, allowedSystems, etc.)
     * @param onChunk Callback — se llama por cada fragmento de texto listo
     */
    public void streamResponse(EvaStreamRequest req, Consumer<String> onChunk) throws Exception {
        String intent = detectIntent(req.getMessage());
        switch (intent) {
            case "daily-summary" -> streamDailySummary(req, onChunk);
            case "metrics-chart" -> streamMetricsInfo(req, onChunk);
            case "open-alerts"   -> streamOpenAlerts(req, onChunk);
            case "trends"        -> streamTrends(req, onChunk);
            default              -> streamDefault(req.getMessage(), onChunk);
        }
    }

    // ── daily-summary ─────────────────────────────────────────────────────────

    private void streamDailySummary(EvaStreamRequest req, Consumer<String> onChunk) throws Exception {
        ObjectId tenantId    = req.getTenantId();
        String   systemFilter = resolveSystem(req);

        DailyManagerSummaryDto summary = dailyManagerService.buildManagerSummary(
                tenantId, req.getDays(), req.getTz(),
                null, null,
                systemFilter, req.getAllowedSystems()
        );

        Instant from = Instant.now().minusSeconds((long) req.getDays() * 24 * 60 * 60);
        Instant to = Instant.now();
        summary.aiDeepAnalysis = deepAnalysisService.analyze(tenantId, systemFilter, from, to, summary);

        emitInChunks(buildSummaryNarrative(summary, systemFilter), onChunk, 40);
    }

    // ── metrics-chart ─────────────────────────────────────────────────────────

    private void streamMetricsInfo(EvaStreamRequest req, Consumer<String> onChunk) throws Exception {
        String system = resolveSystem(req);
        if (system == null) system = "el sistema seleccionado";

        String text = String.format(
                "Consultando la serie histórica de **%s** con granularidad %s " +
                        "en los últimos %d días...\n\nPrepara la gráfica en el panel derecho.",
                system, req.getGranularity(), req.getDays()
        );
        emitInChunks(text, onChunk, 35);
    }

    // ── open-alerts ───────────────────────────────────────────────────────────

    private void streamOpenAlerts(EvaStreamRequest req, Consumer<String> onChunk) throws Exception {
        ObjectId tenantId = req.getTenantId();
        ZoneId   zone     = safeZone(req.getTz());

        // Alertas abiertas de las últimas 24 horas
        Instant since = Instant.now().minusSeconds(24L * 60 * 60);

        Criteria base = Criteria.where("tenantId").is(tenantId)
                .and("createdAt").gte(since);

        // Solo OPEN — state null, ausente o "OPEN"
        Criteria openFilter = new Criteria().orOperator(
                Criteria.where("state").exists(false),
                Criteria.where("state").is(null),
                Criteria.where("state").is("OPEN")
        );

        Query q = new Query(new Criteria().andOperator(base, openFilter))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(10);

        List<AiAlertRecord> alerts = mongoTemplate.find(q, AiAlertRecord.class, "ai_alerts");

        StringBuilder sb = new StringBuilder();

        if (alerts.isEmpty()) {
            sb.append("**Sin alertas abiertas** en las últimas 24 horas.\n\n");
            sb.append("Todos los sistemas operan dentro de parámetros normales. ✅");
        } else {
            sb.append("**").append(alerts.size()).append(" alerta(s) abierta(s)**")
                    .append(" en las últimas 24 horas:\n\n");

            for (AiAlertRecord alert : alerts) {
                String status = nvl(alert.getStatus(), "?");
                String time   = alert.getCreatedAt() != null
                        ? ZonedDateTime.ofInstant(alert.getCreatedAt(), zone).format(FMT_LOCAL)
                        : "—";

                sb.append(statusIcon(status))
                        .append(" **").append(status).append("**")
                        .append(" · ").append(time)
                        .append(" · errorRate: ")
                        .append(String.format("%.1f%%", alert.getErrorRate() * 100));

                // Primera señal que disparó la alerta
                if (alert.getAlerts() != null && !alert.getAlerts().isEmpty()) {
                    SummaryInsightsDto.Alert first = alert.getAlerts().get(0);
                    if (first != null && StringUtils.hasText(first.message)) {
                        sb.append("\n  → ").append(snippet(first.message, 100));
                    }
                }
                sb.append("\n");
            }

            sb.append("\nPuedes hacer **ACK** o **resolver** cada alerta desde el panel de alertas.");
        }

        emitInChunks(sb.toString(), onChunk, 45);
    }

    // ── trends ────────────────────────────────────────────────────────────────

    private void streamTrends(EvaStreamRequest req, Consumer<String> onChunk) throws Exception {
        ObjectId tenantId    = req.getTenantId();
        String   systemFilter = resolveSystem(req);

        // Hoy (1 día) vs semana (7 días)
        DailyManagerSummaryDto hoy    = dailyManagerService.buildManagerSummary(
                tenantId, 1, req.getTz(), null, null, systemFilter, req.getAllowedSystems());
        DailyManagerSummaryDto semana = dailyManagerService.buildManagerSummary(
                tenantId, 7, req.getTz(), null, null, systemFilter, req.getAllowedSystems());

        StringBuilder sb = new StringBuilder();

        String scope = systemFilter != null ? "**" + systemFilter + "**" : "todos los sistemas";
        sb.append("**Tendencias de las últimas 24h** en ").append(scope).append("\n\n");

        // Eventos hoy vs promedio diario de la semana
        long   eventosHoy    = hoy.total;
        double promedioSemana = semana.total > 0 ? (double) semana.total / 7 : 0;
        String tendEventos    = eventosHoy > promedioSemana * 1.2 ? "↑ por encima del promedio"
                : eventosHoy < promedioSemana * 0.8 ? "↓ por debajo del promedio"
                : "→ dentro del rango normal";

        sb.append("- **Eventos hoy:** ").append(eventosHoy)
                .append(" ").append(tendEventos)
                .append(" (promedio 7d: ").append(String.format("%.0f", promedioSemana)).append(")\n");

        // Error rate hoy vs semana
        double errHoy    = hoy.errorRate;
        double errSemana = semana.errorRate;
        String errTend   = errHoy > errSemana * 1.1 ? "↑ aumentando"
                : errHoy < errSemana * 0.9 ? "↓ mejorando"
                : "→ estable";

        sb.append("- **Error rate hoy:** ")
                .append(String.format("%.2f%%", errHoy * 100))
                .append(" ").append(errTend)
                .append(" (semana: ").append(String.format("%.2f%%", errSemana * 100)).append(")\n");

        // Top sistemas activos hoy
        if (hoy.topSystemsRange != null && !hoy.topSystemsRange.isEmpty()) {
            sb.append("\n**Actividad por sistema (24h):**\n");
            hoy.topSystemsRange.stream().limit(4).forEach(s -> {
                if (s != null && StringUtils.hasText(s.name)) {
                    sb.append("- ").append(s.name)
                            .append(": **").append(s.count).append(" eventos**\n");
                }
            });
        }

        // Severidades críticas
        if (hoy.severities != null) {
            long critCount = hoy.severities.getOrDefault("CRITICAL", 0L)
                    + hoy.severities.getOrDefault("ERROR", 0L);
            if (critCount > 0) {
                sb.append("\n⚠ **").append(critCount)
                        .append(" eventos CRITICAL/ERROR** requieren atención.\n");
            }
        }

        // Estado general
        String statusLine = switch (nvl(hoy.status, "OK").toUpperCase()) {
            case "CRIT" -> "\n🔴 **Estado: CRÍTICO** — revisión inmediata.";
            case "WARN" -> "\n🟡 **Estado: ADVERTENCIA** — monitorear de cerca.";
            default     -> "\n🟢 **Estado: NORMAL** — dentro de parámetros.";
        };
        sb.append(statusLine);

        emitInChunks(sb.toString(), onChunk, 45);
    }

    // ── fallback ──────────────────────────────────────────────────────────────

    private void streamDefault(String message, Consumer<String> onChunk) throws Exception {
        String text = "Esa consulta está fuera de mi área de trabajo. Soy Eva, el asistente " +
                "de análisis operacional de DataLogs, y estoy especializada únicamente en " +
                "los datos de tus sistemas.\n\n" +
                "Lo que sí puedo hacer por ti:\n\n" +
                "• Resumen diario — análisis ejecutivo del periodo con métricas clave\n" +
                "• Alertas abiertas — alertas activas de las últimas 24 horas\n" +
                "• Tendencias — comparativa de hoy vs los últimos 7 días\n" +
                "• Gráfica [sistema] — serie histórica de error rate y volumen\n\n" +
                "¿En qué puedo ayudarte con tus sistemas?";
        emitInChunks(text, onChunk, 45);
    }

    // ── intent detection ──────────────────────────────────────────────────────

    private String detectIntent(String message) {
        if (message == null) return "unknown";
        String m = message.toLowerCase();
        if (m.contains("resumen") || m.contains("daily") || m.contains("summary")) return "daily-summary";
        if (m.contains("gráfica") || m.contains("grafica") || m.contains("chart"))  return "metrics-chart";
        if (m.contains("alerta"))                                                    return "open-alerts";
        if (m.contains("tendencia") || m.contains("trend"))                          return "trends";
        return "unknown";
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Resuelve el sistema respetando el scope del usuario */
    private String resolveSystem(EvaStreamRequest req) {
        if (StringUtils.hasText(req.getSystem())) return req.getSystem();
        if (!req.isOrgWide() && req.getAllowedSystems() != null
                && !req.getAllowedSystems().isEmpty()) {
            return req.getAllowedSystems().get(0);
        }
        return null;
    }

    /**
     * Emite el texto palabra por palabra para efecto de escritura natural.
     * Agrupa palabras de ~6 por chunk para velocidad razonable.
     * Envía cada línea por separado para preservar saltos de línea.
     */
    private void emitInChunks(String text, Consumer<String> onChunk, int chunkSize)
            throws InterruptedException {
        if (text != null && !text.isEmpty()) {
            onChunk.accept(text);
        }
    }

    private String buildSummaryNarrative(DailyManagerSummaryDto s, String systemFilter) {
        StringBuilder sb = new StringBuilder();

        if (systemFilter != null) {
            sb.append("**Resumen ejecutivo de ").append(systemFilter).append("** — ");
        } else {
            sb.append("**Resumen ejecutivo** — ");
        }
        sb.append("periodo ").append(s.fromLocal).append(" → ").append(s.toLocal).append("\n\n");
        sb.append("Se procesaron **").append(s.total).append(" eventos** ");
        sb.append("con una tasa de error de **")
                .append(String.format("%.2f%%", s.errorRate * 100)).append("**.\n\n");

        if (s.executiveSummary != null) {
            for (String bullet : s.executiveSummary) sb.append("- ").append(bullet).append("\n");
        }
        if (s.risks != null && !s.risks.isEmpty()) {
            sb.append("\n**Riesgos detectados:**\n");
            for (String risk : s.risks) sb.append("- ").append(risk).append("\n");
        }
        if (s.actions != null && !s.actions.isEmpty()) {
            sb.append("\n**Acciones recomendadas:**\n");
            for (String action : s.actions) sb.append("- ").append(action).append("\n");
        }

        // ── NUEVO: análisis profundo generado por IA ──────────────────────────
        if (s.aiDeepAnalysis != null) {
            sb.append("\n---\n");
            sb.append("**🤖 Análisis generado por IA — Evento dominante: `")
                    .append(s.aiDeepAnalysis.dominantEventType()).append("`**")
                    .append(" (").append(s.aiDeepAnalysis.dominantCount()).append(" ocurrencias)\n\n");

            if (s.aiDeepAnalysis.aiSummary() != null) {
                sb.append("**¿Qué está pasando?**\n")
                        .append(s.aiDeepAnalysis.aiSummary()).append("\n\n");
            }

            if (s.aiDeepAnalysis.aiSuggestions() != null) {
                sb.append("**Acciones específicas sugeridas:**\n");
                // Convertir las sugerencias separadas por \n en bullets
                String[] suggestions = s.aiDeepAnalysis.aiSuggestions().split("\n");
                for (String sug : suggestions) {
                    if (!sug.isBlank()) {
                        String line = sug.trim().startsWith("-") ? sug.trim() : "- " + sug.trim();
                        sb.append(line).append("\n");
                    }
                }
            }
        }

        return sb.toString();
    }

    private static String statusIcon(String status) {
        return switch (status.toUpperCase()) {
            case "CRIT" -> "🔴";
            case "WARN" -> "🟡";
            case "OK"   -> "🟢";
            default     -> "⚪";
        };
    }

    private static String nvl(String s, String def) {
        return StringUtils.hasText(s) ? s : def;
    }

    private static String snippet(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static ZoneId safeZone(String tz) {
        try {
            return StringUtils.hasText(tz) ? ZoneId.of(tz.trim()) : ZoneId.of("America/Mexico_City");
        } catch (Exception e) {
            return ZoneId.of("America/Mexico_City");
        }
    }
}