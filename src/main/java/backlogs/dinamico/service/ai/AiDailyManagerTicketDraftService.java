package backlogs.dinamico.service.ai;

import backlogs.dinamico.service.ai.dto.DailySummaryDto;
import backlogs.dinamico.service.ai.dto.SummaryInsightsDto;
import backlogs.dinamico.service.ai.dto.TicketDraftDto;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiDailyManagerTicketDraftService {

    private final DailySummaryService dailySummaryService;
    private final SummaryInsightsService summaryInsightsService;

    private static final Pattern METHOD_PATH =
            Pattern.compile("^(get|post|put|patch|delete)\\s+([^\\s]+)\\s+fail\\b.*", Pattern.CASE_INSENSITIVE);

    public TicketDraftDto draft(ObjectId tenantId, int days, String tz, Instant from, Instant to) {

        DailySummaryDto summary = dailySummaryService.buildDailySummary(tenantId, days, tz, from, to);
        SummaryInsightsDto insights = summaryInsightsService.fromDaily(tenantId, summary);

        // prioridad por status del insight
        String priority = mapPriority(insights.status);

        String topSystem = firstTopName(insights.topSystemsRange);
        if (!StringUtils.hasText(topSystem)) topSystem = firstTopName(insights.topSystems);

        SummaryInsightsDto.TopError topErr = firstTopError(insights.topErrorsRange);
        if (topErr == null) topErr = firstTopError(insights.topErrors);

        String topErrSnippet = (topErr != null && StringUtils.hasText(topErr.key)) ? snippet(topErr.key, 70) : null;

        String title = buildTitle(
                insights.status,
                "Resumen diario gerente",
                topSystem,
                topErrSnippet,
                insights.fromLocal,
                insights.toLocal
        );

        Map<String, Object> suggestedFilters = new LinkedHashMap<>();
        if (StringUtils.hasText(topSystem)) suggestedFilters.put("system", topSystem);
        if (StringUtils.hasText(insights.from)) suggestedFilters.put("from", insights.from);
        if (StringUtils.hasText(insights.to)) suggestedFilters.put("to", insights.to);

        // si hay error rate alto, sugerimos filtrar errores
        if (insights.errorRate >= 0.20) {
            suggestedFilters.put("severity", List.of("ERROR", "FATAL"));
            suggestedFilters.put("outcome", "FAILURE");
        }

        // si hay top error, intentar extraer method/path para reproducir
        List<String> steps = buildReproSteps(topErrSnippet, suggestedFilters, priority);

        TicketDraftDto out = new TicketDraftDto();
        out.tz = tz;
        out.alertId = null; // porque esto viene de summary, no de un alert
        out.title = title;
        out.priority = priority;
        out.labels = buildLabels(insights, topSystem);
        out.format = "markdown";

        out.descriptionMarkdown = buildMarkdown(summary, insights, suggestedFilters, steps);

        out.stepsToReproduce = steps;
        out.expectedBehavior = "Operación normal del día (sin tasa alta de errores, sin spikes y sin fallos recurrentes).";
        out.actualBehavior = buildActual(insights);

        out.suggestedFilters = suggestedFilters;

        out.meta = safeMap(
                "granularity", insights.granularity,
                "status", insights.status,
                "days", insights.days,
                "from", insights.from,
                "to", insights.to,
                "fromLocal", insights.fromLocal,
                "toLocal", insights.toLocal,
                "total", insights.total,
                "activeBuckets", insights.activeBuckets,
                "errorRate", insights.errorRate
        );

        return out;
    }

    // ---------------- helpers ----------------

    private static String mapPriority(String status) {
        if ("CRIT".equalsIgnoreCase(status)) return "P1";
        if ("WARN".equalsIgnoreCase(status)) return "P2";
        return "P3";
    }

    private static String buildTitle(String status, String base, String sys, String err, String fromLocal, String toLocal) {
        String s = StringUtils.hasText(status) ? status : "OK";
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(s).append("] ").append(base);
        if (StringUtils.hasText(sys)) sb.append(" - ").append(sys);
        if (StringUtils.hasText(err)) sb.append(" - ").append(err);
        if (StringUtils.hasText(fromLocal) && StringUtils.hasText(toLocal)) sb.append(" (").append(fromLocal).append(" → ").append(toLocal).append(")");
        return sb.toString();
    }

    private static List<String> buildLabels(SummaryInsightsDto insights, String topSystem) {
        List<String> labels = new ArrayList<>();
        labels.add("ai");
        labels.add("logs");
        labels.add("daily");
        labels.add("manager");
        if (StringUtils.hasText(insights.status)) labels.add("status:" + insights.status);
        if (StringUtils.hasText(topSystem)) labels.add("system:" + topSystem);
        if (insights.errorRate >= 0.20) labels.add("high-error-rate");
        if (insights.alerts != null && !insights.alerts.isEmpty()) labels.add("has-alerts");
        return labels;
    }

    private static List<String> buildReproSteps(String topErr, Map<String, Object> filters, String priority) {
        List<String> steps = new ArrayList<>();

        // Intentar detectar method/path
        if (StringUtils.hasText(topErr)) {
            Matcher m = METHOD_PATH.matcher(topErr.trim());
            if (m.matches()) {
                String method = m.group(1).toUpperCase(Locale.ROOT);
                String path = m.group(2);
                steps.add("1) Ejecutar `" + method + " " + path + "` con un caso real del día.");
                steps.add("2) Confirmar que falla (HTTP 4xx/5xx, status REJECTED/ERROR u outcome FAILURE).");
                steps.add("3) Validar en Backlogs Dinámico con los filtros sugeridos que se repite.");
                return steps;
            }
        }

        // Fallback general
        steps.add("1) Revisar los dashboards daily/insights del día y confirmar el patrón (errores/topErrors/spikes).");
        steps.add("2) Abrir logs con los filtros sugeridos y revisar 10-20 eventos representativos.");
        steps.add("3) Si " + ("P1".equals(priority) ? "P1" : "P2/P3") + ": documentar evidencia y escalar/crear ticket con owner del sistema.");

        return steps;
    }

    private static String buildActual(SummaryInsightsDto insights) {
        if (insights == null) return "Se detectaron anomalías.";
        if ("CRIT".equalsIgnoreCase(insights.status)) return "Se detectaron anomalías críticas en el día (revisar alerts y topErrorsRange).";
        if ("WARN".equalsIgnoreCase(insights.status)) return "Se detectaron anomalías relevantes en el día (revisar warnings y topErrorsRange).";
        return "Sin anomalías relevantes detectadas.";
    }

    private static String buildMarkdown(DailySummaryDto summary, SummaryInsightsDto insights, Map<String, Object> filters, List<String> steps) {
        StringBuilder sb = new StringBuilder();

        sb.append("## Resumen gerencial (Daily)\n");
        sb.append("- Rango: ").append(nz(insights.fromLocal)).append(" → ").append(nz(insights.toLocal)).append("\n");
        sb.append("- Total eventos: ").append(insights.total).append("\n");
        sb.append("- Buckets activos: ").append(insights.activeBuckets).append(" / ").append(insights.buckets).append("\n");
        sb.append("- Error rate: ").append(String.format(Locale.ROOT, "%.2f%%", insights.errorRate * 100.0)).append("\n\n");

        sb.append("## Tops (rango)\n");
        sb.append("- Systems: ").append(topItems(insights.topSystemsRange)).append("\n");
        sb.append("- EventTypes: ").append(topItems(insights.topEventTypesRange)).append("\n");
        sb.append("- Status: ").append(topItems(insights.topStatusRange)).append("\n");
        sb.append("- Outcome: ").append(topItems(insights.topOutcomeRange)).append("\n");
        sb.append("- TopErrors: ").append(topErrors(insights.topErrorsRange)).append("\n\n");

        if (insights.alerts != null && !insights.alerts.isEmpty()) {
            sb.append("## Alertas detectadas\n");
            for (var a : insights.alerts) {
                if (a == null) continue;
                sb.append("- ").append(nz(a.level)).append(" | ").append(nz(a.type)).append(" | ").append(nz(a.message)).append("\n");
            }
            sb.append("\n");
        }

        if (insights.warnings != null && !insights.warnings.isEmpty()) {
            sb.append("## Warnings\n");
            for (String w : insights.warnings) sb.append("- ").append(w).append("\n");
            sb.append("\n");
        }

        if (insights.recommendations != null && !insights.recommendations.isEmpty()) {
            sb.append("## Recomendaciones\n");
            for (String r : insights.recommendations) sb.append("- ").append(r).append("\n");
            sb.append("\n");
        }

        sb.append("## Pasos de reproducción\n");
        for (String st : steps) sb.append("- ").append(st).append("\n");
        sb.append("\n");

        sb.append("## Filtros sugeridos\n```json\n");
        sb.append(toJsonLike(filters));
        sb.append("\n```\n");

        return sb.toString();
    }

    private static String topItems(List<SummaryInsightsDto.TopItem> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, items.size()); i++) {
            var it = items.get(i);
            if (it == null || !StringUtils.hasText(it.name)) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(it.name).append(" (").append(it.count).append(")");
        }
        return sb.length() == 0 ? "[]" : sb.toString();
    }

    private static String topErrors(List<SummaryInsightsDto.TopError> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, items.size()); i++) {
            var it = items.get(i);
            if (it == null || !StringUtils.hasText(it.key)) continue;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(snippet(it.key, 60)).append(" (").append(it.count).append(")");
        }
        return sb.length() == 0 ? "[]" : sb.toString();
    }

    private static String firstTopName(List<SummaryInsightsDto.TopItem> items) {
        if (items == null || items.isEmpty()) return null;
        var it = items.get(0);
        return it == null ? null : it.name;
    }

    private static SummaryInsightsDto.TopError firstTopError(List<SummaryInsightsDto.TopError> items) {
        if (items == null || items.isEmpty()) return null;
        return items.get(0);
    }

    private static String nz(String s) { return s == null ? "-" : s; }

    private static String snippet(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String toJsonLike(Map<String, Object> m) {
        if (m == null || m.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        int size = m.size();
        for (var e : m.entrySet()) {
            if (e.getKey() == null) continue;
            sb.append("  \"").append(e.getKey()).append("\": ");
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else if (v instanceof List<?> list) sb.append(list.toString());
            else sb.append("\"").append(String.valueOf(v).replace("\"", "\\\"")).append("\"");
            if (++i < size) sb.append(",");
            sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private static Map<String, Object> safeMap(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (kv == null) return out;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object k = kv[i];
            Object v = kv[i + 1];
            if (!(k instanceof String ks)) continue;
            if (v == null) continue;
            out.put(ks, v);
        }
        return out;
    }
}