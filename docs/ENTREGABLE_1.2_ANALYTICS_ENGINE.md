# 📊 ENTREGABLE 1.2: ANALYTICS ENGINE CONFIGURABLE

> Motor de análisis que usa reglas de plugins para detectar anomalías automáticamente

**Tiempo estimado:** 5-7 días

---

## 🎯 Objetivo

Crear un motor que:
- Lee `anomalyRules` de cada plugin
- Evalúa condiciones dinámicamente contra logs
- Calcula risk scores configurables
- Genera alertas automáticas

---

## 📦 ARCHIVOS A CREAR

### 1. Modelo de Anomalías Detectadas

```java
package backlogs.dinamico.model.analytics;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "detected_anomalies")
public class DetectedAnomaly {
    
    @Id
    private ObjectId id;
    
    @Field("tenant_id")
    private ObjectId tenantId;
    
    private String system;              // "TRUSTVALUE", "TICKETS"
    private String ruleId;              // ID de la regla que lo detectó
    private String ruleName;
    
    private Integer riskScore;          // 0-100
    private String severity;            // LOW, MEDIUM, HIGH, CRITICAL
    
    private String description;
    private List<String> recommendations;
    
    // Evidencia
    private List<ObjectId> relatedLogIds;  // Logs que causaron la anomalía
    private Map<String, Object> evidence;   // Datos adicionales de evidencia
    
    // Actores involucrados
    private List<String> actorIds;
    private List<String> actorNames;
    
    // Contexto
    private Instant detectedAt;
    private Instant firstOccurrence;
    private Instant lastOccurrence;
    private Integer occurrenceCount;
    
    // Estado
    private String status;              // OPEN, ACKNOWLEDGED, RESOLVED, FALSE_POSITIVE
    private String acknowledgedBy;
    private Instant acknowledgedAt;
    private String resolvedBy;
    private Instant resolvedAt;
    private String resolutionNotes;
}
```

### 2. Repository

```java
package backlogs.dinamico.repository.analytics;

import backlogs.dinamico.model.analytics.DetectedAnomaly;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface DetectedAnomalyRepository extends MongoRepository<DetectedAnomaly, ObjectId> {
    
    List<DetectedAnomaly> findByTenantIdAndSystemAndStatus(
        ObjectId tenantId, 
        String system, 
        String status
    );
    
    List<DetectedAnomaly> findByTenantIdAndStatusAndDetectedAtAfter(
        ObjectId tenantId,
        String status,
        Instant after
    );
    
    boolean existsByTenantIdAndSystemAndRuleIdAndStatusAndDetectedAtAfter(
        ObjectId tenantId,
        String system,
        String ruleId,
        String status,
        Instant after
    );
}
```

### 3. Query Evaluator (Motor de Evaluación)

