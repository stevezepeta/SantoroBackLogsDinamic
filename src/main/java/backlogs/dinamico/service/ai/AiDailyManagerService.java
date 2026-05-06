package backlogs.dinamico.service.ai;

import backlogs.dinamico.service.ai.dto.AiTicketDraftDto;
import backlogs.dinamico.service.ai.dto.DailyManagerSummaryDto;
import backlogs.dinamico.service.ai.dto.DailySummaryDto;
import backlogs.dinamico.service.ai.dto.SummaryInsightsDto;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;


@Service
@RequiredArgsConstructor
public class AiDailyManagerService {

    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final DailySummaryService dailySummaryService;
    private final SummaryInsightsService summaryInsightsService;
    private final MongoTemplate mongoTemplate;

    @Value("${multitenant.log-collection:log_events}")
    private String logCollection;

    // Campos en LogEvent
    private static final String F_TENANT = "tenant_id";
    private static final String F_TIME   = "eventTime";
    private static final String F_SYS    = "system";
    private static final String F_TYPE   = "eventType";
    private static final String F_STATUS = "status";
    private static final String F_OUT    = "outcome";
    private static final String F_IS_ERROR = "isError";
    private static final String F_MSG_KEY = "messageKey";
    private static final String F_MSG     = "message";

    public DailyManagerSummaryDto buildManagerSummary(ObjectId tenantId, int days, String tz, Instant from, Instant to) {
        return buildManagerSummary(tenantId, days, tz, from, to, null, null);
    }

    /**
     * Overload con filtro de sistema — usado por Eva cuando el usuario
     * tiene sistemas restringidos (SYSTEM_MANAGER, VIEWER con allowedSystems).
     *
     * @param systemFilter   Sistema específico a resumir (ej: "TRUSTVALUE")
     * @param allowedSystems Lista de sistemas que el usuario puede ver.
     *                       Si es null o vacío y systemFilter es null → resumen global.
     */
    public DailyManagerSummaryDto buildManagerSummary(ObjectId tenantId, int days, String tz,
                                                      Instant from, Instant to,
                                                      String systemFilter,
                                                      List<String> allowedSystems) {
        ZoneId zone = safeZone(tz);
        TimeRange r = lastNDaysComplete(zone, days, from, to);

        // Resolver qué sistema usar para el resumen
        String effectiveSystem = systemFilter;
        if (!StringUtils.hasText(effectiveSystem)
                && allowedSystems != null && !allowedSystems.isEmpty()) {
            effectiveSystem = allowedSystems.get(0);
        }

        DailySummaryDto daily = dailySummaryService.buildDailySummary(
                tenantId, r.days, zone.getId(), r.from, r.to, effectiveSystem
        );
        SummaryInsightsDto insights = summaryInsightsService.fromDaily(tenantId, daily);

        DailyManagerSummaryDto out = new DailyManagerSummaryDto();
        out.tz = zone.getId();

        out.from = r.from.toString();
        out.to = r.to.toString();
        out.fromLocal = toLocal(r.from, zone);
        out.toLocal = toLocal(r.to, zone);
        out.days = r.days;

        out.total = insights.total;
        out.errorRate = insights.errorRate;
        out.status = insights.status;

        out.severities = insights.severities;

        out.topSystemsRange = safeList(insights.topSystemsRange);
        out.topEventTypesRange = safeList(insights.topEventTypesRange);
        out.topStatusRange = safeList(insights.topStatusRange);
        out.topOutcomeRange = safeList(insights.topOutcomeRange);
        out.topErrorsRange = safeListErrors(insights.topErrorsRange);

        // ── Filtrar topSystemsRange si el usuario tiene sistemas restringidos ──
        if (allowedSystems != null && !allowedSystems.isEmpty()) {
            final List<String> allowed = allowedSystems;
            out.topSystemsRange = out.topSystemsRange.stream()
                    .filter(s -> s != null && allowed.contains(s.name))
                    .toList();

            // Recalcular total solo con los sistemas permitidos
            out.total = out.topSystemsRange.stream().mapToLong(s -> s.count).sum();
        }

        // topErrorsRange filtrado por sistema
        if (out.topErrorsRange == null || out.topErrorsRange.isEmpty()) {
            String topSys = StringUtils.hasText(effectiveSystem)
                    ? effectiveSystem
                    : firstTopName(out.topSystemsRange);
            out.topErrorsRange = aggregateTopErrorRange(tenantId, r.from, r.to, topSys, 5);
        }

        out.executiveSummary = new ArrayList<>();
        out.risks = new ArrayList<>();
        out.actions = new ArrayList<>();

        // ── Executive bullets ─────────────────────────────────────────────────
        String scopeLabel = StringUtils.hasText(effectiveSystem) ? effectiveSystem : "todos los sistemas";
        out.executiveSummary.add("Eventos en " + scopeLabel + ": " + out.total
                + " (errorRate: " + pct(out.errorRate) + ").");

        if (!out.topSystemsRange.isEmpty()) {
            var topS = out.topSystemsRange.get(0);
            out.executiveSummary.add("Sistema principal: " + topS.name + " (" + topS.count + ").");
        }

        var topErr = firstTopError(out.topErrorsRange);
        if (topErr != null)
            out.executiveSummary.add("Error más frecuente: " + snippet(topErr.key, 90) + " (" + topErr.count + ").");

        if ("CRIT".equalsIgnoreCase(out.status)) {
            out.risks.add("Riesgo ALTO: indicadores críticos en el periodo (revisar inmediatamente).");
            out.actions.add("Abrir incidente / escalación (P1) y asignar owner.");
        } else if ("WARN".equalsIgnoreCase(out.status)) {
            out.risks.add("Riesgo MEDIO: señales operativas relevantes (revisar hoy).");
            out.actions.add("Crear tickets preventivos (P2) y monitorear.");
        } else {
            out.actions.add("Mantener monitoreo. Revisar tendencias si aumenta FAILURE/REJECTED.");
        }

        Map<String, Object> filters = new LinkedHashMap<>();
        if (StringUtils.hasText(effectiveSystem)) filters.put("system", effectiveSystem);
        filters.put("from", out.from);
        filters.put("to", out.to);
        out.suggestedFilters = filters;

        out.executiveSummary = clamp(out.executiveSummary, 5);
        out.risks  = clamp(out.risks, 5);
        out.actions = clamp(out.actions, 5);

        return out;
    }

