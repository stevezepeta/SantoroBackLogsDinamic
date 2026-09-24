package backlogs.dinamico.service.dashboard;

import backlogs.dinamico.api.dto.dashboard.DashboardDistributionItemDto;
import backlogs.dinamico.api.dto.dashboard.DashboardRecentEventDto;
import backlogs.dinamico.api.dto.dashboard.DashboardRecentEventsResponseDto;
import backlogs.dinamico.api.dto.dashboard.DashboardSeriesItemDto;
import backlogs.dinamico.api.dto.dashboard.DashboardStatsDto;
import backlogs.dinamico.api.dto.dashboard.DashboardTopFunctionDto;
import backlogs.dinamico.api.dto.dashboard.DashboardTopFunctionsResponseDto;
import backlogs.dinamico.config.CacheConfig;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.FacetOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Servicio de agregaciones para dashboards de logs.
 *
 * <p>Optimizado para colecciones con millones de documentos:</p>
 * <ul>
 *   <li>Se fuerza el uso del índice compuesto {@code idx_tenant_system_time_v2}.</li>
 *   <li>Primera etapa obligatoria: {@code $match} estricto por tenant, sistema y rango de
 *       tiempo. Si el frontend no envía fechas se fuerzan las últimas 24 h por defecto.</li>
 *   <li>Todas las agregaciones incluyen un rango de fecha acotado para evitar escaneos
 *       completos de la colección.</li>
 *   <li>Segunda etapa: {@code $project} que conserva solo los campos necesarios y descarta payload/meta/remoteConnection.</li>
 *   <li>Tercera etapa: {@code $limit} en pipelines de ranking.</li>
 *   <li>{@code allowDiskUse(true)} y batch size de 1000 para grandes volúmenes.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardLogService {

    private static final String COLLECTION = "log_events";
    private static final String HINT_INDEX = "idx_tenant_system_time_v2";
    private static final int DEFAULT_TOP_LIMIT = 20;
    private static final int CURSOR_BATCH_SIZE = 1000;

    private final MongoTemplate mongoTemplate;

    /**
     * Estadísticas operativas globales de un sistema.
     *
     * <p>Si {@code from} y {@code to} son null, se fuerzan las últimas 24 h para evitar
     * escaneos completos de la colección.</p>
     *
     * <p>Utiliza una sola tubería {@code $facet} para obtener en una pasada:
     * conteos del rango y eventos de hoy. El primer stage es siempre {@code $match}
     * con tenant, sistema y rango de tiempo para usar el índice compuesto.</p>
     *
     * @param system sistema a analizar (ej. CITA_GUYANA)
     * @param from    inicio del rango (inclusive); si es null se usan las últimas 24 h
     * @param to      fin del rango (inclusive); si es null se usa el instante actual
     */
    @Cacheable(value = CacheConfig.SYSTEM_STATS_CACHE,
            key = "T(backlogs.dinamico.tenant.TenantContext).requireTenantId().toHexString() + '_' + #system + '_' + #from + '_' + #to")
    public DashboardStatsDto getStats(String system, Instant from, Instant to) {
        ObjectId tenantId = TenantContext.requireTenantId();
        String normalizedSystem = normalizeSystem(system);
        InstantRange range = resolveOperationalTimeRange(from, to);
        InstantRange todayRange = resolveDashboardRange("TODAY");

        // Match inicial con tenant + system + eventTime para usar idx_tenant_system_time_v2
        Criteria baseCriteria = Criteria.where("tenant_id").is(tenantId)
                .and("system").regex("^" + Pattern.quote(normalizedSystem) + "$", "i")
                .and("eventTime").gte(range.from()).lte(range.to());

        // Facet de conteos del rango solicitado
        List<AggregationOperation> countsOps = new ArrayList<>();
        countsOps.add(group()
                .count().as("totalEvents")
                .sum(ConditionalOperators.when(isErrorCriteria()).then(1).otherwise(0)).as("errorEvents")
                .sum(ConditionalOperators.when(isSuccessCriteria()).then(1).otherwise(0)).as("successEvents")
                .sum(ConditionalOperators.when(isWarningCriteria()).then(1).otherwise(0)).as("warningEvents")
                .sum(ConditionalOperators.when(isInfoCriteria()).then(1).otherwise(0)).as("infoEvents"));

        FacetOperation facet = new FacetOperation()
                .and(countsOps.toArray(new AggregationOperation[0])).as("counts")
                .and(
                        match(Criteria.where("eventTime").gte(todayRange.from()).lte(todayRange.to())),
                        group().count().as("todayEvents")
                ).as("today");

        Aggregation aggregation = newAggregation(
                match(baseCriteria),
                project("status", "outcome", "severity", "isError", "eventTime").andExclude("_id"),
                facet
        ).withOptions(buildOptions(HINT_INDEX));

        log.debug("[getStats] system={} range={} aggregation={}", normalizedSystem, range, aggregation);

        Document doc;
        try {
            AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, COLLECTION, Document.class);
            doc = results.getUniqueMappedResult();
        } catch (Exception e) {
            log.warn("[getStats] Query de dashboard excedió el tiempo límite o falló: {}", e.getMessage());
            return DashboardStatsDto.builder()
                    .system(normalizedSystem)
                    .from(range.from())
                    .to(range.to())
                    .totalEvents(0L)
                    .errorCount(0L)
                    .successCount(0L)
                    .warningEvents(0L)
                    .infoEvents(0L)
                    .errorRate(0.0)
                    .healthStatus("INACTIVE")
                    .totalHistoricalLogs(0L)
                    .todayEvents(0L)
                    .build();
        }

        Document countsDoc = getFacetDocument(doc, "counts");
        Document todayDoc = getFacetDocument(doc, "today");

        long totalEvents = getLong(countsDoc, "totalEvents");
        long errorCount = getLong(countsDoc, "errorEvents");
        long successCount = getLong(countsDoc, "successEvents");
        long warningEvents = getLong(countsDoc, "warningEvents");
        long infoEvents = getLong(countsDoc, "infoEvents");
        double errorRate = totalEvents > 0 ? Math.round((errorCount * 10000.0 / totalEvents)) / 100.0 : 0.0;
        String healthStatus = determineHealthStatus(totalEvents, errorRate);

        // El rango siempre está acotado, por lo que el histórico coincide con el rango consultado.
        long totalHistoricalLogs = totalEvents;
        long todayEvents = getLong(todayDoc, "todayEvents");

        return DashboardStatsDto.builder()
                .system(normalizedSystem)
                .from(range.from())
                .to(range.to())
                .totalEvents(totalEvents)
                .errorCount(errorCount)
                .successCount(successCount)
                .warningEvents(warningEvents)
                .infoEvents(infoEvents)
                .errorRate(errorRate)
                .healthStatus(healthStatus)
                .totalHistoricalLogs(totalHistoricalLogs)
                .todayEvents(todayEvents)
                .build();
    }

    /**
     * Serie temporal diaria de eventos de un sistema.
     * Si no se envía rango se usan las últimas 24 h por defecto.
     *
     * @param system sistema a analizar (ej. CITA_GUYANA)
     * @param from   inicio del rango (inclusive); si es null se usan las últimas 24 h
     * @param to     fin del rango (inclusive); si es null se usa el instante actual
     */
    public List<DashboardSeriesItemDto> getSeries(String system, Instant from, Instant to) {
        ObjectId tenantId = TenantContext.requireTenantId();
        String normalizedSystem = normalizeSystem(system);
        InstantRange range = resolveAnalyticalTimeRange(from, to);

        Criteria matchCriteria = buildBaseMatchCriteria(tenantId, normalizedSystem, range);

        Aggregation aggregation = newAggregation(
                match(matchCriteria),
                project("eventTime")
                        .and(hybridEventTypeExpression()).as("hybridEventType")
                        .and(ConditionalOperators.when(isErrorCriteria()).then(1).otherwise(0)).as("isError"),
                project("hybridEventType", "isError")
                        .andExpression("{ $dateToString: { date: '$eventTime', format: '%Y-%m-%d', timezone: 'UTC' } }")
                        .as("period"),
                group("period", "hybridEventType")
                        .count().as("count")
                        .sum("isError").as("errors"),
                sort(Sort.Direction.ASC, "_id.period")
        ).withOptions(buildOptions(HINT_INDEX));

        log.debug("[getSeries] system={} range={} aggregation={}", normalizedSystem, range, aggregation);

        List<Document> mappedResults;
        try {
            AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, COLLECTION, Document.class);
            mappedResults = results.getMappedResults();
        } catch (Exception e) {
            log.warn("[getSeries] Query de dashboard excedió el tiempo límite o falló: {}", e.getMessage());
            return Collections.emptyList();
        }

        return mappedResults.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        d -> d.get("_id", Document.class).getString("period"),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ))
                .entrySet().stream()
                .map(e -> {
                    String period = e.getKey();
                    long total = 0;
                    long errors = 0;
                    java.util.Map<String, Long> byType = new java.util.LinkedHashMap<>();
                    for (Document d : e.getValue()) {
                        Document id = d.get("_id", Document.class);
                        String eventType = id.getString("hybridEventType");
                        if (eventType == null) eventType = "UNKNOWN";
                        long count = getLong(d, "count");
                        long errorCount = getLong(d, "errors");
                        total += count;
                        errors += errorCount;
                        byType.merge(eventType, count, Long::sum);
                    }
                    return DashboardSeriesItemDto.builder()
                            .period(period)
                            .totalEvents(total)
                            .errorEvents(errors)
                            .byEventType(byType)
                            .build();
                })
                .sorted((a, b) -> a.getPeriod().compareTo(b.getPeriod()))
                .toList();
    }

    /**
     * Funciones/eventos más usados de un sistema.
     * <p>Soporta rangos: {@code TODAY} (24 h), {@code WEEK} (7 días, default) y
     * {@code ALL} (24 h, para evitar escaneos completos). El campo {@code totalProcessedLogs} refleja
     * el total de documentos del sistema dentro del rango solicitado.</p>
     *
     * @param system sistema a analizar (ej. CITA_GUYANA)
     * @param range  TODAY | WEEK | ALL; por defecto WEEK
     * @param limit  máximo de resultados; por defecto 20
     */
    public DashboardTopFunctionsResponseDto getTopFunctions(String system, String range, int limit) {
        ObjectId tenantId = TenantContext.requireTenantId();
        String normalizedSystem = normalizeSystem(system);
        InstantRange instantRange = resolveDashboardRange(range);
        int effectiveLimit = limit > 0 ? limit : DEFAULT_TOP_LIMIT;

        Criteria matchCriteria = buildBaseMatchCriteria(tenantId, normalizedSystem, instantRange);

        FacetOperation facet = new FacetOperation()
                .and(group().count().as("totalProcessedLogs")).as("total")
                .and(
                        project().and(hybridEventTypeExpression()).as("hybridEventType"),
                        group("hybridEventType").count().as("count"),
                        sort(Sort.Direction.DESC, "count"),
                        limit(effectiveLimit)
                ).as("topFunctions");

        Aggregation aggregation = newAggregation(
                match(matchCriteria),
                facet
        ).withOptions(buildOptions(HINT_INDEX));

        log.debug("[getTopFunctions] system={} range={} limit={} aggregation={}",
                normalizedSystem, range, effectiveLimit, aggregation);

        Document doc;
        try {
            AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, COLLECTION, Document.class);
            doc = results.getUniqueMappedResult();
        } catch (Exception e) {
            log.warn("[getTopFunctions] Query de dashboard excedió el tiempo límite o falló: {}", e.getMessage());
            return DashboardTopFunctionsResponseDto.builder()
                    .totalProcessedLogs(0L)
                    .functions(Collections.emptyList())
                    .build();
        }

        Document totalDoc = getFacetDocument(doc, "total");
        List<Document> topFunctionDocs = getFacetList(doc, "topFunctions");

        long totalProcessedLogs = getLong(totalDoc, "totalProcessedLogs");

        List<DashboardTopFunctionDto> topFunctions = new ArrayList<>();
        long rank = 1;
        for (Document itemDoc : topFunctionDocs) {
            topFunctions.add(DashboardTopFunctionDto.builder()
                    .eventType(itemDoc.getString("_id"))
                    .count(getLong(itemDoc, "count"))
                    .rank(rank++)
                    .build());
        }

        return DashboardTopFunctionsResponseDto.builder()
                .totalProcessedLogs(totalProcessedLogs)
                .functions(topFunctions)
                .build();
    }

    /**
     * Cobertura de funciones: distribución de tipos de evento híbridos.
     * <p>Soporta rangos: {@code TODAY} (24 h), {@code WEEK} (7 días, default) y
     * {@code ALL} (24 h, para evitar escaneos completos).</p>
     *
     * @param system sistema a analizar
     * @param range  TODAY | WEEK | ALL; por defecto WEEK
     * @param limit  máximo de resultados; por defecto 20
     */
    public List<DashboardDistributionItemDto> getCoverage(String system, String range, int limit) {
        return aggregateHybridDistribution(system, resolveDashboardRange(range), limit > 0 ? limit : DEFAULT_TOP_LIMIT);
    }

    /**
     * Origen de tráfico: distribución por país de ubicación.
     * Si no se envía rango, se usan las últimas 24 h por defecto.
     */
    public List<DashboardDistributionItemDto> getTrafficOrigin(String system, Instant from, Instant to, int limit) {
        return aggregateDistribution(system, resolveAnalyticalTimeRange(from, to), "location.country", limit > 0 ? limit : DEFAULT_TOP_LIMIT);
    }

    /**
     * Top oficinas: distribución por nombre de ubicación.
     * Si no se envía rango, se usan las últimas 24 h por defecto.
     */
    public List<DashboardDistributionItemDto> getOffices(String system, Instant from, Instant to, int limit) {
        return aggregateDistribution(system, resolveAnalyticalTimeRange(from, to), "location.name", limit > 0 ? limit : DEFAULT_TOP_LIMIT);
    }

    /**
     * Distribución por severidad.
     * <p>Soporta rangos: {@code TODAY} (24 h), {@code WEEK} (7 días, default) y
     * {@code ALL} (24 h, para evitar escaneos completos).</p>
     *
     * @param system sistema a analizar
     * @param range  TODAY | WEEK | ALL; por defecto WEEK
     * @param limit  máximo de resultados; por defecto 20
     */
    public List<DashboardDistributionItemDto> getSeverity(String system, String range, int limit) {
        return aggregateDistribution(system, resolveDashboardRange(range), "severity", limit > 0 ? limit : DEFAULT_TOP_LIMIT);
    }

    /**
     * Actividad de hoy: últimos eventos del sistema dentro del rango operacional
     * de 24 h. Devuelve tanto el listado paginado como el conteo total
     * ({@code totalElements}), alineado con {@code todayEvents} de /stats.
     *
     * @param system sistema a analizar
     * @param limit  cantidad de eventos a retornar; por defecto 10
     */
    public DashboardRecentEventsResponseDto getRecentEvents(String system, int limit) {
        ObjectId tenantId = TenantContext.requireTenantId();
        String normalizedSystem = normalizeSystem(system);
        int effectiveLimit = limit > 0 ? limit : 10;

        InstantRange todayRange = resolveDashboardRange("TODAY");
        Criteria criteria = buildBaseMatchCriteria(tenantId, normalizedSystem, todayRange);

        long totalElements;
        List<Document> docs;
        try {
            totalElements = countEvents(tenantId, normalizedSystem, todayRange);

            org.springframework.data.mongodb.core.query.Query query =
                    new org.springframework.data.mongodb.core.query.Query(criteria)
                            .with(Sort.by(Sort.Direction.DESC, "eventTime"))
                            .limit(effectiveLimit);

            docs = mongoTemplate.find(query, Document.class, COLLECTION);
        } catch (Exception e) {
            log.warn("[getRecentEvents] Query de dashboard excedió el tiempo límite o falló: {}", e.getMessage());
            return DashboardRecentEventsResponseDto.builder()
                    .totalElements(0L)
                    .events(Collections.emptyList())
                    .build();
        }

        log.debug("[getRecentEvents] system={} limit={} totalElements={} found={}",
                normalizedSystem, effectiveLimit, totalElements, docs.size());

        List<DashboardRecentEventDto> events = docs.stream()
                .map(doc -> DashboardRecentEventDto.builder()
                        .id(doc.getObjectId("_id") != null ? doc.getObjectId("_id").toHexString() : null)
                        .eventTime(doc.getDate("eventTime") != null ? doc.getDate("eventTime").toInstant() : null)
                        .system(normalizedSystem)
                        .eventType(doc.getString("eventType"))
                        .status(doc.getString("status"))
                        .severity(doc.getString("severity"))
                        .message(doc.getString("message"))
                        .caseId(doc.getString("caseId"))
                        .actorName(getNestedString(doc, "actor", "fullName"))
                        .locationName(getNestedString(doc, "location", "name"))
                        .build())
                .toList();

        return DashboardRecentEventsResponseDto.builder()
                .totalElements(totalElements)
                .events(events)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Criteria buildBaseMatchCriteria(ObjectId tenantId, String system, InstantRange range) {
        Criteria c = Criteria.where("tenant_id").is(tenantId)
                .and("system").regex("^" + Pattern.quote(system) + "$", "i");
        if (range != null) {
            c = c.and("eventTime").gte(range.from()).lte(range.to());
        }
        return c;
    }

    private long countEvents(ObjectId tenantId, String system, InstantRange range) {
        Criteria criteria = buildBaseMatchCriteria(tenantId, system, range);
        return mongoTemplate.count(
                new org.springframework.data.mongodb.core.query.Query(criteria), COLLECTION);
    }

    private InstantRange resolveOperationalTimeRange(Instant from, Instant to) {
        // Si no llegan fechas se fuerzan las últimas 24 h para evitar escaneos completos
        // de la colección y garantizar el uso del índice compuesto.
        // Si llega al menos una fecha se respeta el rango solicitado.
        // Se trunca a segundos para evitar desajustes por redondeo.
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        if (from == null && to == null) {
            return new InstantRange(now.minus(24, ChronoUnit.HOURS), now.plus(6, ChronoUnit.HOURS));
        }
        Instant effectiveTo = to != null ? to.truncatedTo(ChronoUnit.SECONDS) : now.plus(6, ChronoUnit.HOURS);
        Instant effectiveFrom = from != null ? from.truncatedTo(ChronoUnit.SECONDS) : Instant.EPOCH;
        return new InstantRange(effectiveFrom, effectiveTo);
    }

    /**
     * Resuelve el rango para gráficas analíticas: TODAY, WEEK (default), ALL.
     * ALL se traduce a 24 h para evitar escaneos completos de la colección.
     */
    private InstantRange resolveDashboardRange(String range) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        if (range == null) {
            return new InstantRange(now.minus(7, ChronoUnit.DAYS), now.plus(6, ChronoUnit.HOURS));
        }
        return switch (range.toUpperCase()) {
            case "TODAY" -> new InstantRange(now.minus(24, ChronoUnit.HOURS), now.plus(6, ChronoUnit.HOURS));
            case "ALL"   -> new InstantRange(now.minus(24, ChronoUnit.HOURS), now.plus(6, ChronoUnit.HOURS));
            case "WEEK"  -> new InstantRange(now.minus(7, ChronoUnit.DAYS), now.plus(6, ChronoUnit.HOURS));
            default      -> new InstantRange(now.minus(7, ChronoUnit.DAYS), now.plus(6, ChronoUnit.HOURS));
        };
    }

    private InstantRange resolveAnalyticalTimeRange(Instant from, Instant to) {
        // Si no llegan fechas se fuerzan las últimas 24 h para evitar escaneos completos.
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        if (from == null && to == null) {
            return new InstantRange(now.minus(24, ChronoUnit.HOURS), now.plus(6, ChronoUnit.HOURS));
        }
        Instant effectiveTo = to != null ? to : now.plus(6, ChronoUnit.HOURS);
        Instant effectiveFrom = from != null ? from : Instant.EPOCH;
        return new InstantRange(effectiveFrom, effectiveTo);
    }

    private List<DashboardDistributionItemDto> aggregateDistribution(
            String system, InstantRange range, String field, int limit) {

        ObjectId tenantId = TenantContext.requireTenantId();
        String normalizedSystem = normalizeSystem(system);
        Criteria matchCriteria = buildBaseMatchCriteria(tenantId, normalizedSystem, range);

        long total;
        List<Document> mappedResults;
        try {
            total = mongoTemplate.count(
                    new org.springframework.data.mongodb.core.query.Query(matchCriteria).withHint(HINT_INDEX), COLLECTION);

            Aggregation aggregation = newAggregation(
                    match(matchCriteria),
                    project(field).andExclude("_id"),
                    group(field).count().as("count"),
                    sort(Sort.Direction.DESC, "count"),
                    limit(limit)
            ).withOptions(buildOptions(HINT_INDEX));

            log.debug("[aggregateDistribution] field={} system={} range={} aggregation={}",
                    field, normalizedSystem, range, aggregation);

            AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, COLLECTION, Document.class);
            mappedResults = results.getMappedResults();
        } catch (Exception e) {
            log.warn("[aggregateDistribution] Query de dashboard excedió el tiempo límite o falló: {}", e.getMessage());
            return Collections.emptyList();
        }

        return mappedResults.stream()
                .map(doc -> {
                    String value = doc.getString("_id");
                    if (value == null) value = "N/A";
                    long count = getLong(doc, "count");
                    double percentage = total > 0 ? Math.round((count * 1000.0 / total)) / 10.0 : 0.0;
                    return DashboardDistributionItemDto.builder()
                            .value(value)
                            .count(count)
                            .percentage(percentage)
                            .build();
                })
                .toList();
    }

    private List<DashboardDistributionItemDto> aggregateHybridDistribution(
            String system, InstantRange range, int limit) {

        ObjectId tenantId = TenantContext.requireTenantId();
        String normalizedSystem = normalizeSystem(system);
        Criteria matchCriteria = buildBaseMatchCriteria(tenantId, normalizedSystem, range);

        long total;
        List<Document> mappedResults;
        try {
            total = mongoTemplate.count(
                    new org.springframework.data.mongodb.core.query.Query(matchCriteria).withHint(HINT_INDEX), COLLECTION);

            Aggregation aggregation = newAggregation(
                    match(matchCriteria),
                    project().and(hybridEventTypeExpression()).as("hybridEventType"),
                    group("hybridEventType").count().as("count"),
                    sort(Sort.Direction.DESC, "count"),
                    limit(limit)
            ).withOptions(buildOptions(HINT_INDEX));

            log.debug("[aggregateHybridDistribution] system={} range={} aggregation={}",
                    normalizedSystem, range, aggregation);

            AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, COLLECTION, Document.class);
            mappedResults = results.getMappedResults();
        } catch (Exception e) {
            log.warn("[aggregateHybridDistribution] Query de dashboard excedió el tiempo límite o falló: {}", e.getMessage());
            return Collections.emptyList();
        }

        return mappedResults.stream()
                .map(doc -> {
                    String value = doc.getString("_id");
                    if (value == null) value = "UNKNOWN";
                    long count = getLong(doc, "count");
                    double percentage = total > 0 ? Math.round((count * 1000.0 / total)) / 10.0 : 0.0;
                    return DashboardDistributionItemDto.builder()
                            .value(value)
                            .count(count)
                            .percentage(percentage)
                            .build();
                })
                .toList();
    }

    /**
     * Expresión híbrida para agrupar logs de infraestructura/workstations:
     * eventType → eventCode → messageKey → "UNKNOWN".
     */
    private AggregationExpression hybridEventTypeExpression() {
        return new AggregationExpression() {
            @Override
            public Document toDocument(AggregationOperationContext context) {
                return new Document("$ifNull", List.of("$eventType", "$eventCode", "$messageKey", "UNKNOWN"));
            }
        };
    }

    private AggregationOptions buildOptions(String hint) {
        return AggregationOptions.builder()
                .cursorBatchSize(CURSOR_BATCH_SIZE)
                .allowDiskUse(true)
                .hint(hint)
                .maxTime(Duration.ofMillis(3000))
                .build();
    }

    private Criteria isErrorCriteria() {
        return new Criteria().orOperator(
                Criteria.where("isError").is(true),
                Criteria.where("status").in("ERROR", "FAIL", "FAILURE", "KO", "REJECTED"),
                Criteria.where("outcome").in("FAILURE", "ERROR", "FAILED"),
                Criteria.where("severity").in("ERROR", "CRITICAL", "FATAL")
        );
    }

    private Criteria isSuccessCriteria() {
        return new Criteria().orOperator(
                Criteria.where("status").in("SUCCESS", "OK", "COMPLETED"),
                Criteria.where("outcome").in("SUCCESS", "OK")
        );
    }

    private Criteria isWarningCriteria() {
        return new Criteria().orOperator(
                Criteria.where("status").is("WARNING"),
                Criteria.where("severity").is("WARNING")
        );
    }

    private Criteria isInfoCriteria() {
        return new Criteria().orOperator(
                Criteria.where("status").is("INFO"),
                Criteria.where("severity").is("INFO")
        );
    }

    private boolean isErrorStatus(String status) {
        if (status == null) return false;
        String normalized = status.toUpperCase();
        return normalized.equals("ERROR")
                || normalized.equals("FAIL")
                || normalized.equals("FAILED")
                || normalized.equals("FAILURE")
                || normalized.equals("KO")
                || normalized.equals("REJECTED");
    }

    private long getLong(Document doc, String field) {
        if (doc == null) return 0L;
        Number value = doc.get(field, Number.class);
        return value != null ? value.longValue() : 0L;
    }

    private Document getFacetDocument(Document doc, String facetName) {
        if (doc == null) return null;
        Object facet = doc.get(facetName);
        if (facet instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Document) return (Document) first;
        }
        if (facet instanceof Document) return (Document) facet;
        return null;
    }

    private List<Document> getFacetList(Document doc, String facetName) {
        if (doc == null) return List.of();
        Object facet = doc.get(facetName);
        if (facet instanceof List<?> list) {
            return list.stream()
                    .filter(Document.class::isInstance)
                    .map(Document.class::cast)
                    .toList();
        }
        return List.of();
    }

    private String getNestedString(Document doc, String parent, String child) {
        if (doc == null) return null;
        Document nested = doc.get(parent, Document.class);
        return nested != null ? nested.getString(child) : null;
    }

    private String normalizeSystem(String system) {
        if (!StringUtils.hasText(system)) {
            throw new IllegalArgumentException("system is required");
        }
        return system.trim().toUpperCase();
    }

    private String determineHealthStatus(long totalEvents, double errorRate) {
        if (totalEvents == 0) return "INACTIVE";
        if (errorRate > 10.0) return "CRITICAL";
        if (errorRate > 0.0) return "WARNING";
        return "STABLE";
    }

    private record InstantRange(Instant from, Instant to) {
        @Override
        public String toString() {
            return from + " -> " + to;
        }
    }
}