```java
package backlogs.dinamico.service.analytics;

import backlogs.dinamico.model.log.LogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluador de condiciones definidas en plugins
 * 
 * Soporta sintaxis simple tipo SQL:
 * - eventType='VALOR'
 * - hour < 6 OR hour > 22
 * - COUNT(eventType per actor per hour) > 100
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConditionEvaluator {

    private final MongoTemplate mongoTemplate;
    
    /**
     * Evalúa si una condición se cumple
     * 
     * @param condition Expresión de la condición
     * @param context Contexto de evaluación (tenantId, system, tiempo, etc.)
     * @return true si la condición se cumple
     */
    public EvaluationResult evaluate(String condition, EvaluationContext context) {
        
        try {
            // Parsear y evaluar condición
            if (condition.contains("COUNT(")) {
                return evaluateCountCondition(condition, context);
            } else if (condition.contains("AND") || condition.contains("OR")) {
                return evaluateLogicalCondition(condition, context);
            } else {
                return evaluateSimpleCondition(condition, context);
            }
        } catch (Exception e) {
            log.error("Error evaluando condición '{}': {}", condition, e.getMessage());
            return EvaluationResult.error(e.getMessage());
        }
    }
    
    /**
     * Evalúa condiciones simples: eventType='VALOR', hour < 6, etc.
     */
    private EvaluationResult evaluateSimpleCondition(String condition, EvaluationContext context) {
        
        Criteria criteria = buildCriteriaFromCondition(condition, context);
        
        Query query = new Query(criteria);
        long count = mongoTemplate.count(query, "log_events");
        
        return EvaluationResult.success(count > 0, Map.of("matchingEvents", count));
    }
    
    /**
     * Evalúa condiciones lógicas con AND/OR
     */
    private EvaluationResult evaluateLogicalCondition(String condition, EvaluationContext context) {
        
        // Dividir por OR primero
        if (condition.contains(" OR ")) {
            String[] orParts = condition.split(" OR ");
            for (String part : orParts) {
                EvaluationResult result = evaluate(part.trim(), context);
                if (result.isMatched()) {
                    return result; // Con OR, cualquier true es suficiente
                }
            }
            return EvaluationResult.success(false, Map.of());
        }
        
        // Dividir por AND
        if (condition.contains(" AND ")) {
            String[] andParts = condition.split(" AND ");
            Map<String, Object> allEvidence = new HashMap<>();
            
            for (String part : andParts) {
                EvaluationResult result = evaluate(part.trim(), context);
                if (!result.isMatched()) {
                    return result; // Con AND, cualquier false invalida todo
                }
                allEvidence.putAll(result.getEvidence());
            }
            return EvaluationResult.success(true, allEvidence);
        }
        
        return evaluateSimpleCondition(condition, context);
    }
    
    /**
     * Evalúa condiciones de conteo: COUNT(eventType per actor per hour) > 100
     */
    private EvaluationResult evaluateCountCondition(String condition, EvaluationContext context) {
        
        // Parsear: COUNT(eventType per actor per hour) > 100
        Pattern pattern = Pattern.compile(
            "COUNT\\((.+?) per (.+?) per (.+?)\\)\\s*([><]=?)\\s*(\\d+)"
        );
        Matcher matcher = pattern.matcher(condition);
        
        if (!matcher.find()) {
            log.warn("No se pudo parsear condición de COUNT: {}", condition);
            return EvaluationResult.success(false, Map.of());
        }
        
        String eventTypeFilter = matcher.group(1).replace("'", "").trim();
        String groupBy1 = matcher.group(2).trim(); // "actor"
        String groupBy2 = matcher.group(3).trim(); // "hour"
        String operator = matcher.group(4);
        int threshold = Integer.parseInt(matcher.group(5));
        
        // Construir query de agregación
        Criteria criteria = Criteria.where("tenant_id").is(context.getTenantId())
            .and("system").is(context.getSystem());
        
        if (eventTypeFilter.contains("eventType=")) {
            String eventType = eventTypeFilter.split("=")[1].replace("'", "");
            criteria.and("eventType").is(eventType);
        }
        
        // Rango de tiempo según granularidad
        Instant rangeStart = calculateTimeRange(groupBy2);
        criteria.and("eventTime").gte(rangeStart);
        
        Query query = new Query(criteria);
        
        // Contar por actor
        List<LogEvent> events = mongoTemplate.find(query, LogEvent.class);
        
        // Agrupar manualmente
        Map<String, Long> countByActor = new HashMap<>();
        for (LogEvent event : events) {
            String actorId = event.getActor() != null ? event.getActor().getId() : "unknown";
            countByActor.merge(actorId, 1L, Long::sum);
        }
        
        // Verificar si algún actor excede el umbral
        for (Map.Entry<String, Long> entry : countByActor.entrySet()) {
            boolean exceeds = compareValue(entry.getValue(), operator, threshold);
            if (exceeds) {
                return EvaluationResult.success(true, Map.of(
                    "actorId", entry.getKey(),
                    "count", entry.getValue(),
                    "threshold", threshold,
                    "exceedsBy", entry.getValue() - threshold
                ));
            }
        }
        
        return EvaluationResult.success(false, Map.of());
    }
    
    /**
     * Construir Criteria de MongoDB desde expresión simple
     */
    private Criteria buildCriteriaFromCondition(String condition, EvaluationContext context) {
        
        Criteria criteria = Criteria.where("tenant_id").is(context.getTenantId())
            .and("system").is(context.getSystem());
        
        // Agregar filtro de tiempo si aplica
        if (context.getTimeRangeStart() != null) {
            criteria.and("eventTime").gte(context.getTimeRangeStart());
        }
        if (context.getTimeRangeEnd() != null) {
            criteria.and("eventTime").lte(context.getTimeRangeEnd());
        }
        
        // Parsear condición simple
        // Ejemplo: "eventType='ASISTENCIA_MARCADA'"
        if (condition.contains("eventType=")) {
            String value = extractValue(condition, "eventType");
            criteria.and("eventType").is(value);
        }
        
        // Ejemplo: "hour < 6"
        if (condition.contains("hour")) {
            criteria = addHourCondition(criteria, condition);
        }
        
        // Ejemplo: "geo.accuracyMeters > 1000"
        if (condition.contains("geo.accuracyMeters")) {
            criteria = addGeoCondition(criteria, condition);
        }
        
        return criteria;
    }
    
    private Criteria addHourCondition(Criteria criteria, String condition) {
        // Extraer hora y operador
        Pattern pattern = Pattern.compile("hour\\s*([<>]=?)\\s*(\\d+)");
        Matcher matcher = pattern.matcher(condition);
        
        if (matcher.find()) {
            String operator = matcher.group(1);
            int hour = Integer.parseInt(matcher.group(2));
            
            // Filtrar por hora usando aggregation (simplificado)
            // En producción, usar $expr con $hour
            // Por ahora, aproximación
        }
        
        return criteria;
    }
    
    private Criteria addGeoCondition(Criteria criteria, String condition) {
        Pattern pattern = Pattern.compile("geo\\.accuracyMeters\\s*([<>]=?)\\s*(\\d+)");
        Matcher matcher = pattern.matcher(condition);
        
        if (matcher.find()) {
            String operator = matcher.group(1);
            int value = Integer.parseInt(matcher.group(2));
            
            if (operator.equals(">")) {
                criteria.and("geo.accuracyMeters").gt(value);
            } else if (operator.equals("<")) {
                criteria.and("geo.accuracyMeters").lt(value);
            } else if (operator.equals(">=")) {
                criteria.and("geo.accuracyMeters").gte(value);
            } else if (operator.equals("<=")) {
                criteria.and("geo.accuracyMeters").lte(value);
            }
        }
        
        return criteria;
    }
    
    private String extractValue(String condition, String field) {
        Pattern pattern = Pattern.compile(field + "\\s*=\\s*['\"](.+?)['\"]");
        Matcher matcher = pattern.matcher(condition);
        return matcher.find() ? matcher.group(1) : "";
    }
    
    private boolean compareValue(long actual, String operator, long expected) {
        return switch (operator) {
            case ">" -> actual > expected;
            case "<" -> actual < expected;
            case ">=" -> actual >= expected;
            case "<=" -> actual <= expected;
            case "=" -> actual == expected;
            default -> false;
        };
    }
    
    private Instant calculateTimeRange(String granularity) {
        return switch (granularity.toLowerCase()) {
            case "hour" -> Instant.now().minus(1, ChronoUnit.HOURS);
            case "day" -> Instant.now().minus(1, ChronoUnit.DAYS);
            case "week" -> Instant.now().minus(7, ChronoUnit.DAYS);
            default -> Instant.now().minus(1, ChronoUnit.HOURS);
        };
    }
}

/**
 * Contexto de evaluación
 */
@Data
@lombok.Builder
class EvaluationContext {
    private ObjectId tenantId;
    private String system;
    private Instant timeRangeStart;
    private Instant timeRangeEnd;
}

/**
 * Resultado de evaluación
 */
@Data
@lombok.AllArgsConstructor
class EvaluationResult {
    private boolean matched;
    private Map<String, Object> evidence;
    private String error;
    
    public static EvaluationResult success(boolean matched, Map<String, Object> evidence) {
        return new EvaluationResult(matched, evidence, null);
    }
    
    public static EvaluationResult error(String errorMsg) {
        return new EvaluationResult(false, Map.of(), errorMsg);
    }
}
```