    public List<AiTicketDraftDto> buildTicketDraftsFromManagerSummary(
            ObjectId tenantId,
            int days,
            String tz,
            Instant from,
            Instant to,
            String system,
            int maxTickets
    ) {
        ZoneId zone = safeZone(tz);
        TimeRange r = lastNDaysComplete(zone, days, from, to);

        // ── CORRECCIÓN: pasar system y allowedSystems para que el summary
        // base no mezcle datos de otros sistemas al generar ticket drafts ──
        DailyManagerSummaryDto mgr = buildManagerSummary(tenantId, days, zone.getId(), r.from, r.to, system, null);

        Instant rangeFrom = r.from;
        Instant rangeTo = r.to;

        int safeMax = Math.min(Math.max(maxTickets, 1), 10);

        // Base: topErrorsRange (ya es global)
        List<SummaryInsightsDto.TopError> topErrors = mgr.topErrorsRange == null ? List.of() : mgr.topErrorsRange;
        if (topErrors.isEmpty()) return List.of();

        String topSystem = (StringUtils.hasText(system)) ? system : firstTopName(mgr.topSystemsRange);

        List<AiTicketDraftDto> drafts = new ArrayList<>();

        for (SummaryInsightsDto.TopError e : topErrors) {
            if (drafts.size() >= safeMax) break;
            if (e == null || !StringUtils.hasText(e.key) || e.count <= 0) continue;

            // samples para evidencia (por messageKey exacto, fallback por contains)
            List<Document> samples = fetchSamplesForMessageKey(tenantId, rangeFrom, rangeTo, topSystem, e.key, 5);

            AiTicketDraftDto d = new AiTicketDraftDto();
            d.tz = zone.getId();
            d.alertId = null;

            String status = mgr.status != null ? mgr.status : "OK";
            d.priority = priorityFrom(status, e.count, e.key);

            d.title = "[" + status + "] Daily - " + (topSystem != null ? topSystem : "SYSTEM") + " - " + snippet(e.key, 90);

            d.labels = List.of(
                    "ai", "logs", "daily_manager",
                    "system:" + (topSystem != null ? topSystem : "unknown"),
                    "status:" + status
            );

            d.format = "markdown";

            d.actualBehavior = "Se detectó recurrencia del error en el periodo (count=" + e.count + ").";
            d.expectedBehavior = "El flujo no debería fallar repetidamente; los errores deben ser excepcionales o explicados por evento esperado.";

            Map<String, Object> suggested = new LinkedHashMap<>();
            if (topSystem != null) suggested.put("system", topSystem);
            suggested.put("from", mgr.from);
            suggested.put("to", mgr.to);
            suggested.put("messageKey", e.key);
            suggested.put("isError", true);
            d.suggestedFilters = suggested;

            d.stepsToReproduce = List.of(
                    "1) Ejecutar el flujo relacionado al endpoint/acción indicada en el error.",
                    "2) Validar que la operación falla bajo las mismas condiciones.",
                    "3) Revisar logs con los filtros sugeridos y confirmar patrón (messageKey/requestId/outcome)."
            );

            d.meta = new LinkedHashMap<>();
            d.meta.put("kind", "daily_manager_ticket");
            d.meta.put("status", status);
            d.meta.put("windowFrom", mgr.from);
            d.meta.put("windowTo", mgr.to);
            d.meta.put("windowFromLocal", mgr.fromLocal);
            d.meta.put("windowToLocal", mgr.toLocal);
            d.meta.put("errorKey", e.key);
            d.meta.put("errorCount", e.count);

            d.descriptionMarKdown = buildMarkdownDailyTicket(mgr, topSystem, e, samples);

            drafts.add(d);
        }

        return drafts;
    }

