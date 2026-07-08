package backlogs.dinamico.service.analytics;

import backlogs.dinamico.api.dto.analytics.FunnelResponseDto;
import backlogs.dinamico.api.dto.analytics.FunnelStepDto;
import backlogs.dinamico.api.dto.analytics.FunnelSummaryDto;
import backlogs.dinamico.model.analytics.FunnelTemplate;
import backlogs.dinamico.model.analytics.FunnelTemplate.FunnelStepDefinition;
import backlogs.dinamico.repository.analytics.FunnelTemplateRepository;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.security.auth.ScopeGuard;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de analítica de embudos de conversión (Funnels) - VERSIÓN DINÁMICA.
 * 
 * Lee las configuraciones de funnel desde MongoDB (colección: funnel_templates)
 * en lugar de tener sistemas hardcoded en código Java.
 * 
 * Esto permite:
 * - Agregar nuevos sistemas sin recompilar
 * - Modificar flujos de proceso desde la BD
 * - Multi-tenant con configuraciones personalizadas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunnelAnalyticsService {

    private static final String COLLECTION = "log_events";

    private final MongoTemplate mongoTemplate;
    private final FunnelTemplateRepository funnelTemplateRepository;
    private final ScopeGuard scopeGuard;

    /**
     * Calcula el embudo de conversión para un sistema específico.
     * Lee la configuración del funnel desde MongoDB (funnel_templates).
     * 
     * @param auth Autenticación del usuario
     * @param systemName Nombre del sistema (ej: TRUSTVALUE, CITA_GUYANA, TICKETS)
     * @param from Fecha de inicio del rango (opcional)
     * @param to Fecha de fin del rango (opcional)
     * @return FunnelResponseDto con los datos del embudo calculado
     */
    public FunnelResponseDto calculateFunnel(
            Authentication auth,
            String systemName,
            Instant from,
            Instant to
    ) {
        // ── 1. Validaciones y normalización ──────────────────────────────────
        if (!StringUtils.hasText(systemName)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "systemName is required"
            );
        }

        String normalizedSystem = systemName.trim().toUpperCase(Locale.ROOT);
        
        // ── 2. Buscar configuración del funnel en MongoDB ────────────────────
        FunnelTemplate template = funnelTemplateRepository
                .findBySystemNameAndActive(normalizedSystem, true)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        String.format(
                                "No active funnel template found for system: %s. " +
                                "Available systems: %s",
                                systemName,
                                String.join(", ", getAvailableSystems())
                        )
                ));

        log.debug("[Funnel] Found template for system={}, funnel={}, steps={}",
                normalizedSystem, template.getFunnelName(), template.getSteps().size());

        // ── 3. Seguridad y contexto del tenant ───────────────────────────────
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant_not_resolved");
        }

        AuthUser user = extractAuthUser(auth);
        scopeGuard.requireSystemAccess(user, normalizedSystem);

        // ── 4. Construir criterios de consulta ───────────────────────────────
        Criteria criteria = buildBaseCriteria(tenantId, normalizedSystem, from, to);

        // ── 5. Calcular métricas por paso ────────────────────────────────────
        Map<String, Long> stepCounts = calculateStepCounts(criteria, template);

        // ── 6. Construir la respuesta con las métricas calculadas ────────────
        List<FunnelStepDto> steps = buildFunnelSteps(template, stepCounts);

        // ── 7. Calcular el resumen global ────────────────────────────────────
        FunnelSummaryDto summary = buildSummary(steps);

        log.info("[Funnel] system={}, totalStarted={}, totalCompleted={}, globalConversion={}%",
                normalizedSystem, summary.getTotalStarted(), summary.getTotalCompleted(), 
                summary.getGlobalConversionRate());

        return FunnelResponseDto.builder()
                .system(normalizedSystem)
                .funnelName(template.getFunnelName())
                .summary(summary)
                .steps(steps)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MÉTODOS PRIVADOS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extrae el usuario autenticado.
     */
    private AuthUser extractAuthUser(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthenticated");
        }
        return user;
    }

    /**
     * Construye los criterios base de MongoDB para filtrar logs.
     */
    private Criteria buildBaseCriteria(
            ObjectId tenantId,
            String system,
            Instant from,
            Instant to
    ) {
        Criteria criteria = Criteria.where("tenant_id").is(tenantId)
                .and("system").is(system);

        // Rango de fechas - default: últimos 30 días
        Instant effectiveFrom = from != null ? from : 
                Instant.now().minusSeconds(30L * 24 * 3600);
        Instant effectiveTo = to != null ? to : Instant.now();

        criteria = criteria.and("eventTime")
                .gte(Date.from(effectiveFrom))
                .lt(Date.from(effectiveTo));

        return criteria;
    }

    /**
     * Calcula la cantidad de caseId únicos para cada paso del funnel.
     * Usa agregaciones de MongoDB para performance óptima.
     */
    private Map<String, Long> calculateStepCounts(
            Criteria baseCriteria,
            FunnelTemplate template
    ) {
        Map<String, Long> counts = new HashMap<>();

        // Extraer todos los eventTypes del funnel
        List<String> eventTypes = template.getSteps().stream()
                .map(FunnelStepDefinition::getEventType)
                .collect(Collectors.toList());

        log.debug("[Funnel] Calculating counts for eventTypes: {}", eventTypes);

        // Agregación MongoDB para contar caseId únicos por eventType
        Aggregation aggregation = Aggregation.newAggregation(
                // Match: filtrar por criterios base y eventTypes del funnel
                Aggregation.match(
                        baseCriteria.and("eventType").in(eventTypes)
                                .and("caseId").exists(true).ne(null).ne("")
                ),
                
                // Group: agrupar por eventType y contar caseId distintos
                Aggregation.group("eventType")
                        .addToSet("caseId").as("uniqueCases"),
                
                // Project: calcular el tamaño del array de casos únicos
                new AggregationOperation() {
                    @Override
                    public Document toDocument(AggregationOperationContext ctx) {
                        return new Document("$project", new Document()
                                .append("_id", 1)
                                .append("count", new Document("$size", "$uniqueCases"))
                        );
                    }
                }
        );

        List<Document> results = mongoTemplate
                .aggregate(aggregation, COLLECTION, Document.class)
                .getMappedResults();

        // Mapear resultados: eventType -> count
        for (Document doc : results) {
            String eventType = doc.getString("_id");
            Long count = ((Number) doc.getOrDefault("count", 0)).longValue();
            counts.put(eventType, count);
        }

        log.debug("[Funnel] Step counts: {}", counts);
        
        return counts;
    }

    /**
     * Construye los pasos del funnel con las métricas calculadas.
     */
    private List<FunnelStepDto> buildFunnelSteps(
            FunnelTemplate template,
            Map<String, Long> stepCounts
    ) {
        List<FunnelStepDto> steps = new ArrayList<>();
        long firstStepCount = 0;
        long previousStepCount = 0;

        for (FunnelStepDefinition stepDef : template.getSteps()) {
            long count = stepCounts.getOrDefault(stepDef.getEventType(), 0L);
            
            // El primer paso es la base (100%)
            if (stepDef.getOrder() == 1) {
                firstStepCount = count;
            }

            // Calcular conversion rate (respecto al paso 1)
            double conversionRate = firstStepCount > 0 
                    ? Math.round((count * 1000.0 / firstStepCount)) / 10.0 
                    : 0.0;

            // Calcular drop rate (respecto al paso anterior)
            double dropRate = 0.0;
            if (stepDef.getOrder() > 1 && previousStepCount > 0) {
                double lostUsers = previousStepCount - count;
                dropRate = Math.round((lostUsers * 1000.0 / previousStepCount)) / 10.0;
            }

            steps.add(FunnelStepDto.builder()
                    .step(stepDef.getOrder())
                    .label(stepDef.getLabel())
                    .eventType(stepDef.getEventType())
                    .count(count)
                    .conversionRate(conversionRate)
                    .dropRate(dropRate)
                    .build());

            previousStepCount = count;
        }

        return steps;
    }

    /**
     * Construye el resumen global del funnel.
     */
    private FunnelSummaryDto buildSummary(List<FunnelStepDto> steps) {
        if (steps.isEmpty()) {
            return FunnelSummaryDto.builder()
                    .totalStarted(0)
                    .totalCompleted(0)
                    .globalConversionRate(0.0)
                    .build();
        }

        long totalStarted = steps.get(0).getCount();
        long totalCompleted = steps.get(steps.size() - 1).getCount();
        
        double globalConversionRate = totalStarted > 0 
                ? Math.round((totalCompleted * 1000.0 / totalStarted)) / 10.0 
                : 0.0;

        return FunnelSummaryDto.builder()
                .totalStarted(totalStarted)
                .totalCompleted(totalCompleted)
                .globalConversionRate(globalConversionRate)
                .build();
    }

    /**
     * Retorna la lista de sistemas que tienen configuración de funnel activa.
     * Lee directamente desde MongoDB.
     * 
     * @return Lista de nombres de sistemas disponibles
     */
    public List<String> getAvailableSystems() {
        return funnelTemplateRepository.findByActive(true).stream()
                .map(FunnelTemplate::getSystemName)
                .sorted()
                .collect(Collectors.toList());
    }
}

