package backlogs.dinamico.service.ai;

import backlogs.dinamico.service.ai.dto.HourlySummaryDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
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
public class HourlySummaryService {

    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static final int MAX_HOURS = 24 * 31; // 31 días
    private static final String DEFAULT_TZ = "America/Mexico_City";

    private final MongoTemplate mongoTemplate;

    @Value("${multitenant.log-collection:log_events}")
    private String logCollection;

    // Campos reales de tu LogEvent
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

    public HourlySummaryDto buildHourlySummary(ObjectId tenantId, int hours, String tz, Instant from, Instant to) {
        return buildHourlySummary(tenantId, hours, tz, from, to, null);
    }

    /**
     * Overload con filtro de sistema — usado por Eva cuando el usuario
     * tiene sistemas restringidos (SYSTEM_MANAGER, VIEWER con allowedSystems).
     */
    public HourlySummaryDto buildHourlySummary(ObjectId tenantId, int hours, String tz,
                                               Instant from, Instant to, String system) {

        int safeHours = sanitizeHours(hours);
        ZoneId zone = safeZone(tz);

        // --------- RANGO ALINEADO A HORAS (en TZ) ---------
        ZonedDateTime endExclusiveZdt = (to != null)
                ? ceilToHour(ZonedDateTime.ofInstant(to, zone))
                : ZonedDateTime.now(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1);

        ZonedDateTime startInclusiveZdt = (from != null)
                ? ZonedDateTime.ofInstant(from, zone).truncatedTo(ChronoUnit.HOURS)
                : endExclusiveZdt.minusHours(safeHours);

        // si vienen invertidos
        if (startInclusiveZdt.isAfter(endExclusiveZdt)) {
            ZonedDateTime tmp = startInclusiveZdt;
            startInclusiveZdt = endExclusiveZdt.minusHours(safeHours);
            endExclusiveZdt = ceilToHour(tmp);
        }

        // limita rango máximo (si alguien manda from/to enormes)
        long requested = ChronoUnit.HOURS.between(startInclusiveZdt.toInstant(), endExclusiveZdt.toInstant());
        if (requested > MAX_HOURS) {
            startInclusiveZdt = endExclusiveZdt.minusHours(MAX_HOURS);
        }

        Instant rangeFrom = startInclusiveZdt.toInstant();
        Instant rangeTo = endExclusiveZdt.toInstant();

        // 1) Totales por hora + severities
        List<Document> totals = aggregateTotalsByHour(tenantId, rangeFrom, rangeTo, zone.getId(), system);

        // fallback si sale vacío: tomar último eventTime real del tenant
        if (totals.isEmpty()) {
            Instant latest = findLatestEventTime(tenantId);
            if (latest != null) {
                ZonedDateTime latestEndExclusive = ceilToHour(ZonedDateTime.ofInstant(latest, zone));
                endExclusiveZdt = latestEndExclusive;
                startInclusiveZdt = latestEndExclusive.minusHours(safeHours);

                rangeFrom = startInclusiveZdt.toInstant();
                rangeTo = endExclusiveZdt.toInstant();

                totals = aggregateTotalsByHour(tenantId, rangeFrom, rangeTo, zone.getId(), system);
            }
        }

        Map<Date, Document> totalsByHour = totals.stream()
                .filter(d -> d.getDate("hourStart") != null)
                .collect(Collectors.toMap(
                        d -> d.getDate("hourStart"),
                        Function.identity(),
                        (a, b) -> a
                ));

        // 2) Tops por hora
        Map<Date, List<HourlySummaryDto.TopItem>> topSystems =
                aggregateTopByHour(tenantId, rangeFrom, rangeTo, zone.getId(), F_SYS, 5, system);

        Map<Date, List<HourlySummaryDto.TopItem>> topTypes =
                aggregateTopByHour(tenantId, rangeFrom, rangeTo, zone.getId(), F_TYPE, 5, system);

        Map<Date, List<HourlySummaryDto.TopItem>> topStatus =
                aggregateTopByHour(tenantId, rangeFrom, rangeTo, zone.getId(), F_STATUS, 5, system);

        Map<Date, List<HourlySummaryDto.TopItem>> topOutcome =
                aggregateTopByHour(tenantId, rangeFrom, rangeTo, zone.getId(), F_OUT, 5, system);

        Map<Date, List<HourlySummaryDto.TopError>> topErrors =
                aggregateTopErrorsByHour(tenantId, rangeFrom, rangeTo, zone.getId(), 5, system);

        // 3) Construir todas las horas del rango (para buckets vacíos)
        List<Date> hourStarts = buildHourStarts(startInclusiveZdt, endExclusiveZdt);

        HourlySummaryDto out = new HourlySummaryDto();
        out.tz = zone.getId();
        out.from = rangeFrom.toString();
        out.to = rangeTo.toString();
        out.hours = hourStarts.size();
        out.buckets = new ArrayList<>();

        for (Date hourStart : hourStarts) {
            Document t = totalsByHour.get(hourStart);

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

            HourlySummaryDto.Bucket b = new HourlySummaryDto.Bucket();
            b.hourStart = hourStart.toInstant().toString(); // UTC ISO
            b.hourStartLocal = hourStart.toInstant().atZone(zone).format(ISO_OFFSET);
            b.total = total;
            b.severities = severities;
            b.errorTotal = (t == null) ? 0L : toLong(t.get("errorTotal"));

            b.topSystems = topSystems.getOrDefault(hourStart, List.of());
            b.topEventTypes = topTypes.getOrDefault(hourStart, List.of());
            b.topStatus = topStatus.getOrDefault(hourStart, List.of());
            b.topOutcome = topOutcome.getOrDefault(hourStart, List.of());
            b.topErrors = topErrors.getOrDefault(hourStart, List.of());

            out.buckets.add(b);
        }

        return out;
    }