### 4. Analytics Engine Service

```java
package backlogs.dinamico.service.analytics;

import backlogs.dinamico.model.analytics.DetectedAnomaly;
import backlogs.dinamico.model.plugin.AnomalyRule;
import backlogs.dinamico.model.plugin.SystemPlugin;
import backlogs.dinamico.repository.analytics.DetectedAnomalyRepository;
import backlogs.dinamico.repository.plugin.SystemPluginRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsEngineService {

    private final SystemPluginRepository pluginRepository;
    private final DetectedAnomalyRepository anomalyRepository;
    private final ConditionEvaluator conditionEvaluator;
    
    /**
     * Ejecutar análisis cada hora
     */
    @Scheduled(cron = "0 0 * * * *")
    public void runScheduledAnalysis() {
        log.info("Iniciando análisis programado de anomalías...");
        
        // Obtener todos los plugins activos
        List<SystemPlugin> plugins = pluginRepository.findByActiveTrue();
        
        for (SystemPlugin plugin : plugins) {
            try {
                analyzeSystem(plugin);
            } catch (Exception e) {
                log.error("Error analizando sistema {}: {}", 
                    plugin.getSystemId(), e.getMessage());
            }
        }
        
        log.info("Análisis programado completado");
    }
    
    /**
     * Analizar un sistema específico
     */
    public void analyzeSystem(SystemPlugin plugin) {
        
        log.debug("Analizando sistema: {}", plugin.getSystemId());
        
        if (plugin.getAnomalyRules() == null || plugin.getAnomalyRules().isEmpty()) {
            log.debug("Sistema {} no tiene reglas de anomalías configuradas", 
                plugin.getSystemId());
            return;
        }
        
        // Evaluar cada regla
        for (AnomalyRule rule : plugin.getAnomalyRules()) {
            evaluateRule(plugin, rule);
        }
    }
    
    /**
     * Evaluar una regla específica
     */
    private void evaluateRule(SystemPlugin plugin, AnomalyRule rule) {
        
        // Verificar si ya existe una anomalía abierta para esta regla
        Instant recentWindow = Instant.now().minus(1, ChronoUnit.HOURS);
        
        boolean alreadyExists = anomalyRepository
            .existsByTenantIdAndSystemAndRuleIdAndStatusAndDetectedAtAfter(
                plugin.getTenantId(),
                plugin.getSystemId(),
                rule.getId(),
                "OPEN",
                recentWindow
            );
        
        if (alreadyExists) {
            log.debug("Ya existe anomalía abierta para regla {}, skip", rule.getId());
            return;
        }
        
        // Construir contexto de evaluación
        EvaluationContext context = EvaluationContext.builder()
            .tenantId(plugin.getTenantId())
            .system(plugin.getSystemId())
            .timeRangeStart(Instant.now().minus(1, ChronoUnit.HOURS))
            .timeRangeEnd(Instant.now())
            .build();
        
        // Evaluar condición
        EvaluationResult result = conditionEvaluator.evaluate(rule.getCondition(), context);
        
        if (result.isMatched()) {
            // ¡Anomalía detectada!
            createAnomaly(plugin, rule, result);
        }
    }
    
    /**
     * Crear registro de anomalía detectada
     */
    private void createAnomaly(SystemPlugin plugin, AnomalyRule rule, EvaluationResult result) {
        
        DetectedAnomaly anomaly = new DetectedAnomaly();
        anomaly.setTenantId(plugin.getTenantId());
        anomaly.setSystem(plugin.getSystemId());
        anomaly.setRuleId(rule.getId());
        anomaly.setRuleName(rule.getName());
        anomaly.setRiskScore(rule.getRiskScore());
        anomaly.setSeverity(rule.getSeverity());
        anomaly.setDescription(rule.getDescription());
        anomaly.setRecommendations(rule.getRecommendations());
        anomaly.setEvidence(result.getEvidence());
        anomaly.setDetectedAt(Instant.now());
        anomaly.setFirstOccurrence(Instant.now());
        anomaly.setLastOccurrence(Instant.now());
        anomaly.setOccurrenceCount(1);
        anomaly.setStatus("OPEN");
        
        // Extraer actores de evidencia si existen
        if (result.getEvidence().containsKey("actorId")) {
            anomaly.setActorIds(List.of(result.getEvidence().get("actorId").toString()));
        }
        
        anomalyRepository.save(anomaly);
        
        log.info("🚨 Anomalía detectada: {} (Risk: {}/100) en sistema {}", 
            rule.getName(), rule.getRiskScore(), plugin.getSystemId());
        
        // Notificar si es crítico
        if (rule.getRiskScore() >= 80) {
            notifyCriticalAnomaly(anomaly);
        }
    }
    
    private void notifyCriticalAnomaly(DetectedAnomaly anomaly) {
        // TODO: Integrar con servicio de notificaciones
        log.warn("🔴 ANOMALÍA CRÍTICA: {} - {}", anomaly.getRuleName(), anomaly.getDescription());
    }
}
```

