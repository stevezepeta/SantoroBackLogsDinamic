package backlogs.dinamico.service.ai;

import backlogs.dinamico.service.ai.dto.DailySummaryDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
public class DailySummaryService {

    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static final int MAX_DAYS = 180;
    private static final String DEFAULT_TZ = "America/Mexico_City";

    private final MongoTemplate mongoTemplate;

    @Value("${multitenant.log-collection:log_events}")
    private String logCollection;

    // Campos reales
    private static final String F_TENANT = "tenant_id";
    private static final String F_TIME   = "eventTime";
    private static final String F_SEV    = "severity";
    private static final String F_SYS    = "system";
    private static final String F_MSG    = "message";
    private static final String F_TYPE   = "eventType";
    private static final String F_STATUS = "status";
    private static final String F_OUT    = "outcome";

    private static final String F_IS_ERROR = "isError";
    private static final String F_MSG_KEY = "messageKey";

    public DailySummaryDto buildDailySummary(ObjectId tenantId, int days, String tz, Instant from, Instant to) {

        int safeDays = sanitizeDays(days);
        ZoneId zone = safeZone(tz);

        // Si vienen ambos y vienen invertidos, los volteamos
        if (from != null && to != null && from.isAfter(to)) {
            Instant tmp = from; from = to; to = tmp;
        }

        // --------- RANGO ALINEADO A DÍAS (en TZ) ---------
        ZonedDateTime endExclusiveZdt = (to != null)
                ? ceilToDay(ZonedDateTime.ofInstant(to, zone))
                : ZonedDateTime.now(zone).truncatedTo(ChronoUnit.DAYS).plusDays(1); // mañana 00:00 local

        ZonedDateTime startInclusiveZdt = (from != null)
                ? ZonedDateTime.ofInstant(from, zone).truncatedTo(ChronoUnit.DAYS) // hoy 00:00 local
                : endExclusiveZdt.minusDays(safeDays); // exacto N días

        // Limita rango máximo
        long requestedDays = ChronoUnit.DAYS.between(startInclusiveZdt, endExclusiveZdt);
        if (requestedDays > MAX_DAYS) {
            startInclusiveZdt = endExclusiveZdt.minusDays(MAX_DAYS);
        }

        // Asegura que haya al menos 1 día
        if (!startInclusiveZdt.isBefore(endExclusiveZdt)) {
            endExclusiveZdt = startInclusiveZdt.plusDays(1);
        }

        Instant rangeFrom = startInclusiveZdt.toInstant();
        Instant rangeTo   = endExclusiveZdt.toInstant();

        // 1) Totales por día + severities
        List<Document> totals = aggregateTotalsByDay(tenantId, rangeFrom, rangeTo, zone.getId());

        // fallback si sale vacío: tomar último eventTime real
        if (totals.isEmpty()) {
            Instant latest = findLatestEventTime(tenantId);
            if (latest != null) {
                ZonedDateTime latestEndExclusive = ceilToDay(ZonedDateTime.ofInstant(latest, zone));
                endExclusiveZdt = latestEndExclusive;
                startInclusiveZdt = latestEndExclusive.minusDays(safeDays);

                rangeFrom = startInclusiveZdt.toInstant();
                rangeTo = endExclusiveZdt.toInstant();

                totals = aggregateTotalsByDay(tenantId, rangeFrom, rangeTo, zone.getId());
            }
        }

        Map<Date, Document> totalsByDay = totals.stream()
                .filter(d -> d.getDate("dayStart") != null)
                .collect(Collectors.toMap(
                        d -> d.getDate("dayStart"),
                        Function.identity(),
                        (a, b) -> a
                ));

        // 2) Tops por día
        Map<Date, List<DailySummaryDto.TopItem>> topSystems =
                aggregateTopByDay(tenantId, rangeFrom, rangeTo, zone.getId(), F_SYS, 5);

        Map<Date, List<DailySummaryDto.TopItem>> topTypes =
                aggregateTopByDay(tenantId, rangeFrom, rangeTo, zone.getId(), F_TYPE, 5);

        Map<Date, List<DailySummaryDto.TopItem>> topStatus =
                aggregateTopByDay(tenantId, rangeFrom, rangeTo, zone.getId(), F_STATUS, 5);

        Map<Date, List<DailySummaryDto.TopItem>> topOutcome =
                aggregateTopByDay(tenantId, rangeFrom, rangeTo, zone.getId(), F_OUT, 5);

        Map<Date, List<DailySummaryDto.TopError>> topErrors =
                aggregateTopErrorsByDay(tenantId, rangeFrom, rangeTo, zone.getId(), 5);

        // 3) Construir todos los días del rango (para buckets vacíos)
        List<Date> dayStarts = buildDayStarts(startInclusiveZdt, endExclusiveZdt);

        DailySummaryDto out = new DailySummaryDto();
        out.tz = zone.getId();
        out.from = rangeFrom.toString();
        out.to = rangeTo.toString();
        out.days = dayStarts.size();
        out.buckets = new ArrayList<>();

        for (Date dayStart : dayStarts) {
            Document t = totalsByDay.get(dayStart);

            long total = 0L;
            Map<String, Long> severities = Collections.emptyMap();

            if (t != null) {
                total = toLong(t.get("total"));

                @SuppressWarnings("unchecked")
                Map<String, Object> raw = (Map<String, Object>) t.get("severities", Map.class);

                if (raw != null && !raw.isEmpty()) {
                    severities = raw.entrySet().stream()
                            .filter(e -> StringUtils.hasText(e.getKey()))
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> toLong(e.getValue()),
                                    (a, b) -> a,
                                    LinkedHashMap::new
                            ));
                }
            }

