package backlogs.dinamico.service.catalog;

import backlogs.dinamico.api.dto.catalog.SystemHealthDto;
import backlogs.dinamico.api.dto.catalog.SystemStatus24hItemDto;
import backlogs.dinamico.model.catalog.SystemApp;
import backlogs.dinamico.repository.catalog.SystemAppRepository;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;


/**
 * Servicio de catálogo de sistemas con salud operativa.
 *
 * <p>Calcula la salud de cada sistema mediante una única agregación MongoDB
 * agrupada por {@code system} dentro del periodo solicitado. Si no se indica
 * periodo, evalúa el día actual en el huso horario local.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    private static final String COLLECTION = "log_events";

    private static final double STABLE_THRESHOLD = 1.0;
    private static final double WARNING_THRESHOLD = 5.0;

    private final SystemAppRepository systemAppRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Devuelve la salud de cada sistema visible para el usuario.
     *
     * <p>El cálculo se realiza con una sola agregación MongoDB por tenant y
     * periodo. Si {@code from} o {@code to} son nulos se evalúa el día actual
     * en el huso horario local.</p>
     *
     * @param allowedSystems null o vacío = sin restricción; con elementos = filtrar por code
     * @param from           inicio del periodo (inclusive); null = día actual
     * @param to             fin del periodo (inclusive); null = día actual
     */
    public List<SystemHealthDto> getSystemsHealth(List<String> allowedSystems, Instant from, Instant to) {
        ObjectId tenantId = TenantContext.requireTenantId();

        // 1. Descubrir TODOS los sistemas del tenant en log_events (sin filtro de fechas)
        List<String> tenantSystems = getTenantSystems(tenantId);
        if (tenantSystems.isEmpty()) {
            return List.of();
        }

        // 2. Aplicar filtro de sistemas permitidos si viene definido
        List<String> visibleSystems = filterAllowedSystems(tenantSystems, allowedSystems);
        if (visibleSystems.isEmpty()) {
            return List.of();
        }

        // 3. Cargar nombres de sistema desde el catálogo (opcional, para enriquecer la respuesta)
        Map<String, SystemApp> appByCode = loadAppNamesByCode(tenantId);

        try {
            InstantRange range = resolveRange(from, to);
            Map<String, HealthAggregate> statsBySystem = aggregateHealthBySystem(tenantId, visibleSystems, range);
            return buildHealthItems(visibleSystems, appByCode, statsBySystem);
        } catch (Throwable e) {
            log.error("[getSystemsHealth] Error calculando salud de sistemas", e);
            return buildHealthItems(visibleSystems, appByCode, Map.of());
        }
    }

    private List<SystemHealthDto> buildHealthItems(
            List<String> visibleSystems,
            Map<String, SystemApp> appByCode,
            Map<String, HealthAggregate> statsBySystem) {
        List<SystemHealthDto> result = new ArrayList<>();
        for (String code : visibleSystems) {
            SystemApp app = appByCode.get(code.toUpperCase(Locale.ROOT));
            HealthAggregate aggregate = statsBySystem.getOrDefault(
                    code.toUpperCase(Locale.ROOT), HealthAggregate.empty());
            result.add(buildHealthItem(app != null ? app : createPlaceholderApp(code), aggregate));
        }
        return result;
    }

    private SystemApp createPlaceholderApp(String code) {
        SystemApp app = new SystemApp();
        app.setCode(code);
        app.setName(code);
        return app;
    }

    /**
     * Devuelve el color de estado de cada sistema visible para el menú desplegable.
     *
     * <p>Reutiliza el cálculo de salud del día actual y lo transforma en los colores
     * esperados por el frontend: {@code positive}, {@code warning},
     * {@code negative} o {@code grey-6}.</p>
     *
     * @param allowedSystems null o vacío = sin restricción; con elementos = filtrar por code
     */
    public List<SystemStatus24hItemDto> getSystemsStatus24h(List<String> allowedSystems) {
        return getSystemsHealth(allowedSystems, null, null).stream()
                .map(this::toStatus24h)
                .toList();
    }

    private Map<String, HealthAggregate> aggregateHealthBySystem(ObjectId tenantId, List<String> systems, InstantRange range) {
        List<AggregationOperation> stages = new ArrayList<>();

        Criteria tenantCriteria = new Criteria().orOperator(
                Criteria.where("tenant_id").is(tenantId),
                Criteria.where("tenant_id").is(tenantId.toHexString())
        );

        List<Criteria> matchAndCriteria = new ArrayList<>();
        matchAndCriteria.add(tenantCriteria);
        matchAndCriteria.add(Criteria.where("system").in(systems));

        if (range.from() != null && range.to() != null && !range.from().equals(Instant.EPOCH)) {
            matchAndCriteria.add(Criteria.where("eventTime").gte(Date.from(range.from())).lte(Date.from(range.to())));
        }

        stages.add(match(new Criteria().andOperator(matchAndCriteria.toArray(new Criteria[0]))));

        stages.add(Aggregation.group("system")
                .count().as("totalEvents")
                .sum(ConditionalOperators.when(Criteria.where("isError").is(true)).then(1).otherwise(0)).as("errorEvents")
                .sum(ConditionalOperators.when(Criteria.where("severity").is("FATAL")).then(1).otherwise(0)).as("fatalEvents"));

        stages.add(Aggregation.project("totalEvents", "errorEvents", "fatalEvents")
                .and("system").previousOperation()
                .andExpression("(errorEvents / totalEvents) * 100").as("errorRate"));

        AggregationOptions options = AggregationOptions.builder()
                .maxTime(Duration.ofMillis(5000))
                .build();

        Aggregation aggregation = newAggregation(stages).withOptions(options);

        List<Document> results = mongoTemplate.aggregate(aggregation, COLLECTION, Document.class)
                .getMappedResults();

        Map<String, HealthAggregate> map = new HashMap<>();
        for (Document doc : results) {
            String system = doc.getString("system");
            long totalEvents = getLong(doc, "totalEvents");
            long errorEvents = getLong(doc, "errorEvents");
            long fatalEvents = getLong(doc, "fatalEvents");
            if (StringUtils.hasText(system)) {
                String key = system.trim().toUpperCase(Locale.ROOT);
                map.merge(key, new HealthAggregate(totalEvents, errorEvents, fatalEvents),
                        (a, b) -> new HealthAggregate(
                                a.totalEvents() + b.totalEvents(),
                                a.errorEvents() + b.errorEvents(),
                                a.fatalEvents() + b.fatalEvents()));
            }
        }
        return map;
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

    private List<String> filterAllowedSystems(List<String> tenantSystems, List<String> allowedSystems) {
        if (allowedSystems == null || allowedSystems.isEmpty()) {
            return tenantSystems;
        }
        Set<String> allowed = allowedSystems.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return tenantSystems.stream()
                .filter(allowed::contains)
                .toList();
    }

    private Map<String, SystemApp> loadAppNamesByCode(ObjectId tenantId) {
        try {
            return systemAppRepository.findByTenantId(tenantId, Pageable.unpaged()).getContent().stream()
                    .filter(app -> StringUtils.hasText(app.getCode()))
                    .collect(Collectors.toMap(
                            app -> app.getCode().toUpperCase(Locale.ROOT),
                            app -> app,
                            (a, b) -> a));
        } catch (Exception e) {
            log.warn("[loadAppNamesByCode] No se pudieron cargar nombres de sistema: {}", e.getMessage());
            return Map.of();
        }
    }

    private SystemHealthDto buildHealthItem(SystemApp app, HealthAggregate aggregate) {
        long totalEvents = aggregate.totalEvents();
        long errorEvents = aggregate.errorEvents();
        long fatalEvents = aggregate.fatalEvents();

        String status;
        double errorRate;
        if (totalEvents == 0) {
            status = "INACTIVE";
            errorRate = 0.0;
        } else {
            errorRate = Math.round((errorEvents * 10000.0 / totalEvents)) / 100.0;
            if (fatalEvents > 0 || errorRate >= WARNING_THRESHOLD) {
                status = "CRITICAL";
            } else if (errorRate > 0.0) {
                status = "WARNING";
            } else {
                status = "STABLE";
            }
        }

        return SystemHealthDto.builder()
                .systemCode(app.getCode())
                .systemName(app.getName())
                .status(status)
                .totalEvents(totalEvents)
                .errorEvents(errorEvents)
                .errorRate(errorRate)
                .build();
    }

    private SystemStatus24hItemDto toStatus24h(SystemHealthDto health) {
        String color = switch (health.getStatus()) {
            case "STABLE" -> "positive";
            case "WARNING" -> "warning";
            case "CRITICAL" -> "negative";
            default -> "grey-6";
        };

        return SystemStatus24hItemDto.builder()
                .system(health.getSystemCode())
                .color(color)
                .build();
    }

    private InstantRange resolveRange(Instant from, Instant to) {
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : Instant.now().minus(24, java.time.temporal.ChronoUnit.HOURS);
        return new InstantRange(effectiveFrom, effectiveTo);
    }

    private static long getLong(Document doc, String key) {
        if (doc == null) {
            return 0L;
        }
        Object value = doc.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private record InstantRange(Instant from, Instant to) {
        @Override
        public String toString() {
            return from + " -> " + to;
        }
    }

    private record HealthAggregate(long totalEvents, long errorEvents, long fatalEvents) {
        static HealthAggregate empty() {
            return new HealthAggregate(0L, 0L, 0L);
        }
    }
}
