package backlogs.dinamico.service.ai;

import backlogs.dinamico.service.ai.dto.DailyManagerBriefDto;
import backlogs.dinamico.service.ai.dto.DailySummaryDto;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DailyManagerBriefService {

    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    // Puedes alinear estos thresholds con SummaryInsightsService si quieres
    private static final double ERR_WARN = 0.20; // 20%
    private static final double ERR_CRIT = 0.50; // 50%
    private static final double DOMINANCE_WARN = 0.80;

    private final DailySummaryService dailySummaryService;

    /**
     * Overload sin system — mantiene compatibilidad hacia atrás (resumen global del tenant).
     */
    public DailyManagerBriefDto build(ObjectId tenantId, String tz, Instant from, Instant to) {
        return build(tenantId, tz, from, to, null);
    }

    /**
     * Construye el brief ejecutivo diario filtrando opcionalmente por sistema.
     * Si {@code system} es null o vacío, resume todos los sistemas del tenant.
     */
    public DailyManagerBriefDto build(ObjectId tenantId, String tz, Instant from, Instant to, String system) {

        ZoneId zone = safeZone(tz);

        // Queremos "días completos" (como tu dailySummary):
        // Por default: hasta el inicio del día de HOY (local) para resumir AYER completo.
        Instant now = Instant.now();
        Instant end = ((to != null) ? to : now).atZone(zone).truncatedTo(ChronoUnit.DAYS).toInstant();

        // Tomamos 2 días para comparar contra el día anterior
        Instant start = (from != null)
                ? from
                : end.minus(2, ChronoUnit.DAYS);

        // ── CORRECCIÓN: pasar el filtro de sistema a DailySummaryService ──────
        // Sin esto, buildDailySummary consulta TODOS los sistemas del tenant
        // y los campos executiveSummary / keyPoints mezclan contextos entre sistemas.
        String effectiveSystem = StringUtils.hasText(system) ? system.trim().toUpperCase(Locale.ROOT) : null;

        DailySummaryDto s = dailySummaryService.buildDailySummary(tenantId, 2, zone.getId(), start, end, effectiveSystem);

        List<DailySummaryDto.Bucket> buckets = (s.buckets == null) ? List.of() : s.buckets;
        DailySummaryDto.Bucket cur = buckets.isEmpty() ? null : buckets.get(buckets.size() - 1);
        DailySummaryDto.Bucket prev = (buckets.size() >= 2) ? buckets.get(buckets.size() - 2) : null;

        DailyManagerBriefDto out = new DailyManagerBriefDto();
        out.tz = zone.getId();
        out.system = effectiveSystem;  // ← informar al cliente qué sistema se usó

        // Ventana real (lo que usó el daily summary)
        out.windowFrom = s.from;
        out.windowTo = s.to;
        out.windowFromLocal = toLocal(Instant.parse(s.from), zone);
        out.windowToLocal = toLocal(Instant.parse(s.to), zone);

        if (cur == null) {
            // Sin buckets (raro, pero por seguridad)
            out.total = 0;
            out.errorCount = 0;
            out.errorRate = 0;
            out.severities = Map.of();
            out.topSystems = List.of();
            out.topEventTypes = List.of();
            out.topStatus = List.of();
            out.topOutcome = List.of();
            out.topErrors = List.of();
            out.keyPoints = List.of("No hay información disponible para el rango seleccionado.");
            out.actions = List.of("Verificar si el sistema está enviando logs y si el rango (tz/from/to) es correcto.");
            out.executiveSummary = "Sin eventos en el día seleccionado.";
            return out;
        }

        // Día objetivo
        out.dayStart = cur.dayStart;
        out.dayStartLocal = cur.dayStartLocal;

        long total = cur.total;
        Map<String, Long> sev = (cur.severities == null) ? Map.of() : cur.severities;

        long err = sev.getOrDefault("ERROR", 0L) + sev.getOrDefault("FATAL", 0L);
        double errRate = (total <= 0) ? 0.0 : ((double) err / (double) total);

        out.total = total;
        out.severities = sev;
        out.errorCount = err;
        out.errorRate = errRate;

        out.topSystems = mapTopItems(cur.topSystems);
        out.topEventTypes = mapTopItems(cur.topEventTypes);
        out.topStatus = mapTopItems(cur.topStatus);
        out.topOutcome = mapTopItems(cur.topOutcome);
        out.topErrors = mapTopErrors(cur.topErrors);

        // Trend vs día anterior
        out.trend = buildTrend(prev);

        // Texto gerente
        out.executiveSummary = buildExecutiveSummary(out);
        out.keyPoints = buildKeyPoints(out);
        out.actions = buildActions(out);

        return out;
    }

    private DailyManagerBriefDto.Trend buildTrend(DailySummaryDto.Bucket prev) {
        if (prev == null) return null;

        long prevTotal = prev.total;
        Map<String, Long> prevSev = (prev.severities == null) ? Map.of() : prev.severities;
        long prevErr = prevSev.getOrDefault("ERROR", 0L) + prevSev.getOrDefault("FATAL", 0L);
        double prevErrRate = (prevTotal <= 0) ? 0.0 : ((double) prevErr / (double) prevTotal);

        DailyManagerBriefDto.Trend t = new DailyManagerBriefDto.Trend();
        t.prevTotal = prevTotal;
        t.prevErrorRate = prevErrRate;
        return t;
    }

    private String buildExecutiveSummary(DailyManagerBriefDto d) {
        String day = d.dayStartLocal != null ? d.dayStartLocal : d.dayStart;
        String topSys = (!d.topSystems.isEmpty()) ? d.topSystems.get(0).name : "N/D";

        if (d.total <= 0) {
            return "Día " + day + ": no se registraron eventos. Posible caída de envío de logs o rango incorrecto.";
        }

        String riesgo =
                (d.errorRate >= ERR_CRIT) ? "Riesgo ALTO" :
                        (d.errorRate >= ERR_WARN) ? "Riesgo MEDIO" :
                                "Riesgo BAJO";

        return "Día " + day + ": " + d.total + " eventos. Error rate " +
                String.format(Locale.ROOT, "%.2f%%", d.errorRate * 100.0) +
                " (" + riesgo + "). Sistema con mayor actividad: " + topSys + ".";
    }

    private List<String> buildKeyPoints(DailyManagerBriefDto d) {
        List<String> out = new ArrayList<>();

        out.add("Eventos totales: " + d.total);
        out.add("Errores: " + d.errorCount + " (" + String.format(Locale.ROOT, "%.2f%%", d.errorRate * 100.0) + ")");

        if (!d.topSystems.isEmpty()) out.add("Top sistema: " + d.topSystems.get(0).name + " (" + d.topSystems.get(0).count + ")");
        if (!d.topEventTypes.isEmpty()) out.add("Top evento: " + d.topEventTypes.get(0).name + " (" + d.topEventTypes.get(0).count + ")");

        // Dominancia del top system
        if (d.total > 0 && !d.topSystems.isEmpty()) {
            double share = (double) d.topSystems.get(0).count / (double) d.total;
            if (share >= DOMINANCE_WARN) {
                out.add("Concentración: un solo sistema genera " + String.format(Locale.ROOT, "%.0f%%", share * 100.0) + " del tráfico.");
            }
        }

        // Top error si existe
        if (!d.topErrors.isEmpty()) {
            out.add("Error más repetido: \"" + safeSnippet(d.topErrors.get(0).key) + "\" (" + d.topErrors.get(0).count + ")");
        }

        return out;
    }

    private List<String> buildActions(DailyManagerBriefDto d) {
        List<String> out = new ArrayList<>();

        if (d.total <= 0) {
            out.add("1) Confirmar si el sistema fuente está en línea y enviando logs.");
            out.add("2) Validar configuración de conexión/red/credenciales hacia Backlogs.");
            out.add("3) Revisar ventanas de tiempo (tz/from/to) y monitorear la siguiente hora.");
            return out;
        }

        if (d.errorRate >= ERR_CRIT) {
            out.add("1) Escalar incidente: tasa de error crítica.");
            out.add("2) Revisar errores recurrentes (topErrors) y correlación (requestId/caseId).");
            out.add("3) Abrir ticket y asignar responsable (sistema + evidencia).");
            return out;
        }

        if (d.errorRate >= ERR_WARN) {
            out.add("1) Priorizar revisión de errores (topErrors) y patrones por sistema/eventType.");
            out.add("2) Confirmar si hubo deploy/cambio de configuración el día del pico.");
            out.add("3) Crear ticket preventivo si el patrón se repite 2 días.");
            return out;
        }

        out.add("1) Monitoreo normal: sin señales críticas por tasa de error.");
        out.add("2) Revisar concentración por sistema (si aplica) para balancear visibilidad.");
        out.add("3) Mantener seguimiento de tendencias (volumen/errores) mañana.");
        return out;
    }

    private List<DailyManagerBriefDto.TopItem> mapTopItems(List<DailySummaryDto.TopItem> items) {
        if (items == null) return List.of();
        return items.stream()
                .filter(Objects::nonNull)
                .map(x -> new DailyManagerBriefDto.TopItem(x.name, x.count))
                .toList();
    }

    private List<DailyManagerBriefDto.TopError> mapTopErrors(List<DailySummaryDto.TopError> items) {
        if (items == null) return List.of();
        return items.stream()
                .filter(Objects::nonNull)
                .map(x -> new DailyManagerBriefDto.TopError(x.key, x.count))
                .toList();
    }

    private ZoneId safeZone(String tz) {
        try {
            if (!StringUtils.hasText(tz)) return ZoneId.of("America/Mexico_City");
            return ZoneId.of(tz.trim());
        } catch (Exception e) {
            return ZoneId.of("America/Mexico_City");
        }
    }

    private String toLocal(Instant instant, ZoneId zone) {
        return (instant == null) ? null : instant.atZone(zone).format(ISO_OFFSET);
    }

    private String safeSnippet(String s) {
        if (s == null) return "";
        String x = s.replaceAll("\\s+", " ").trim();
        return x.length() <= 120 ? x : x.substring(0, 120) + "...";
    }
}
