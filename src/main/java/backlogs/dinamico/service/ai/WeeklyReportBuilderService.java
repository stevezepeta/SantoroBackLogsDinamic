package backlogs.dinamico.service.ai;

import backlogs.dinamico.model.ai.WeeklyReportData;
import backlogs.dinamico.service.ai.dto.SummaryInsightsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Construye el WeeklyReportData con comparativa semana actual vs semana anterior.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportBuilderService {

    private final MongoTemplate          mongoTemplate;
    private final EvaDeepAnalysisService deepAnalysis;

    private static final ZoneId           MX_ZONE  = ZoneId.of("America/Mexico_City");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(MX_ZONE);

    // ── Entry point ───────────────────────────────────────────────────────────

    public WeeklyReportData build(ObjectId tenantId) {
        // Calcular rangos de semanas
        ZonedDateTime now      = ZonedDateTime.now(MX_ZONE);
        ZonedDateTime sunEnd   = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .withHour(23).withMinute(59).withSecond(59);
        ZonedDateTime monStart = sunEnd.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .withHour(0).withMinute(0).withSecond(0);

        ZonedDateTime prevSunEnd   = monStart.minusSeconds(1);
        ZonedDateTime prevMonStart = prevSunEnd.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .withHour(0).withMinute(0).withSecond(0);

        Instant thisFrom = monStart.toInstant();
        Instant thisTo   = sunEnd.toInstant();
        Instant prevFrom = prevMonStart.toInstant();
        Instant prevTo   = prevSunEnd.toInstant();

        log.info("[WeeklyReport] Semana actual: {} → {}", thisFrom, thisTo);
        log.info("[WeeklyReport] Semana anterior: {} → {}", prevFrom, prevTo);

        WeeklyReportData data = new WeeklyReportData();

        // Etiquetas
        data.weekLabel     = DATE_FMT.format(thisFrom) + " — " + DATE_FMT.format(thisTo);
        data.prevWeekLabel = DATE_FMT.format(prevFrom) + " — " + DATE_FMT.format(prevTo);

        // Datos globales
        WeekStats thisWeek = queryWeekStats(tenantId, thisFrom, thisTo);
        WeekStats prevWeek = queryWeekStats(tenantId, prevFrom, prevTo);

        data.totalThisWeek  = thisWeek.total;
        data.totalLastWeek  = prevWeek.total;
        data.totalChangePct = changePct(prevWeek.total, thisWeek.total);

        data.errorRateThisWeek  = thisWeek.errorRate;
        data.errorRateLastWeek  = prevWeek.errorRate;
        data.errorRateChangePct = changePct(prevWeek.errorRate, thisWeek.errorRate);

        // Sistema más activo
        data.topSystem      = thisWeek.topSystem;
        data.topSystemTotal = thisWeek.topSystemTotal;

        // Por sistema
        data.systems = buildSystemStats(tenantId, thisFrom, thisTo, prevFrom, prevTo);

        // Top errores
        data.topErrors = queryTopErrors(tenantId, thisFrom, thisTo, 5);

        // Estado general
        data.status = deriveStatus(data.errorRateThisWeek);

        // Análisis IA
        buildAiAnalysis(data, tenantId, thisFrom, thisTo);

        return data;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    private record WeekStats(long total, double errorRate,
                             long failures, String topSystem, long topSystemTotal) {}

    private WeekStats queryWeekStats(ObjectId tenantId, Instant from, Instant to) {
        try {
            org.bson.Document matchDoc = new org.bson.Document("$match",
                    new org.bson.Document("tenant_id", tenantId)
                            .append("eventTime", new org.bson.Document("$gte", java.util.Date.from(from))
                                    .append("$lt", java.util.Date.from(to))));

            org.bson.Document groupDoc = new org.bson.Document("$group",
                    new org.bson.Document("_id", null)
                            .append("total", new org.bson.Document("$sum", 1))
                            .append("failures", new org.bson.Document("$sum",
                                    new org.bson.Document("$cond", java.util.Arrays.asList(
                                            new org.bson.Document("$eq",
                                                    java.util.Arrays.asList("$outcome", "FAILURE")),
                                            1, 0)))));

            List<org.bson.Document> pipeline = java.util.Arrays.asList(matchDoc, groupDoc);
            org.bson.Document result = mongoTemplate.getDb()
                    .getCollection("log_events")
                    .aggregate(pipeline, org.bson.Document.class)
                    .first();

            long total    = result != null ? ((Number) result.getOrDefault("total",    0)).longValue() : 0L;
            long failures = result != null ? ((Number) result.getOrDefault("failures", 0)).longValue() : 0L;
            double errorRate = total > 0 ? (double) failures / total : 0.0;

            String topSystem      = null;
            long   topSystemTotal = 0L;
            List<org.bson.Document> topSystems = queryTopSystems(tenantId, from, to, 1);
            if (!topSystems.isEmpty()) {
                topSystem      = topSystems.get(0).getString("_id");
                topSystemTotal = ((Number) topSystems.get(0).get("count")).longValue();
            }

            return new WeekStats(total, errorRate, failures, topSystem, topSystemTotal);
        } catch (Exception e) {
            log.error("[WeeklyReport] Error queryWeekStats: {}", e.getMessage());
            return new WeekStats(0, 0, 0, null, 0);
        }
    }

    private List<org.bson.Document> queryTopSystems(
            ObjectId tenantId, Instant from, Instant to, int limit) {
        try {
            MatchOperation match = Aggregation.match(new Criteria().andOperator(
                    Criteria.where("tenant_id").is(tenantId),
                    Criteria.where("eventTime").gte(from).lt(to)));
            GroupOperation  group = Aggregation.group("system").count().as("count");
            SortOperation   sort  = Aggregation.sort(
                    org.springframework.data.domain.Sort.Direction.DESC, "count");
            LimitOperation  lim   = Aggregation.limit(limit);

            return mongoTemplate.aggregate(
                    Aggregation.newAggregation(match, group, sort, lim),
                    "log_events", org.bson.Document.class).getMappedResults();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<WeeklyReportData.SystemWeeklyStats> buildSystemStats(
            ObjectId tenantId,
            Instant thisFrom, Instant thisTo,
            Instant prevFrom, Instant prevTo) {
        try {
            // Obtener sistemas activos esta semana
            List<org.bson.Document> topSystems = queryTopSystems(tenantId, thisFrom, thisTo, 10);
            List<WeeklyReportData.SystemWeeklyStats> result = new ArrayList<>();

            for (org.bson.Document doc : topSystems) {
                String system = doc.getString("_id");
                if (system == null || system.isBlank()) continue;

                WeekStats thisW = querySystemWeekStats(tenantId, system, thisFrom, thisTo);
                WeekStats prevW = querySystemWeekStats(tenantId, system, prevFrom, prevTo);

                WeeklyReportData.SystemWeeklyStats s = new WeeklyReportData.SystemWeeklyStats();
                s.setSystem(system);
                s.setTotalThisWeek(thisW.total);
                s.setTotalLastWeek(prevW.total);
                s.setErrorRateThisWeek(thisW.errorRate);
                s.setErrorRateLastWeek(prevW.errorRate);
                s.setFailuresThisWeek(thisW.failures);
                s.setSuccessesThisWeek(thisW.total - thisW.failures);
                s.setTrend(trend(prevW.errorRate, thisW.errorRate));
                result.add(s);
            }

            return result;
        } catch (Exception e) {
            log.error("[WeeklyReport] Error buildSystemStats: {}", e.getMessage());
            return List.of();
        }
    }

    private WeekStats querySystemWeekStats(
            ObjectId tenantId, String system, Instant from, Instant to) {
        try {
            org.bson.Document matchDoc = new org.bson.Document("$match",
                    new org.bson.Document("tenant_id", tenantId)
                            .append("system", system)
                            .append("eventTime", new org.bson.Document("$gte", java.util.Date.from(from))
                                    .append("$lt", java.util.Date.from(to))));

            org.bson.Document groupDoc = new org.bson.Document("$group",
                    new org.bson.Document("_id", null)
                            .append("total", new org.bson.Document("$sum", 1))
                            .append("failures", new org.bson.Document("$sum",
                                    new org.bson.Document("$cond", java.util.Arrays.asList(
                                            new org.bson.Document("$eq",
                                                    java.util.Arrays.asList("$outcome", "FAILURE")),
                                            1, 0)))));

            List<org.bson.Document> pipeline = java.util.Arrays.asList(matchDoc, groupDoc);
            org.bson.Document result = mongoTemplate.getDb()
                    .getCollection("log_events")
                    .aggregate(pipeline, org.bson.Document.class)
                    .first();

            long total    = result != null ? ((Number) result.getOrDefault("total",    0)).longValue() : 0L;
            long failures = result != null ? ((Number) result.getOrDefault("failures", 0)).longValue() : 0L;
            double errorRate = total > 0 ? (double) failures / total : 0.0;

            return new WeekStats(total, errorRate, failures, system, total);
        } catch (Exception e) {
            return new WeekStats(0, 0, 0, system, 0);
        }
    }

    private List<WeeklyReportData.TopError> queryTopErrors(
            ObjectId tenantId, Instant from, Instant to, int limit) {
        try {
            MatchOperation match = Aggregation.match(new Criteria().andOperator(
                    Criteria.where("tenant_id").is(tenantId),
                    Criteria.where("eventTime").gte(from).lt(to),
                    Criteria.where("isError").is(true)));

            GroupOperation  group = Aggregation.group("messageKey").count().as("count");
            SortOperation   sort  = Aggregation.sort(
                    org.springframework.data.domain.Sort.Direction.DESC, "count");
            LimitOperation  lim   = Aggregation.limit(limit);

            return mongoTemplate.aggregate(
                            Aggregation.newAggregation(match, group, sort, lim),
                            "log_events", org.bson.Document.class)
                    .getMappedResults().stream()
                    .map(d -> {
                        WeeklyReportData.TopError e = new WeeklyReportData.TopError();
                        e.setKey(d.getString("_id"));
                        e.setCount(((Number) d.get("count")).longValue());
                        return e;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[WeeklyReport] Error queryTopErrors: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Análisis IA ───────────────────────────────────────────────────────────

    private void buildAiAnalysis(WeeklyReportData data,
                                 ObjectId tenantId, Instant from, Instant to) {
        try {
            if (data.topSystem == null) return;

            // Reusar SummaryInsightsDto para pasarle contexto a Eva
            SummaryInsightsDto insights = new SummaryInsightsDto();
            insights.total      = data.totalThisWeek;
            insights.errorRate  = data.errorRateThisWeek;
            insights.status     = data.status;

            EvaDeepAnalysisService.DeepAnalysisResult ai =
                    deepAnalysis.analyzeFromInsights(
                            tenantId, data.topSystem, from, to, insights);

            if (ai != null) {
                data.aiSummary     = ai.aiSummary();
                data.aiSuggestions = ai.aiSuggestions();
            }
        } catch (Exception e) {
            log.warn("[WeeklyReport] Error análisis IA: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double changePct(double prev, double current) {
        if (prev == 0) return current > 0 ? 100.0 : 0.0;
        return ((current - prev) / prev) * 100.0;
    }

    private String trend(double prev, double current) {
        double diff = current - prev;
        if (Math.abs(diff) < 0.01) return "STABLE";
        return diff > 0 ? "UP" : "DOWN";
    }

    private String deriveStatus(double errorRate) {
        if (errorRate >= 0.15) return "CRIT";
        if (errorRate >= 0.05) return "WARN";
        return "HEALTHY";
    }
}