### 5. Controller

```java
package backlogs.dinamico.controller.analytics;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.model.analytics.DetectedAnomaly;
import backlogs.dinamico.repository.analytics.DetectedAnomalyRepository;
import backlogs.dinamico.service.analytics.AnalyticsEngineService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Analytics - Anomalías", description = "Anomalías detectadas automáticamente")
@RestController
@RequestMapping(value = "/api/analytics/anomalies", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AnomaliesController {

    private final DetectedAnomalyRepository anomalyRepository;
    private final AnalyticsEngineService analyticsEngine;
    private final MongoTemplate mongoTemplate;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ORG_OWNER', 'ORG_ADMIN', 'VIEWER')")
    public ApiResponse<List<DetectedAnomaly>> listAnomalies(
        Authentication auth,
        @RequestParam(required = false) String system,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "24") int hours
    ) {
        ObjectId tenantId = resolveTenantId(auth);
        
        Criteria criteria = Criteria.where("tenantId").is(tenantId);
        
        if (system != null && !system.isBlank()) {
            criteria.and("system").is(system.toUpperCase());
        }
        
        if (status != null && !status.isBlank()) {
            criteria.and("status").is(status.toUpperCase());
        }
        
        // Filtrar por tiempo
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        criteria.and("detectedAt").gte(since);
        
        Query query = new Query(criteria)
            .with(Sort.by(Sort.Direction.DESC, "riskScore", "detectedAt"));
        
        List<DetectedAnomaly> anomalies = mongoTemplate.find(query, DetectedAnomaly.class);
        
        return ApiResponse.ok("Anomalías detectadas", "anomalies_list", anomalies);
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ORG_OWNER', 'ORG_ADMIN', 'VIEWER')")
    public ApiResponse<DetectedAnomaly> getAnomaly(
        Authentication auth,
        @PathVariable ObjectId id
    ) {
        ObjectId tenantId = resolveTenantId(auth);
        
        DetectedAnomaly anomaly = anomalyRepository.findById(id)
            .filter(a -> a.getTenantId().equals(tenantId))
            .orElseThrow(() -> new RuntimeException("Anomalía no encontrada"));
        
        return ApiResponse.ok("Detalle de anomalía", "anomaly_detail", anomaly);
    }
    
    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyAuthority('ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<DetectedAnomaly> acknowledgeAnomaly(
        Authentication auth,
        @PathVariable ObjectId id
    ) {
        DetectedAnomaly anomaly = anomalyRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Anomalía no encontrada"));
        
        anomaly.setStatus("ACKNOWLEDGED");
        anomaly.setAcknowledgedBy(getUserEmail(auth));
        anomaly.setAcknowledgedAt(Instant.now());
        
        anomalyRepository.save(anomaly);
        
        return ApiResponse.ok("Anomalía reconocida", "anomaly_acknowledged", anomaly);
    }
    
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyAuthority('ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<DetectedAnomaly> resolveAnomaly(
        Authentication auth,
        @PathVariable ObjectId id,
        @RequestBody ResolveRequest request
    ) {
        DetectedAnomaly anomaly = anomalyRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Anomalía no encontrada"));
        
        anomaly.setStatus("RESOLVED");
        anomaly.setResolvedBy(getUserEmail(auth));
        anomaly.setResolvedAt(Instant.now());
        anomaly.setResolutionNotes(request.getNotes());
        
        anomalyRepository.save(anomaly);
        
        return ApiResponse.ok("Anomalía resuelta", "anomaly_resolved", anomaly);
    }
    
    @PostMapping("/{id}/mark-false-positive")
    @PreAuthorize("hasAnyAuthority('ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<DetectedAnomaly> markFalsePositive(
        Authentication auth,
        @PathVariable ObjectId id
    ) {
        DetectedAnomaly anomaly = anomalyRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Anomalía no encontrada"));
        
        anomaly.setStatus("FALSE_POSITIVE");
        anomaly.setResolvedBy(getUserEmail(auth));
        anomaly.setResolvedAt(Instant.now());
        
        anomalyRepository.save(anomaly);
        
        return ApiResponse.ok("Marcada como falsa alarma", "anomaly_false_positive", anomaly);
    }
    
    @PostMapping("/analyze-now")
    @PreAuthorize("hasAuthority('ORG_OWNER')")
    public ApiResponse<Void> triggerAnalysis(
        @RequestParam(required = false) String system
    ) {
        // Disparar análisis manual
        log.info("Análisis manual solicitado para sistema: {}", system);
        
        // TODO: Implementar análisis específico por sistema
        
        return ApiResponse.ok("Análisis iniciado", "analysis_triggered", null);
    }

    private ObjectId resolveTenantId(Authentication auth) {
        return new ObjectId(); // Implementar
    }
    
    private String getUserEmail(Authentication auth) {
        return auth.getName();
    }
}

@Data
class ResolveRequest {
    private String notes;
}
```