    // ===================== AGGREGATIONS =====================

    private List<Document> aggregateTotalsByHour(ObjectId tenantId, Instant from, Instant to, String tz) {
        return aggregateTotalsByHour(tenantId, from, to, tz, null);
    }

    private List<Document> aggregateTotalsByHour(ObjectId tenantId, Instant from, Instant to, String tz, String system) {

        // {$arrayToObject: {$ifNull: ["$sevPairs", []]}}
        var severitiesExpr = (org.springframework.data.mongodb.core.aggregation.AggregationExpression)
                ctx -> new Document("$arrayToObject",
                        new Document("$ifNull", List.of("$sevPairs", List.of()))
                );

        var errCond = (org.springframework.data.mongodb.core.aggregation.AggregationExpression)
                ctx -> new Document("$cond", List.of(
                        new Document("$ifNull", List.of("$isError", false)),
                        1, 0
                ));

        Aggregation agg = newAggregation(
                match(StringUtils.hasText(system)
                                ? new Criteria().andOperator(
                                Criteria.where(F_TENANT).is(tenantId),
                                Criteria.where(F_TIME).gte(Date.from(from)).lt(Date.from(to)),
                                Criteria.where(F_SEV).exists(true).ne(null).ne(""),
                                Criteria.where(F_SYS).is(system.trim())
                        )
                                : new Criteria().andOperator(
                                Criteria.where(F_TENANT).is(tenantId),
                                Criteria.where(F_TIME).gte(Date.from(from)).lt(Date.from(to)),
                                Criteria.where(F_SEV).exists(true).ne(null).ne("")
                        )
                ),

                addFields().addFieldWithValue("hour",
                        new Document("$dateTrunc",
                                new Document("date", "$" + F_TIME)
                                        .append("unit", "hour")
                                        .append("timezone", tz)
                        )
                ).build(),

                group(Fields.from(
                        Fields.field("hour", "$hour"),
                        Fields.field("sev", "$" + F_SEV)
                ))
                        .count().as("c")
                        .sum(errCond).as("errC"),

                sort(Sort.by(Sort.Direction.ASC, "_id.hour")
                        .and(Sort.by(Sort.Direction.DESC, "c"))),

                group("hour")
                        .sum("c").as("total")
                        .sum("errC").as("errorTotal")
                        .push(new Document("k", "$_id.sev").append("v", "$c")).as("sevPairs"),

                project()
                        .and("_id").as("hourStart")
                        .and("total").as("total")
                        .and("errorTotal").as("errorTotal")
                        .and(severitiesExpr).as("severities")
                        .andExclude("_id"),

                sort(Sort.by(Sort.Direction.ASC, "hourStart"))
        );

        return mongoTemplate.aggregate(agg, logCollection, Document.class).getMappedResults();
    }

