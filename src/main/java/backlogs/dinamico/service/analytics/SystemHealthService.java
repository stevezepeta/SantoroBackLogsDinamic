package backlogs.dinamico.service.analytics;

import backlogs.dinamico.api.dto.catalog.ExecutiveSummaryDto;
import backlogs.dinamico.api.dto.catalog.SystemHealthDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemHealthService {

    private final MongoTemplate mongoTemplate;
    private static final String COLLECTION = "log_events";
    private static final String HINT_INDEX = "idx_tenant_system_time_v2";
    private static final int CURSOR_BATCH_SIZE = 1000;

    public List<SystemHealthDto> getSystemsHealth(ObjectId tenantId, Instant from, Instant to) {
        if (tenantId == null) {
            log.warn("[getSystemsHealth] tenantId es requerido");
            return Collections.emptyList();
        }

        // 1. Sistemas dinámicos que pertenecen EXCLUSIVAMENTE a este tenant (sin filtro de fechas)
        List<String> tenantSystems = getTenantSystems(tenantId);

        if (tenantSystems.isEmpty()) {
            log.debug("[getSystemsHealth] tenantId={} no tiene sistemas registrados", tenantId);
            return Collections.emptyList();
        }

        // 2. Manejo defensivo de fechas (default a últimas 24h si from es nulo)
        Instant effectiveTo = (to != null) ? to : Instant.now();
        Instant effectiveFrom = (from != null) ? from : Instant.now().minus(24, ChronoUnit.HOURS);

        try {
            // 3. Filtro $match híbrido por tenant, sistema y rango de fechas
            Criteria criteria = buildTenantCriteria(tenantId)
                    .and("system").in(tenantSystems)
                    .and("eventTime").gte(Date.from(effectiveFrom)).lte(Date.from(effectiveTo));

            Aggregation agg = Aggregation.newAggregation(
                    Aggregation.match(criteria),
                    Aggregation.group("system")
                            .count().as("totalEvents")
                            .sum(ConditionalOperators.when(Criteria.where("isError").is(true)).then(1).otherwise(0)).as("errorEvents")
            ).withOptions(buildOptions(HINT_INDEX));

            AggregationResults<Document> results = mongoTemplate.aggregate(agg, COLLECTION, Document.class);

            Map<String, Document> resultMap = new HashMap<>();
            for (Document doc : results.getMappedResults()) {
                Object idObj = doc.get("_id");
                if (idObj != null && !idObj.toString().isBlank()) {
                    resultMap.put(idObj.toString().toUpperCase(Locale.ROOT), doc);
                }
            }

            // 4. Retornar SOLO los sistemas descubiertos para este tenant
            List<SystemHealthDto> dtos = new ArrayList<>();
            for (String sysCode : tenantSystems) {
                Document stats = resultMap.get(sysCode.toUpperCase(Locale.ROOT));

                long total = stats != null ? getLongValue(stats.get("totalEvents")) : 0L;
                long errors = stats != null ? getLongValue(stats.get("errorEvents")) : 0L;

                double rate = total > 0 ? ((double) errors / total) * 100.0 : 0.0;
                rate = Math.round(rate * 100.0) / 100.0;

                String status = "INACTIVE";
                if (total > 0) {
                    if (rate == 0.0) status = "STABLE";
                    else if (rate < 5.0) status = "WARNING";
                    else status = "CRITICAL";
                }

                dtos.add(new SystemHealthDto(sysCode, sysCode, status, total, errors, rate));
            }

            return dtos;

        } catch (Exception e) {
            log.error("[getSystemsHealth] Error procesando salud de sistemas para tenantId={}", tenantId, e);
            return Collections.emptyList();
        }
    }

    public List<String> getTenantSystems(ObjectId tenantId) {
        if (tenantId == null) return Collections.emptyList();

        Criteria criteria = new Criteria().orOperator(
                Criteria.where("tenant_id").is(tenantId),
                Criteria.where("tenant_id").is(tenantId.toHexString())
        );

        Query query = Query.query(criteria);
        List<String> systems = mongoTemplate.findDistinct(query, "system", COLLECTION, String.class);

        return systems.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::toUpperCase)
                .distinct()
                .collect(Collectors.toList());
    }

    private Criteria buildTenantCriteria(ObjectId tenantId) {
        return new Criteria().orOperator(
                Criteria.where("tenant_id").is(tenantId),
                Criteria.where("tenant_id").is(tenantId.toHexString())
        );
    }

    public ExecutiveSummaryDto getExecutiveSummary(ObjectId tenantId, Instant from, Instant to) {
        List<SystemHealthDto> healths = getSystemsHealth(tenantId, from, to);

        long totalGlobalEvents = 0;
        long totalGlobalErrors = 0;
        boolean hasCritical = false;
        boolean hasWarning = false;

        for (SystemHealthDto h : healths) {
            totalGlobalEvents += h.getTotalEvents();
            totalGlobalErrors += h.getErrorEvents();
            if ("CRITICAL".equalsIgnoreCase(h.getStatus())) hasCritical = true;
            if ("WARNING".equalsIgnoreCase(h.getStatus())) hasWarning = true;
        }

        double globalRate = totalGlobalEvents > 0
                ? Math.round(((double) totalGlobalErrors / totalGlobalEvents * 100.0) * 100.0) / 100.0
                : 0.0;

        String globalHealth = "STABLE";
        if (hasCritical) globalHealth = "CRITICAL";
        else if (hasWarning) globalHealth = "WARNING";

        return new ExecutiveSummaryDto(globalHealth, totalGlobalEvents, globalRate, 0);
    }

    private long getLongValue(Object obj) {
        if (obj instanceof Number n) return n.longValue();
        return 0L;
    }

    private AggregationOptions buildOptions(String hint) {
        return AggregationOptions.builder()
                .cursorBatchSize(CURSOR_BATCH_SIZE)
                .allowDiskUse(true)
                .hint(hint)
                .maxTime(Duration.ofMillis(3000))
                .build();
    }

}
