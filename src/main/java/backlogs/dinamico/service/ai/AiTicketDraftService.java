package backlogs.dinamico.service.ai;

import backlogs.dinamico.service.ai.dto.AlertOperatorExplainDto;
import backlogs.dinamico.service.ai.dto.TicketDraftDto;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiTicketDraftService {

    private final AiAlertOperatorExplainService explainService;

    private static final Pattern METHOD_PATH =
            Pattern.compile("^(get|post|put|patch|delete)\\\\s+([^\\\\s]+)\\\\s+fail\\\\b.*", Pattern.CASE_INSENSITIVE);

    public TicketDraftDto draftFromAlert(ObjectId tenantId, ObjectId alertId, String tz, int samples) {

        AlertOperatorExplainDto ex = explainService.explain(tenantId, alertId, tz, samples);

        String topSystem = firstTopName(ex.topSystems);
        String topEventType = firstTopName(ex.topEventTypes);

        String friendlyType = friendlyAlertType(ex.primaryType);
        String prio = mapPriority(ex.status);

        // Top error
        String topErr = (ex.topErrors != null && !ex.topErrors.isEmpty() && StringUtils.hasText(ex.topErrors.get(0).key))
                ? snippet(ex.topErrors.get(0).key, 60)
                : null;

        String title = buildTitle(ex.status, friendlyType, topSystem, topEventType, topErr);

        // Filters
        Map<String, Object> suggestedFilters = new LinkedHashMap<>();
        if (ex.steps != null && !ex.steps.isEmpty() && ex.steps.get(0) != null && ex.steps.get(0).suggestedFilters != null) {
            suggestedFilters.putAll(ex.steps.get(0).suggestedFilters);
        } else {
            // fallback minimo
            if (StringUtils.hasText(topSystem)) suggestedFilters.put("system", topSystem);
            if (StringUtils.hasText(topEventType)) suggestedFilters.put("eventType", topEventType);
            if (ex.windowFrom != null) suggestedFilters.put("from", ex.windowFrom);
            if (ex.windowTo != null) suggestedFilters.put("to", ex.windowTo);
        }

        // Repro steps
        List<String> steps = buildReproSteps(ex, suggestedFilters);

        // Expected/Actual
        String expected = expectedBehavior(ex.primaryType);
        String actual = actualBehavior(ex.primaryType);

        // Markdown
        String md = buildMarkdown(ex, steps, suggestedFilters);

        TicketDraftDto out = new TicketDraftDto();
        out.tz = ex.tz;
        out.alertId = ex.alertId;
        out.title = title;
        out.priority = prio;
        out.labels = buildLabels(ex, topSystem, topEventType);
        out.format = "markdown";
        out.descriptionMarkdown = md;
        out.stepsToReproduce = steps;
        out.expectedBehavior = expected;
        out.actualBehavior = actual;
        out.suggestedFilters = suggestedFilters;

        out.meta = safeMap(
                "granularity", ex.granularity,
                "status", ex.status,
                "state", ex.state,
                "windowFrom", ex.windowFrom,
                "windowTo", ex.windowTo,
                "windowFromLocal", ex.windowFromLocal,
                "windowToLocal", ex.windowToLocal,
                "bucketFrom", ex.bucketFrom,
                "bucketTo", ex.bucketTo,
                "bucketFromLocal", ex.bucketFromLocal,
                "bucketFromToLocal", ex.bucketToLocal,
                "primaryType", ex.primaryType,
                "primaryLevel", ex.primaryLevel
        );
        return out;
    }

    // -------------------- HELPERS -------------------------
    private static String buildTitle(String status, String type, String sys, String evt, String err) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(status == null ? "OK" : status).append("] ").append(type);

        if (StringUtils.hasText(sys)) sb.append(" - ").append(sys);
        if (StringUtils.hasText(evt)) sb.append(" - ").append(evt);
        if (StringUtils.hasText(err)) sb.append(" - ").append(err);

        return sb.toString();
    }

    private static String friendlyAlertType(String t) {
        if (t == null) return "Alerta";
        return switch (t) {
            case "HIGH_ERROR_RATE" -> "Tasa alta de errores";
            case "VOLUME_SPIKE" -> "Pico de volumen";
            case "NO_DATA" -> "Sin datos (No Data)";
            case "SYSTEM_DOMINANCE" -> "Dominancia de sistema";
            default -> t;
        };
    }

    private static String mapPriority(String status) {
        if ("CRIT".equalsIgnoreCase(status)) return "P1";
        if ("WARN".equalsIgnoreCase(status)) return "P2";
        return "P3";
    }

    private static List<String> buildLabels(AlertOperatorExplainDto ex, String sys, String evt) {
        List<String> labels = new ArrayList<>();
        labels.add("ai");
        labels.add("logs");
        if (StringUtils.hasText(ex.granularity)) labels.add(ex.granularity);
        if (StringUtils.hasText(ex.primaryType)) labels.add(ex.primaryType.toLowerCase(Locale.ROOT));
        if (StringUtils.hasText(sys)) labels.add("system:" + sys);
        if (StringUtils.hasText(evt)) labels.add("event:" + evt);
        if (StringUtils.hasText(ex.status)) labels.add("status:" + ex.status);
        return labels;
    }

    private static List<String> buildReproSteps(AlertOperatorExplainDto ex, Map<String, Object> suggestedFilters) {
        List<String> steps = new ArrayList<>();

        // Si tenemos un topError con forma "post /api/... fail: ..."
        String key = (ex.topErrors != null && !ex.topErrors.isEmpty()) ? ex.topErrors.get(0).key : null;
        Matcher m = (key == null) ? null : METHOD_PATH.matcher(key.trim());

        if (m != null && m.matches()) {
            String method = m.group(1).toUpperCase(Locale.ROOT);
            String path = m.group(2);

            steps.add("1) Ejecutar `" + method + " " + path + "` en el ambiente PROD/stage con un caso representativo.");
            steps.add("2) Confirmar que la respuesta es fallo (HTTP 4xx/5xx o outcome FAILURE / status REJECTED).");
            steps.add("3) En Backlogs Dinámico, filtrar logs con los filtros sugeridos y confirmar repetición del error.");
        } else {
            // fallback general
            steps.add("1) Abrir el flujo asociado al system/eventType principal y ejecutarlo normalmente.");
            steps.add("2) Observar que el flujo presenta fallos (REJECTED/FAILURE o aumento anormal de errores).");
            steps.add("3) Validar en logs (con filtros sugeridos) que el patrón coincide con el error reportado.");
        }

        // Si hay requestId/caseId dominante en filtros, sugerirlo explícitamente
        Object reqId = suggestedFilters.get("requestId");
        Object caseId = suggestedFilters.get("caseId");
        if (reqId != null) steps.add("4) Correlacionar por requestId: `" + reqId + "` (traza completa).");
        if (caseId != null) steps.add("5) Correlacionar por caseId: `" + caseId + "` (flujo específico).");

        return steps;
    }

    private static String expectedBehavior(String primaryType) {
        if (primaryType == null) return "El sistema debe operar sin degradación.";
        return switch (primaryType) {
            case "HIGH_ERROR_RATE" -> "Los requests/operaciones deben completarse exitosamente con baja tasa de errores.";
            case "VOLUME_SPIKE" -> "El volumen debe mantenerse estable (sin picos anómalos) o ser explicado por batch esperado.";
            case "NO_DATA" -> "Debe existir flujo continuo de logs en el rango esperado.";
            default -> "Comportamiento esperado normal.";
        };
    }

    private static String actualBehavior(String primaryType) {
        if (primaryType == null) return "Se detectó una anomalía.";
        return switch (primaryType) {
            case "HIGH_ERROR_RATE" -> "La tasa de fallos aumentó de forma relevante en el rango analizado.";
            case "VOLUME_SPIKE" -> "Se detectó un pico anómalo de volumen en el último bucket activo.";
            case "NO_DATA" -> "No se detectaron eventos en el rango seleccionado.";
            default -> "Se detectó condición anómala.";
        };
    }

    private static String buildMarkdown(AlertOperatorExplainDto ex, List<String> steps, Map<String, Object> suggestedFilters) {
        StringBuilder sb = new StringBuilder();

        sb.append("## Resumen\n");
        if (ex.operatorMeaning != null) sb.append("- ").append(ex.operatorMeaning).append("\n");
        if (ex.impact != null) sb.append("- Impacto: ").append(ex.impact).append("\n");
        sb.append("- Tipo: `").append(nz(ex.primaryType)).append("`  Nivel: `").append(nz(ex.primaryLevel)).append("`\n");
        sb.append("- Status: `").append(nz(ex.status)).append("`  Granularity: `").append(nz(ex.granularity)).append("`\n\n");

        sb.append("## Ventana\n");
        sb.append("- Window: ").append(nz(ex.windowFromLocal)).append(" → ").append(nz(ex.windowToLocal)).append("\n");
        sb.append("- Bucket: ").append(nz(ex.bucketFromLocal)).append(" → ").append(nz(ex.bucketToLocal)).append("\n\n");

        sb.append("## Evidencia (tops)\n");
        sb.append("- TopSystems: ").append(topList(ex.topSystems)).append("\n");
        sb.append("- TopEventTypes: ").append(topList(ex.topEventTypes)).append("\n");
        sb.append("- TopErrors: ").append(errList(ex.topErrors)).append("\n\n");

        sb.append("## Pasos de reproducción\n");
        for (String st : steps) sb.append("- ").append(st).append("\n");
        sb.append("\n");

        sb.append("## Filtros sugeridos (para buscar en logs)\n");
        sb.append("```json\n");
        sb.append(toJsonLike(suggestedFilters));
        sb.append("\n```\n\n");

        if (ex.samples != null && !ex.samples.isEmpty()) {
            sb.append("## Muestras (primeros ").append(Math.min(ex.samples.size(), 5)).append(")\n");
            int n = 0;
            for (var s : ex.samples) {
                if (s == null) continue;
                if (n++ >= 5) break;
                sb.append("- ").append(nz(s.eventTimeLocal)).append(" | ")
                        .append(nz(s.system)).append(" | ")
                        .append(nz(s.eventType)).append(" | ")
                        .append(nz(s.status)).append(" | ")
                        .append(nz(s.outcome)).append(" | ")
                        .append(snippet(nz(s.message), 140))
                        .append("\n");
            }
        }

        return sb.toString();
    }

    private static String topList(List<AlertOperatorExplainDto.TopItem> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, items.size()); i++) {
            var it = items.get(i);
            if (it == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(it.name).append(" (").append(it.count).append(")");
        }
        return sb.toString();
    }

    private static String errList(List<AlertOperatorExplainDto.TopError> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, items.size()); i++) {
            var it = items.get(i);
            if (it == null) continue;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(snippet(nz(it.key), 60)).append(" (").append(it.count).append(")");
        }
        return sb.toString();
    }

    private static String firstTopName(List<AlertOperatorExplainDto.TopItem> items) {
        if (items == null || items.isEmpty()) return null;
        var it = items.get(0);
        return (it == null) ? null : it.name;
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
        for (var e : m.entrySet()) {
            if (e.getKey() == null) continue;
            sb.append("  \"").append(e.getKey()).append("\": ");
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else if (v instanceof List<?> list) sb.append(list.toString());
            else sb.append("\"").append(String.valueOf(v).replace("\"", "\\\"")).append("\"");
            if (++i < m.size()) sb.append(",");
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