    private Map<Date, List<HourlySummaryDto.TopItem>> aggregateTopByHour(
            ObjectId tenantId, Instant from, Instant to, String tz, String field, int limit
    ) {
        return aggregateTopByHour(tenantId, from, to, tz, field, limit, null);
    }

    private Map<Date, List<HourlySummaryDto.TopItem>> aggregateTopByHour(
            ObjectId tenantId, Instant from, Instant to, String tz, String field, int limit, String system
    ) {
        // Criterio base — siempre aplica
        Criteria timeCriteria = Criteria.where(F_TENANT).is(tenantId)
                .and(F_TIME).gte(Date.from(from)).lt(Date.from(to));

        // Criterio del campo a agregar — evitar duplicar si field == F_SYS
        Criteria fieldCriteria = Criteria.where(field).exists(true).ne(null).ne("");

        // Criterio de sistema — solo si se especificó Y el campo no es ya "system"
        Criteria baseCriteria;
        if (StringUtils.hasText(system) && !F_SYS.equals(field)) {
            // field != "system" → podemos agregar system sin conflicto
            baseCriteria = new Criteria().andOperator(
                    timeCriteria,
                    fieldCriteria,
                    Criteria.where(F_SYS).is(system.trim())
            );
        } else if (StringUtils.hasText(system) && F_SYS.equals(field)) {
            // field == "system" → el filtro IS el propio campo, no duplicar
            baseCriteria = new Criteria().andOperator(
                    timeCriteria,
                    Criteria.where(F_SYS).is(system.trim())
            );
        } else {
            baseCriteria = new Criteria().andOperator(timeCriteria, fieldCriteria);
        }

        Aggregation agg = newAggregation(
                match(baseCriteria),

                addFields().addFieldWithValue("hour",
                        new Document("$dateTrunc",
                                new Document("date", "$" + F_TIME)
                                        .append("unit", "hour")
                                        .append("timezone", tz)
                        )
                ).build(),

                group(Fields.from(
                        Fields.field("hour", "$hour"),
                        Fields.field("val", "$" + field)
                )).count().as("c"),

                sort(Sort.by(Sort.Direction.ASC, "_id.hour")
                        .and(Sort.by(Sort.Direction.DESC, "c"))),

                group("_id.hour")
                        .push(new Document("name", "$_id.val").append("count", "$c")).as("items"),

                project()
                        .and("_id").as("hour")
                        .and(ArrayOperators.Slice.sliceArrayOf("items").itemCount(limit)).as("items")
                        .andExclude("_id"),

                sort(Sort.by(Sort.Direction.ASC, "hour"))
        );

        List<Document> rows = mongoTemplate.aggregate(agg, logCollection, Document.class).getMappedResults();
        Map<Date, List<HourlySummaryDto.TopItem>> out = new HashMap<>();

        for (Document r : rows) {
            Date hour = r.getDate("hour");
            @SuppressWarnings("unchecked")
            List<Document> items = (List<Document>) r.get("items", List.class);

            List<HourlySummaryDto.TopItem> mapped = (items == null) ? List.of() :
                    items.stream()
                            .filter(d -> StringUtils.hasText(d.getString("name")))
                            .map(d -> new HourlySummaryDto.TopItem(
                                    d.getString("name"),
                                    toLong(d.get("count"))
                            ))
                            .toList();

            out.put(hour, mapped);
        }
        return out;
    }

    /**
     * Top errores por hora basado en message (solo severity ERROR/FATAL).
     */
    private Map<Date, List<HourlySummaryDto.TopError>> aggregateTopErrorsByHour(
            ObjectId tenantId, Instant from, Instant to, String tz, int limit
    ) {
        return aggregateTopErrorsByHour(tenantId, from, to, tz, limit, null);
    }