---

## ✅ CHECKLIST ENTREGABLE 1.2

- [ ] Modelo `DetectedAnomaly` creado
- [ ] Repository creado
- [ ] `ConditionEvaluator` implementado
- [ ] `AnalyticsEngineService` implementado
- [ ] Scheduler configurado (cron cada hora)
- [ ] Controller `/api/analytics/anomalies` creado
- [ ] Probado con reglas de TRUSTVALUE
- [ ] Probado con reglas de TICKETS
- [ ] Verificar alertas se crean correctamente

**Tiempo:** 5-7 días

---

## 🧪 TESTING

### Caso de Prueba 1: TRUSTVALUE - Asistencia fuera de horario

1. Crear log con `eventType=ASISTENCIA_MARCADA` a las 11 PM
2. Esperar análisis programado (o ejecutar manualmente)
3. Verificar que se crea `DetectedAnomaly` con:
   - `ruleId = "asistencia_fuera_horario"`
   - `riskScore = 60`
   - `severity = "MEDIUM"`

### Caso de Prueba 2: TICKETS - Acceso masivo

1. Crear 150 logs de `CONSULTA_DE_CLIENTES` del mismo actor en 1 hora
2. Ejecutar análisis
3. Verificar anomalía detectada con `ruleId = "acceso_masivo_datos"`

---

## 🔄 PRÓXIMO PASO

Continuar con **Entregable 1.3: KPIs Dinámicos**