    private List<SummaryInsightsDto.TopError> aggregateTopErrorRange(
            ObjectId tenantId, Instant from, Instant to, String system, int limit
    ) {

        int safeLimit = Math.min(Math.max(limit, 1), 20);

        Criteria base = Criteria.where("tenant_id").is(tenantId)
                .and("eventTime").gte(Date.from(from)).lt(Date.from(to));

        if (StringUtils.hasText(system)) {
            base = new Criteria().andOperator(base, Criteria.where("system").is(system));
        }

        // Regla de error robusta
        Criteria isErr = new Criteria().orOperator(
                Criteria.where("isError").is(true),
                Criteria.where("outcome").is("FAILURE"),
                Criteria.where("status").in("REJECTED", "ERROR"),
                Criteria.where("outcome").regex("ERR", "I")
        );

        // errKey = messageKey
        Document errTextExpr = new Document("$ifNull", List.of(
                new Document("$ifNull", List.of("$messageKey",
                        new Document("$ifNull", List.of("$message", "$reason.description"))
                )),
                ""
        ));

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(new Criteria().andOperator(base, isErr)),

                Aggregation.addFields().addFieldWithValue("errKey",
                        new Document("$substrCP", List.of(errTextExpr, 0, 160))
                ).build(),

                Aggregation.match(Criteria.where("errKey").ne("")),

                Aggregation.group("errKey")
                        .count().as("c"),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "c")),
                Aggregation.limit(safeLimit),

                Aggregation.project().and("_id").as("key").and("c").as("count").andExclude("_id")
        );

        List<Document> rows = mongoTemplate.aggregate(agg, logCollection, Document.class).getMappedResults();

        List<SummaryInsightsDto.TopError> out = new ArrayList<>();
        for (Document r : rows) {
            out.add(new SummaryInsightsDto.TopError(
                    Objects.toString(r.get("key"), ""),
                    toLong(r.get("count"))
            ));
        }
        return out;
    }

    // ----------------------- helpers -----------------------
    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }

    private List<Document> fetchSamplesForMessageKey(ObjectId tenantId, Instant from, Instant to, String system, String messageKey, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);

        Criteria c = Criteria.where(F_TENANT).is(tenantId)
                .and(F_TIME).gte(Date.from(from)).lt(Date.from(to));

        if (StringUtils.hasText(system)) {
            c = new Criteria().andOperator(c, Criteria.where(F_SYS).is(system));
        }

        // Solo errores (si tu data usa isError)
        Criteria isErr = new Criteria().orOperator(
                Criteria.where(F_IS_ERROR).is(true),
                Criteria.where(F_OUT).is("FAILURE"),
                Criteria.where(F_STATUS).in("REJECTED", "ERROR")
        );

        // match exact messageKey
        Criteria byKey = Criteria.where(F_MSG_KEY).is(messageKey);

        Criteria finalC = new Criteria().andOperator(c, isErr, byKey);

        Query q = new Query(finalC);
        q.with(Sort.by(Sort.Direction.DESC, F_TIME));
        q.limit(safeLimit);

        List<Document> rows = mongoTemplate.find(q, Document.class, logCollection);

        // fallback: contains (si no hay exact match)
        if (rows.isEmpty()) {
            String rep = snippet(messageKey, 80);
            if (StringUtils.hasText(rep)) {
                Pattern p = Pattern.compile(Pattern.quote(rep), Pattern.CASE_INSENSITIVE);
                Criteria fallback = new Criteria().andOperator(c, isErr,
                        new Criteria().orOperator(
                                Criteria.where(F_MSG_KEY).regex(p),
                                Criteria.where(F_MSG).regex(p)
                        )
                );
                Query q2 = new Query(fallback);
                q2.with(Sort.by(Sort.Direction.DESC, F_TIME));
                q2.limit(safeLimit);
                rows = mongoTemplate.find(q2, Document.class, logCollection);
            }
        }

        return rows;
    }

    private static ZonedDateTime ceilToDay(ZonedDateTime zdt) {
        ZonedDateTime floor = zdt.truncatedTo(ChronoUnit.DAYS);
        return zdt.equals(floor) ? floor : floor.plusDays(1);
    }

    private String buildMarkdownDailyTicket(DailyManagerSummaryDto mgr, String system, SummaryInsightsDto.TopError e, List<Document> samples) {
        StringBuilder sb = new StringBuilder();

        sb.append("## Resumen\n");
        sb.append("- Error recurrente detectado en resumen diario gerencial.\n");
        sb.append("- Count: ").append(e.count).append("\n");
        sb.append("- ErrorKey: `").append(snippet(e.key, 140)).append("`\n\n");

        sb.append("## Ventana\n");
        sb.append("- Window: ").append(mgr.fromLocal).append(" → ").append(mgr.toLocal).append("\n\n");

        sb.append("## Contexto (tops del rango)\n");
        sb.append("- TopSystems: ").append(fmtTopItems(mgr.topSystemsRange)).append("\n");
        sb.append("- TopEventTypes: ").append(fmtTopItems(mgr.topEventTypesRange)).append("\n");
        sb.append("- TopStatus: ").append(fmtTopItems(mgr.topStatusRange)).append("\n");
        sb.append("- TopOutcome: ").append(fmtTopItems(mgr.topOutcomeRange)).append("\n\n");

        sb.append("## Filtros sugeridos\n```json\n");
        sb.append("{\n");
        if (StringUtils.hasText(system)) sb.append("  \"system\": \"").append(system).append("\",\n");
        sb.append("  \"from\": \"").append(mgr.from).append("\",\n");
        sb.append("  \"to\": \"").append(mgr.to).append("\",\n");
        sb.append("  \"messageKey\": \"").append(escapeJson(snippet(e.key, 180))).append("\",\n");
        sb.append("  \"isError\": true\n");
        sb.append("}\n");
        sb.append("```\n\n");

        sb.append("## Muestras (primeros ").append(Math.min(samples.size(), 5)).append(")\n");
        for (int i = 0; i < Math.min(samples.size(), 5); i++) {
            Document d = samples.get(i);
            String t = Objects.toString(d.get(F_TIME), "");
            String msg = Objects.toString(d.get(F_MSG), "");
            sb.append("- ").append(t).append(" | ").append(snippet(msg, 120)).append("\n");
        }

        return sb.toString();
    }

    private static String fmtTopItems(List<SummaryInsightsDto.TopItem> items) {
        if (items == null || items.isEmpty()) return "(sin datos)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(items.size(), 3); i++) {
            var it = items.get(i);
            if (it == null) continue;
            if (i > 0) sb.append(" | ");
            sb.append(it.name).append(" (").append(it.count).append(")");
        }
        return sb.toString();
    }

    private static String firstTopName(List<SummaryInsightsDto.TopItem> items) {
        if (items == null || items.isEmpty()) return null;
        var it = items.get(0);
        return (it != null && StringUtils.hasText(it.name)) ? it.name : null;
    }

    private static SummaryInsightsDto.TopError firstTopError(List<SummaryInsightsDto.TopError> items) {
        if (items == null || items.isEmpty()) return null;
        var it = items.get(0);
        return (it != null && StringUtils.hasText(it.key)) ? it : null;
    }

    private static <T> List<T> clamp(List<T> xs, int max) {
        if (xs == null) return List.of();
        return xs.size() <= max ? xs : xs.subList(0, max);
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.2f%%", v * 100.0);
    }

    private static String snippet(String s, int max) {
        if (!StringUtils.hasText(s)) return s;
        String x = s.trim();
        if (x.length() <= max) return x;
        return x.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String toLocal(Instant instant, ZoneId zone) {
        return instant == null ? null : instant.atZone(zone).format(ISO_OFFSET);
    }

    private static ZoneId safeZone(String tz) {
        try {
            return StringUtils.hasText(tz) ? ZoneId.of(tz.trim()) : ZoneId.of("America/Mexico_City");
        } catch (Exception e) {
            return ZoneId.of("America/Mexico_City");
        }
    }

    private static String priorityFrom(String status, long count, String key) {
        String k = (key == null) ? "" : key.toLowerCase(Locale.ROOT);
        if ("CRIT".equalsIgnoreCase(status)) return "P1";
        if (k.contains("jdbc") || k.contains("unknown column") || k.contains("db")) return "P1";
        if (count >= 20) return "P1";
        if ("WARN".equalsIgnoreCase(status)) return "P2";
        if (count >= 5) return "P2";
        return "P3";
    }

    private static List<SummaryInsightsDto.TopItem> safeList(List<SummaryInsightsDto.TopItem> x) {
        return x == null ? new ArrayList<>() : x;
    }

    private static List<SummaryInsightsDto.TopError> safeListErrors(List<SummaryInsightsDto.TopError> x) {
        return x == null ? new ArrayList<>() : x;
    }

    private static class TimeRange {
        final Instant from;
        final  Instant to;
        final int days;
        TimeRange(Instant from, Instant to, int days) {
            this.from = from;
            this.to = to;
            this.days = days;
        }
    }

    /**
     * Ultimos N dias completos (dia calendario)
     */
    private static TimeRange lastNDaysComplete(ZoneId zone, int days, Instant from, Instant to) {
        int d = Math.max(days, 1);

        // Si el usuario manda rango explícito, lo respetamos (pero alineamos a día)
        if (from != null || to != null) {
            ZonedDateTime end = (to != null)
                    ? ceilToDay(ZonedDateTime.ofInstant(to, zone))
                    : ZonedDateTime.now(zone).truncatedTo(ChronoUnit.DAYS);

            ZonedDateTime start = (from != null)
                    ? ZonedDateTime.ofInstant(from, zone).truncatedTo(ChronoUnit.DAYS)
                    : end.minusDays(d);

            Instant f = start.toInstant();
            Instant t = end.toInstant();
            int effectiveDays = (int) ChronoUnit.DAYS.between(start, end);
            return new TimeRange(f, t, Math.max(effectiveDays, 1));
        }

        // Caso normal days=N sin rango explícito
        ZonedDateTime endExclusive = ZonedDateTime.now(zone).truncatedTo(ChronoUnit.DAYS); // inicio de HOY
        ZonedDateTime startInclusive = endExclusive.minusDays(d); // inicio de hace N días
        return new TimeRange(startInclusive.toInstant(), endExclusive.toInstant(), d);
    }

}