    private Map<Date, List<HourlySummaryDto.TopError>> aggregateTopErrorsByHour(
            ObjectId tenantId, Instant from, Instant to, String tz, int limit, String system
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);

        Criteria errorCriteria = StringUtils.hasText(system)
                ? new Criteria().andOperator(
                Criteria.where(F_TENANT).is(tenantId),
                Criteria.where(F_TIME).gte(Date.from(from)).lt(Date.from(to)),
                Criteria.where(F_IS_ERROR).is(true),
                Criteria.where(F_MSG_KEY).exists(true).ne(null).ne(""),
                Criteria.where(F_SYS).is(system.trim())
        )
                : new Criteria().andOperator(
                Criteria.where(F_TENANT).is(tenantId),
                Criteria.where(F_TIME).gte(Date.from(from)).lt(Date.from(to)),
                Criteria.where(F_IS_ERROR).is(true),
                Criteria.where(F_MSG_KEY).exists(true).ne(null).ne("")
        );

        Aggregation agg = newAggregation(
                match(errorCriteria),

                addFields().addFieldWithValue("hour",
                        new Document("$dateTrunc",
                                new Document("date", "$" + F_TIME)
                                        .append("unit", "hour")
                                        .append("timezone", tz)
                        )
                ).build(),

                // group1
                group(Fields.from(
                        Fields.field("hour", "$hour"),
                        Fields.field("key", "$" + F_MSG_KEY)
                )).count().as("c"),

                sort(Sort.by(Sort.Direction.ASC, "_id.hour")
                        .and(Sort.by(Sort.Direction.DESC, "c"))),

                group("_id.hour")
                        .push(new Document("key", "$_id.key").append("count", "$c"))
                        .as("items"),

                project()
                        .and("_id").as("hour")
                        .and(ArrayOperators.Slice.sliceArrayOf("items").itemCount(limit)).as("items")
                        .andExclude("_id"),

                sort(Sort.by(Sort.Direction.ASC, "hour"))
        );

        List<Document> rows = mongoTemplate.aggregate(agg, logCollection, Document.class).getMappedResults();
        Map<Date, List<HourlySummaryDto.TopError>> out = new HashMap<>();

        for (Document r : rows) {
            Date hour = r.getDate("hour");
            @SuppressWarnings("unchecked")
            List<Document> items = (List<Document>) r.get("items", List.class);

            List<HourlySummaryDto.TopError> mapped = (items == null) ? List.of() :
                    items.stream().map(d ->
                            new HourlySummaryDto.TopError(
                                    Objects.toString(d.get("key"), ""),
                                    toLong(d.get("count"))
                            )
                    ).toList();

            out.put(hour, mapped);
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
        return (dt != null) ? dt.toInstant() : null;
    }

    private List<Date> buildHourStarts(ZonedDateTime startInclusive, ZonedDateTime endExclusive) {
        ZonedDateTime cursor = startInclusive.truncatedTo(ChronoUnit.HOURS);

        List<Date> out = new ArrayList<>();
        while (cursor.isBefore(endExclusive)) {
            out.add(Date.from(cursor.toInstant()));
            cursor = cursor.plusHours(1);
        }
        return out;
    }

    private static long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(value)); }
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

    // ----------------------- HELPERS -------------------
    private static ZonedDateTime ceilToHour(ZonedDateTime zdt) {
        ZonedDateTime floor = zdt.truncatedTo(ChronoUnit.HOURS);
        return zdt.equals(floor) ? floor : floor.plusHours(1);
    }

    private int sanitizeHours(int hours) {
        if (hours <= 0) return 24;
        return Math.min(hours, MAX_HOURS);
    }

    private ZoneId safeZone(String tz) {
        if (!StringUtils.hasText(tz)) return ZoneId.of(DEFAULT_TZ);
        try { return ZoneId.of(tz); }
        catch (Exception e) { return ZoneId.of(DEFAULT_TZ); }
    }
}