            DailySummaryDto.Bucket b = new DailySummaryDto.Bucket();
            b.dayStart = dayStart.toInstant().toString(); // UTC
            b.dayStartLocal = dayStart.toInstant().atZone(zone).format(ISO_OFFSET); // local
            b.total = total;
            b.severities = severities;

            b.topSystems = topSystems.getOrDefault(dayStart, List.of());
            b.topEventTypes = topTypes.getOrDefault(dayStart, List.of());
            b.topStatus = topStatus.getOrDefault(dayStart, List.of());
            b.topOutcome = topOutcome.getOrDefault(dayStart, List.of());
            b.topErrors = topErrors.getOrDefault(dayStart, List.of());

            b.errorTotal = (t == null) ? 0L : toLong(t.get("errorTotal"));

            out.buckets.add(b);
        }

        return out;
    }

    // ----------------- Aggregations -----------------
    private List<Document> aggregateTotalsByDay(ObjectId tenantId, Instant from, Instant to, String tz) {

        var severitiesExpr = (org.springframework.data.mongodb.core.aggregation.AggregationExpression)
                ctx -> new Document("$arrayToObject",
                        new Document("$ifNull", List.of("$sevPairs", List.of()))
                );

        var errCond = (AggregationExpression) ctx ->
                new Document("$cond", List.of("$" + F_IS_ERROR, 1, 0));

        Aggregation agg = newAggregation(
                match(new Criteria()
                        .and(F_TENANT).is(tenantId)
                        .and(F_TIME).gte(Date.from(from)).lt(Date.from(to))
                        .and(F_SEV).exists(true).ne(null).ne("")
                ),

                addFields().addFieldWithValue("day",
                        new Document("$dateTrunc",
                                new Document("date", "$" + F_TIME)
                                        .append("unit", "day")
                                        .append("timezone", tz)
                        )
                ).build(),

                group(Fields.from(
                        Fields.field("day", "$day"),
                        Fields.field("sev", "$" + F_SEV)
                ))
                        .count().as("c")
                        .sum(errCond).as("errC"),

                sort(Sort.by(Sort.Direction.ASC, "_id.day")
                        .and(Sort.by(Sort.Direction.DESC, "c"))),

                group("day")
                        .sum("c").as("total")
                        .sum("errC").as("errorTotal")
                        .push(new Document("k", "$_id.sev").append("v", "$c")).as("sevPairs"),

                project()
                        .and("_id").as("dayStart")
                        .and("total").as("total")
                        .and("errorTotal").as("errorTotal")
                        .and(severitiesExpr).as("severities")
                        .andExclude("_id"),

                sort(Sort.by(Sort.Direction.ASC, "dayStart"))
        );

        return mongoTemplate.aggregate(agg, logCollection, Document.class).getMappedResults();
    }

    private Map<Date, List<DailySummaryDto.TopItem>> aggregateTopByDay(
            ObjectId tenantId, Instant from, Instant to, String tz, String field, int limit
    ) {

        Aggregation agg = newAggregation(
                match(Criteria.where(F_TENANT).is(tenantId)
                        .and(F_TIME).gte(Date.from(from)).lt(Date.from(to))
                        .and(field).exists(true).ne(null).ne("")
                ),

                addFields().addFieldWithValue("day",
                        new Document("$dateTrunc",
                                new Document("date", "$" + F_TIME)
                                        .append("unit", "day")
                                        .append("timezone", tz)
                        )
                ).build(),

                group(Fields.from(
                        Fields.field("day", "$day"),
                        Fields.field("val", "$" + field)
                )).count().as("c"),

                sort(Sort.by(Sort.Direction.ASC, "_id.day")
                        .and(Sort.by(Sort.Direction.DESC, "c"))),

                group("_id.day")
                        .push(new Document("name", "$_id.val").append("count", "$c"))
                        .as("items"),

                project()
                        .and("_id").as("day")
                        .and(ArrayOperators.Slice.sliceArrayOf("items").itemCount(limit)).as("items")
                        .andExclude("_id"),

                sort(Sort.by(Sort.Direction.ASC, "day"))
        );

        List<Document> rows = mongoTemplate.aggregate(agg, logCollection, Document.class).getMappedResults();
        Map<Date, List<DailySummaryDto.TopItem>> out = new HashMap<>();

        for (Document r : rows) {
            Date day = r.getDate("day");
            @SuppressWarnings("unchecked")
            List<Document> items = (List<Document>) r.get("items", List.class);

            List<DailySummaryDto.TopItem> mapped = (items == null) ? List.of() :
                    items.stream()
                            .map(d -> new DailySummaryDto.TopItem(
                                    d.getString("name"),
                                    toLong(d.get("count"))
                            ))
                            .filter(t -> StringUtils.hasText(t.name))
                            .toList();

            out.put(day, mapped);
        }
        return out;
    }

    private Map<Date, List<DailySummaryDto.TopError>> aggregateTopErrorsByDay(
            ObjectId tenantId, Instant from, Instant to, String tz, int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);

        AggregationExpression sliceItems = ctx ->
                new Document("$slice", List.of("$items", safeLimit));

        Aggregation agg = newAggregation(
                match(Criteria.where(F_TENANT).is(tenantId)
                        .and(F_TIME).gte(Date.from(from)).lt(Date.from(to))
                        .and(F_IS_ERROR).in(true)
                        .and(F_MSG_KEY).exists(true).ne(null).ne("")
                ),

                addFields().addFieldWithValue("day",
                        new Document("$dateTrunc",
                                new Document("date", "$" + F_TIME)
                                        .append("unit", "day")
                                        .append("timezone", tz)
                        )
                ).build(),

                // group1
                group(Fields.from(
                        Fields.field("day", "$day"),
                        Fields.field("key", "$" + F_MSG_KEY)
                )).count().as("c"),

                sort(Sort.by(Sort.Direction.ASC, "_id.day")
                        .and(Sort.by(Sort.Direction.DESC, "c"))),

                group("_id.day")
                        .push(new Document("key", "$_id.key").append("count", "$c"))
                        .as("items"),

                project()
                        .and("_id").as("day")
                        .and(sliceItems).as("items")
                        .andExclude("_id"),

                sort(Sort.by(Sort.Direction.ASC, "day"))
        );

        List<Document> rows = mongoTemplate.aggregate(agg, logCollection, Document.class).getMappedResults();
        Map<Date, List<DailySummaryDto.TopError>> out = new HashMap<>();

        for (Document r : rows) {
            Date day = r.getDate("day");
            @SuppressWarnings("unchecked")
            List<Document> items = (List<Document>) r.get("items", List.class);

            List<DailySummaryDto.TopError> mapped = (items == null) ? List.of() :
                    items.stream().map(d ->
                            new DailySummaryDto.TopError(
                                    Objects.toString(d.get("Key"), ""),
                                    toLong(d.get("count"))
                            )
                    ).toList();

            out.put(day, mapped);
        }

        return out;
    }

    private Instant findLatestEventTime(ObjectId tenantId) {
        Query q = new Query(Criteria.where(F_TENANT).is(tenantId));
        q.with(Sort.by(Sort.Direction.DESC, F_TIME));
        q.limit(1);

        Document doc = mongoTemplate.findOne(q, Document.class, logCollection);
        if (doc == null) return null;

        Date dt = doc.getDate(F_TIME);
        return dt != null ? dt.toInstant() : null;
    }

    private List<Date> buildDayStarts(ZonedDateTime startInclusive, ZonedDateTime endExclusive) {
        ZonedDateTime cursor = startInclusive.truncatedTo(ChronoUnit.DAYS);
        List<Date> out = new ArrayList<>();
        while (cursor.isBefore(endExclusive)) {
            out.add(Date.from(cursor.toInstant()));
            cursor = cursor.plusDays(1);
        }
        return out;
    }

    private static ZonedDateTime ceilToDay(ZonedDateTime zdt) {
        ZonedDateTime floor = zdt.truncatedTo(ChronoUnit.DAYS);
        return zdt.equals(floor) ? floor : floor.plusDays(1);
    }

    private int sanitizeDays(int days) {
        if (days <= 0) return 7;
        return Math.min(days, MAX_DAYS);
    }

    private ZoneId safeZone(String tz) {
        if (!StringUtils.hasText(tz)) return ZoneId.of(DEFAULT_TZ);
        try { return ZoneId.of(tz.trim()); }
        catch (Exception e) { return ZoneId.of(DEFAULT_TZ); }
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); }
        catch (Exception e) { return 0L; }
    }

    // ============ Tenant Resolver ============
    public ObjectId resolveTenantId(Authentication auth, HttpServletRequest req) {
        if (auth == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");

        Object principal = auth.getPrincipal();

        try {
            Method m = principal.getClass().getMethod("getTenantId");
            Object val = m.invoke(principal);
            if (val instanceof ObjectId oid) return oid;
            if (val instanceof String s && ObjectId.isValid(s)) return new ObjectId(s);
        } catch (Exception ignored) {}

        if (principal instanceof Map<?, ?> map) {
            Object tid = map.get("tenantId");
            if (tid instanceof String s && ObjectId.isValid(s)) return new ObjectId(s);
        }

        String headerTenant = req.getHeader("X-Tenant");
        if (StringUtils.hasText(headerTenant) && ObjectId.isValid(headerTenant)) return new ObjectId(headerTenant);

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant_id_not_found_in_auth");
    }
}
