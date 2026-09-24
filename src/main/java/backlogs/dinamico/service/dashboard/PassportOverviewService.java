package backlogs.dinamico.service.dashboard;

import backlogs.dinamico.api.dto.passport.PassportSummaryResponse;
import backlogs.dinamico.api.dto.passport.PassportsByOfficeItem;
import backlogs.dinamico.api.dto.passport.PassportsByTypeItem;
import backlogs.dinamico.infra.cache.TimedCache;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PassportOverviewService {

    private final MongoTemplate mongoTemplate;

    private final TimedCache<PassportSummaryResponse> summaryCache =
            new TimedCache<>("passport-summary", java.time.Duration.ofSeconds(30));
    private final TimedCache<List<PassportsByOfficeItem>> byOfficeCache =
            new TimedCache<>("passport-by-office", java.time.Duration.ofSeconds(30));
    private final TimedCache<List<PassportsByTypeItem>> byTypeCache =
            new TimedCache<>("passport-by-type", java.time.Duration.ofSeconds(30));

    /**
     * SWITCH INMEDIATO:
     * ahora todo el dashboard de pasaportes se calcula desde log_events (esquema universal).
     */
    private static final String COLLECTION = "log_events";
    private static final String HINT_INDEX = "idx_tenant_system_time_v2";
    private static final int CURSOR_BATCH_SIZE = 1000;

    // Helpers para fechas por defecto
    public static Instant defaultFrom() {
        return LocalDate.now(ZoneOffset.UTC)
                .minusDays(30)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    public static Instant defaultTo() {
        return LocalDate.now(ZoneOffset.UTC)
                .plusDays(1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    /*
     * Resumen general en un rango de fechas.
     *
     * Mapeo universal:
     * - system -> system
     * - eventTime -> eventTime
     * - status -> status
     * - operationType -> eventType
     * - officeId -> location.locationId
     * - userId -> actor.actorId
     * - channel -> payload.channel  (recomendado)
     */
    public PassportSummaryResponse getSummary(
            String passportSystem,
            Instant from,
            Instant to,
            String officeId,
            String userId,
            String channel,
            String operationType,
            String status
    ) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant no resuelto en contexto");
        }

        String cacheKey = buildSummaryKey(tenantId, passportSystem, from, to, officeId, userId, channel, operationType, status);
        return summaryCache.get(cacheKey, () -> buildSummary(
                tenantId, passportSystem, from, to, officeId, userId, channel, operationType, status
        ));
    }

    private PassportSummaryResponse buildSummary(
            ObjectId tenantId,
            String passportSystem,
            Instant from,
            Instant to,
            String officeId,
            String userId,
            String channel,
            String operationType,
            String status
    ) {
        InstantRange range = resolveTimeRange(from, to);

        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("tenant_id").is(tenantId));
        criteriaList.add(Criteria.where("system").is(passportSystem));
        criteriaList.add(Criteria.where("eventTime").gte(range.from()).lt(range.to()));

        if (StringUtils.hasText(userId)) {
            criteriaList.add(Criteria.where("actor.actorId").is(userId));
        }

        if (StringUtils.hasText(channel)) {
            // canal viene dentro del payload en esquema universal
            criteriaList.add(Criteria.where("payload.channel").is(channel));
        }

        if (StringUtils.hasText(operationType)) {
            // operationType del dashboard pasaportes -> eventType universal
            criteriaList.add(Criteria.where("eventType").is(operationType));
        }

        if (StringUtils.hasText(status)) {
            String normalizedStatus = normalizeStatusFilter(status);
            criteriaList.add(Criteria.where("status").is(normalizedStatus));
        }

        Criteria baseCriteria = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));

        // Totales por status
        Aggregation totalsAgg = newAggregation(
                match(baseCriteria),
                project("status").andExclude("_id"),
                group("status").count().as("count")
        ).withOptions(buildAggregationOptions(HINT_INDEX));

        AggregationResults<Document> totalsResults =
                mongoTemplate.aggregate(totalsAgg, COLLECTION, Document.class);

        long emitidos = 0L, enTramite = 0L, rechazados = 0L, cancelados = 0L;

        for (Document d : totalsResults.getMappedResults()) {
            String dbStatus = d.getString("_id");
            long count = getCountAsLong(d);

            if ("EMITIDO".equalsIgnoreCase(dbStatus)) {
                emitidos = count;
            } else if (isEnTramite(dbStatus)) {
                enTramite = count;
            } else if ("RECHAZADO".equalsIgnoreCase(dbStatus)) {
                rechazados = count;
            } else if ("CANCELADO".equalsIgnoreCase(dbStatus)) {
                cancelados = count;
            }
        }

        // Por día
        Aggregation perDayAgg = newAggregation(
                match(baseCriteria),
                project("status", "eventTime").andExclude("_id"),
                project("status")
                        .andExpression("{ $dateToString: { date: \"$eventTime\", format: \"%Y-%m-%d\", timezone: \"UTC\" } }")
                        .as("fecha"),
                group("fecha", "status").count().as("count"),
                sort(Sort.Direction.ASC, "_id.fecha")
        ).withOptions(buildAggregationOptions(HINT_INDEX));

        AggregationResults<Document> perDayResults =
                mongoTemplate.aggregate(perDayAgg, COLLECTION, Document.class);

        List<PassportSummaryResponse.PorDia> perDayDtos =
                perDayResults.getMappedResults().stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                d -> d.get("_id", Document.class).getString("fecha"),
                                java.util.stream.Collectors.toList()
                        ))
                        .entrySet().stream()
                        .map(e -> {
                            String fecha = e.getKey();
                            long eEmit = 0L, eEnTram = 0L, eRech = 0L, eCanc = 0L;

                            for (Document d : e.getValue()) {
                                Document id = d.get("_id", Document.class);
                                String st = id.getString("status");
                                long c = getCountAsLong(d);

                                if ("EMITIDO".equalsIgnoreCase(st)) {
                                    eEmit = c;
                                } else if (isEnTramite(st)) {
                                    eEnTram = c;
                                } else if ("RECHAZADO".equalsIgnoreCase(st)) {
                                    eRech = c;
                                } else if ("CANCELADO".equalsIgnoreCase(st)) {
                                    eCanc = c;
                                }
                            }

                            return new PassportSummaryResponse.PorDia(
                                    fecha,
                                    eEmit,
                                    eEnTram,
                                    eRech,
                                    eCanc
                            );
                        })
                        .sorted((a, b) -> a.fecha().compareTo(b.fecha()))
                        .toList();

        return new PassportSummaryResponse(
                range.from(),
                range.to(),
                new PassportSummaryResponse.Totales(
                        emitidos,
                        enTramite,
                        rechazados,
                        cancelados
                ),
                perDayDtos
        );
    }

    private String buildSummaryKey(
            ObjectId tenantId,
            String passportSystem,
            Instant from,
            Instant to,
            String officeId,
            String userId,
            String channel,
            String operationType,
            String status
    ) {
        return tenantId + "|" + passportSystem + "|" + from + "|" + to + "|"
                + officeId + "|" + userId + "|" + channel + "|" + operationType + "|" + status;
    }

    /*
     * Por oficina
     * Mapeo:
     * - officeId/officeName => location.locationId / location.locationName
     */
    public List<PassportsByOfficeItem> getByOffice(String passportSystem, Instant from, Instant to) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant no resuelto en contexto");
        }

        String cacheKey = tenantId + "|" + passportSystem + "|" + from + "|" + to;
        return byOfficeCache.get(cacheKey, () -> buildByOffice(tenantId, passportSystem, from, to));
    }

    private List<PassportsByOfficeItem> buildByOffice(ObjectId tenantId, String passportSystem, Instant from, Instant to) {
        InstantRange range = resolveTimeRange(from, to);

        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where("tenant_id").is(tenantId));
        criteria.add(Criteria.where("system").is(passportSystem));
        criteria.add(Criteria.where("eventTime").gte(range.from()).lt(range.to()));

        Criteria baseCriteria = new Criteria().andOperator(criteria.toArray(new Criteria[0]));

        Aggregation agg = newAggregation(
                match(baseCriteria),
                project("status")
                        .and("location.locationId").as("officeId")
                        .and("location.locationName").as("officeName"),
                group("officeId", "officeName", "status").count().as("count"),
                sort(Sort.Direction.ASC, "_id.officeName")
        ).withOptions(buildAggregationOptions(HINT_INDEX));

        AggregationResults<Document> results =
                mongoTemplate.aggregate(agg, COLLECTION, Document.class);

        return results.getMappedResults().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        d -> d.get("_id", Document.class).getString("officeId"),
                        java.util.stream.Collectors.toList()
                ))
                .entrySet().stream()
                .map(e -> {
                    String officeId = e.getKey();
                    List<Document> docs = e.getValue();

                    Document firstId = docs.get(0).get("_id", Document.class);
                    String officeName = firstId.getString("officeName");

                    long emitidos = 0L, enTramite = 0L, rechazados = 0L, cancelados = 0L;

                    for (Document d : docs) {
                        Document id = d.get("_id", Document.class);
                        String st = id.getString("status");
                        long c = getCountAsLong(d);

                        if ("EMITIDO".equalsIgnoreCase(st)) {
                            emitidos = c;
                        } else if (isEnTramite(st)) {
                            enTramite = c;
                        } else if ("RECHAZADO".equalsIgnoreCase(st)) {
                            rechazados = c;
                        } else if ("CANCELADO".equalsIgnoreCase(st)) {
                            cancelados = c;
                        }
                    }

                    return new PassportsByOfficeItem(
                            officeId,
                            officeName,
                            emitidos,
                            enTramite,
                            rechazados,
                            cancelados
                    );
                })
                .sorted((a, b) -> Long.compare(b.emitidos(), a.emitidos()))
                .toList();
    }

    /*
     * Por tipo (operationType del dashboard -> eventType universal)
     */
    public List<PassportsByTypeItem> getByType(String passportSystem, Instant from, Instant to) {
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant no resuelto en contexto");
        }

        String cacheKey = tenantId + "|" + passportSystem + "|" + from + "|" + to;
        return byTypeCache.get(cacheKey, () -> buildByType(tenantId, passportSystem, from, to));
    }

    private List<PassportsByTypeItem> buildByType(ObjectId tenantId, String passportSystem, Instant from, Instant to) {
        InstantRange range = resolveTimeRange(from, to);

        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where("tenant_id").is(tenantId));
        criteria.add(Criteria.where("system").is(passportSystem));
        criteria.add(Criteria.where("eventTime").gte(range.from()).lte(range.to()));

        Criteria baseCriteria = new Criteria().andOperator(criteria.toArray(new Criteria[0]));

        Aggregation agg = newAggregation(
                match(baseCriteria),
                project("eventType", "status").andExclude("_id"),
                group("eventType", "status").count().as("count"),
                sort(Sort.Direction.ASC, "_id.eventType")
        ).withOptions(buildAggregationOptions(HINT_INDEX));

        AggregationResults<Document> results =
                mongoTemplate.aggregate(agg, COLLECTION, Document.class);

        return results.getMappedResults().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        d -> d.get("_id", Document.class).getString("eventType"),
                        java.util.stream.Collectors.toList()
                ))
                .entrySet().stream()
                .map(e -> {
                    String tipo = e.getKey();
                    List<Document> docs = e.getValue();

                    long emitidos = 0L, enTramite = 0L, rechazados = 0L, cancelados = 0L;

                    for (Document d : docs) {
                        Document id = d.get("_id", Document.class);
                        String st = id.getString("status");
                        long c = getCountAsLong(d);

                        if ("EMITIDO".equalsIgnoreCase(st)) {
                            emitidos = c;
                        } else if (isEnTramite(st)) {
                            enTramite = c;
                        } else if ("RECHAZADO".equalsIgnoreCase(st)) {
                            rechazados = c;
                        } else if ("CANCELADO".equalsIgnoreCase(st)) {
                            cancelados = c;
                        }
                    }

                    return new PassportsByTypeItem(
                            tipo,
                            emitidos,
                            enTramite,
                            rechazados,
                            cancelados
                    );
                })
                .sorted((a, b) -> Long.compare(b.emitidos(), a.emitidos()))
                .toList();
    }

    // Normalizar status ENTRAMITE
    private boolean isEnTramite(String status) {
        if (status == null) return false;
        String norm = status.replace("_", "").replace(" ", "").toUpperCase();
        return "ENTRAMITE".equals(norm) || "EN_TRAMITE".equals(norm);
    }

    private long getCountAsLong(Document d) {
        Number n = d.get("count", Number.class);
        return n != null ? n.longValue() : 0L;
    }

    private String normalizeStatusFilter(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase();
        if ("ENTRAMITE".equals(s) || "EN_TRAMITE".equals(s)) {
            return "EN_TRAMITE";
        }
        return s;
    }

    private InstantRange resolveTimeRange(Instant from, Instant to) {
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(24, java.time.temporal.ChronoUnit.HOURS);
        return new InstantRange(effectiveFrom, effectiveTo);
    }

    private AggregationOptions buildAggregationOptions(String hint) {
        return AggregationOptions.builder()
                .cursorBatchSize(CURSOR_BATCH_SIZE)
                .allowDiskUse(true)
                .hint(hint)
                .build();
    }

    private record InstantRange(Instant from, Instant to) {
    }
}
