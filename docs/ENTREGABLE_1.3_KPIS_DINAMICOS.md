# 📈 ENTREGABLE 1.3: KPIs DINÁMICOS

> Dashboard que calcula y muestra KPIs definidos en plugins automáticamente

**Tiempo estimado:** 5-7 días

---

## 🎯 Objetivo

Crear sistema que:
- Lee `kpis` definidos en cada plugin
- Interpreta queries simples
- Calcula valores automáticamente
- Renderiza widgets configurables en frontend

---

## 📦 ARCHIVOS A CREAR

### 1. KPI Calculator Service

```java
package backlogs.dinamico.service.analytics;

import backlogs.dinamico.model.plugin.KpiDefinition;
import backlogs.dinamico.model.plugin.SystemPlugin;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class KpiCalculatorService {

    private final MongoTemplate mongoTemplate;
    
    /**
     * Calcular todos los KPIs de un sistema
     */
    public List<KpiValue> calculateKpis(SystemPlugin plugin, LocalDate date) {
        
        if (plugin.getKpis() == null || plugin.getKpis().isEmpty()) {
            return Collections.emptyList();
        }
        
        List<KpiValue> results = new ArrayList<>();
        
        for (KpiDefinition kpi : plugin.getKpis()) {
            try {
                KpiValue value = calculateKpi(plugin, kpi, date);
                results.add(value);
            } catch (Exception e) {
                log.error("Error calculando KPI {}: {}", kpi.getId(), e.getMessage());
                results.add(KpiValue.error(kpi, e.getMessage()));
            }
        }
        
        return results;
    }
    
    /**
     * Calcular un KPI específico
     */
    public KpiValue calculateKpi(SystemPlugin plugin, KpiDefinition kpi, LocalDate date) {
        
        log.debug("Calculando KPI: {} para sistema {}", kpi.getId(), plugin.getSystemId());
        
        // Parsear query
        KpiQuery parsedQuery = parseQuery(kpi.getQuery());
        
        // Ejecutar query según tipo
        Object result = executeQuery(parsedQuery, plugin.getTenantId(), plugin.getSystemId(), date);
        
        return KpiValue.success(kpi, result);
    }
    
    /**
     * Parsear query simple en objeto estructurado
     * 
     * Ejemplos soportados:
     * - COUNT WHERE eventType='VALOR' AND date=TODAY
     * - COUNT_DISTINCT location.name WHERE ...
     * - AVG(sla.elapsedSeconds) WHERE ...
     */
    private KpiQuery parseQuery(String queryString) {
        
        KpiQuery query = new KpiQuery();
        
        // Detectar operación (COUNT, COUNT_DISTINCT, AVG, SUM)
        if (queryString.startsWith("COUNT_DISTINCT")) {
            query.operation = "COUNT_DISTINCT";
            Pattern pattern = Pattern.compile("COUNT_DISTINCT\\s+([\\w.]+)\\s+WHERE");
            Matcher matcher = pattern.matcher(queryString);
            if (matcher.find()) {
                query.distinctField = matcher.group(1);
            }
        } else if (queryString.startsWith("COUNT")) {
            query.operation = "COUNT";
        } else if (queryString.startsWith("AVG")) {
            query.operation = "AVG";
            Pattern pattern = Pattern.compile("AVG\\(([\\w.]+)\\)");
            Matcher matcher = pattern.matcher(queryString);
            if (matcher.find()) {
                query.avgField = matcher.group(1);
            }
        } else if (queryString.startsWith("SUM")) {
            query.operation = "SUM";
            Pattern pattern = Pattern.compile("SUM\\(([\\w.]+)\\)");
            Matcher matcher = pattern.matcher(queryString);
            if (matcher.find()) {
                query.sumField = matcher.group(1);
            }
        }
        
        // Extraer filtros del WHERE
        Pattern wherePattern = Pattern.compile("WHERE\\s+(.+)$");
        Matcher whereMatcher = wherePattern.matcher(queryString);
        if (whereMatcher.find()) {
            query.filters = whereMatcher.group(1).trim();
        }
        
        return query;
    }
    
    /**
     * Ejecutar query contra MongoDB
     */
    private Object executeQuery(KpiQuery query, ObjectId tenantId, String system, LocalDate date) {
        
        // Construir Criteria base
        Criteria criteria = Criteria.where("tenant_id").is(tenantId)
            .and("system").is(system);
        
        // Agregar filtros
        criteria = applyFilters(criteria, query.filters, date);
        
        Query mongoQuery = new Query(criteria);
        
        // Ejecutar según operación
        return switch (query.operation) {
            case "COUNT" -> mongoTemplate.count(mongoQuery, "log_events");
            
            case "COUNT_DISTINCT" -> countDistinct(mongoQuery, query.distinctField);
            
            case "AVG" -> calculateAverage(mongoQuery, query.avgField);
            
            case "SUM" -> calculateSum(mongoQuery, query.sumField);
            
            default -> 0;
        };
    }
    
    /**
     * Aplicar filtros del WHERE a Criteria
     */
    private Criteria applyFilters(Criteria criteria, String filters, LocalDate date) {
        
        if (filters == null || filters.isBlank()) {
            return criteria;
        }
        
        // Parsear filtros separados por AND
        String[] conditions = filters.split(" AND ");
        
        for (String condition : conditions) {
            condition = condition.trim();
            
            // eventType='VALOR'
            if (condition.contains("eventType=")) {
                String value = extractQuotedValue(condition);
                criteria.and("eventType").is(value);
            }
            
            // outcome='SUCCESS'
            else if (condition.contains("outcome=")) {
                String value = extractQuotedValue(condition);
                criteria.and("outcome").is(value);
            }
            
            // status='PENDING'
            else if (condition.contains("status=")) {
                String value = extractQuotedValue(condition);
                criteria.and("status").is(value);
            }
            
            // date=TODAY
            else if (condition.contains("date=TODAY")) {
                ZoneId zone = ZoneId.of("America/Mexico_City");
                Instant dayStart = date.atStartOfDay(zone).toInstant();
                Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
                criteria.and("eventTime").gte(dayStart).lt(dayEnd);
            }
        }
        
        return criteria;
    }
    
    private String extractQuotedValue(String condition) {
        Pattern pattern = Pattern.compile("['\"](.+?)['\"]");
        Matcher matcher = pattern.matcher(condition);
        return matcher.find() ? matcher.group(1) : "";
    }
    
    private long countDistinct(Query query, String field) {
        // MongoDB distinct count
        List<?> distinct = mongoTemplate.findDistinct(query, field, "log_events", Object.class);
        return distinct.size();
    }
    
    private double calculateAverage(Query query, String field) {
        // Obtener todos los docs y calcular promedio
        List<Map> docs = mongoTemplate.find(query, Map.class, "log_events");
        
        if (docs.isEmpty()) return 0.0;
        
        double sum = 0;
        int count = 0;
        
        for (Map doc : docs) {
            Object value = extractNestedField(doc, field);
            if (value instanceof Number) {
                sum += ((Number) value).doubleValue();
                count++;
            }
        }
        
        return count > 0 ? sum / count : 0.0;
    }
    
    private double calculateSum(Query query, String field) {
        List<Map> docs = mongoTemplate.find(query, Map.class, "log_events");
        
        double sum = 0;
        for (Map doc : docs) {
            Object value = extractNestedField(doc, field);
            if (value instanceof Number) {
                sum += ((Number) value).doubleValue();
            }
        }
        
        return sum;
    }
    
    private Object extractNestedField(Map<String, Object> doc, String field) {
        // Soportar campos anidados: "sla.elapsedSeconds"
        String[] parts = field.split("\\.");
        Object current = doc;
        
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        
        return current;
    }
}

/**
 * Query parseado
 */
@Data
class KpiQuery {
    String operation;      // COUNT, COUNT_DISTINCT, AVG, SUM
    String distinctField;  // Para COUNT_DISTINCT
    String avgField;       // Para AVG
    String sumField;       // Para SUM
    String filters;        // Condiciones del WHERE
}

/**
 * Valor calculado de un KPI
 */
@Data
@lombok.AllArgsConstructor
class KpiValue {
    private String id;
    private String name;
    private String description;
    private Object value;
    private String displayType;
    private String icon;
    private Integer target;
    private String error;
    
    public static KpiValue success(KpiDefinition kpi, Object value) {
        return new KpiValue(
            kpi.getId(),
            kpi.getName(),
            kpi.getDescription(),
            value,
            kpi.getDisplayType(),
            kpi.getIcon(),
            kpi.getTarget(),
            null
        );
    }
    
    public static KpiValue error(KpiDefinition kpi, String error) {
        return new KpiValue(
            kpi.getId(),
            kpi.getName(),
            kpi.getDescription(),
            null,
            kpi.getDisplayType(),
            kpi.getIcon(),
            kpi.getTarget(),
            error
        );
    }
}
```

### 2. Dashboard KPI Service

```java
package backlogs.dinamico.service.analytics;

import backlogs.dinamico.model.plugin.SystemPlugin;
import backlogs.dinamico.repository.plugin.SystemPluginRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardKpiService {

    private final SystemPluginRepository pluginRepository;
    private final KpiCalculatorService kpiCalculator;
    
    /**
     * Obtener dashboard de KPIs para un sistema
     */
    public DashboardKpiDto getDashboardKpis(ObjectId tenantId, String systemId, LocalDate date) {
        
        // Obtener plugin del sistema
        SystemPlugin plugin = pluginRepository.findBySystemIdAndTenantId(systemId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Sistema " + systemId + " no encontrado o no tiene plugin configurado"
            ));
        
        if (!plugin.isActive()) {
            throw new IllegalStateException("Plugin de sistema " + systemId + " está inactivo");
        }
        
        // Calcular KPIs
        List<KpiValue> kpis = kpiCalculator.calculateKpis(plugin, date);
        
        // Construir respuesta
        DashboardKpiDto dto = new DashboardKpiDto();
        dto.setSystemId(systemId);
        dto.setSystemName(plugin.getDisplayName());
        dto.setDate(date.toString());
        dto.setKpis(kpis);
        dto.setTotalKpis(kpis.size());
        
        return dto;
    }
}

@Data
class DashboardKpiDto {
    private String systemId;
    private String systemName;
    private String date;
    private List<KpiValue> kpis;
    private Integer totalKpis;
}
```

### 3. Controller

```java
package backlogs.dinamico.controller.analytics;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.service.analytics.DashboardKpiService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Analytics - KPIs", description = "KPIs dinámicos por sistema")
@RestController
@RequestMapping(value = "/api/analytics/kpis", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class KpiController {

    private final DashboardKpiService dashboardKpiService;

    @GetMapping("/{systemId}")
    @PreAuthorize("hasAnyAuthority('ORG_OWNER', 'ORG_ADMIN', 'VIEWER')")
    public ApiResponse<DashboardKpiDto> getSystemKpis(
        Authentication auth,
        @PathVariable String systemId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
            LocalDate date
    ) {
        ObjectId tenantId = resolveTenantId(auth);
        LocalDate targetDate = date != null ? date : LocalDate.now();
        
        DashboardKpiDto kpis = dashboardKpiService.getDashboardKpis(
            tenantId, 
            systemId.toUpperCase(), 
            targetDate
        );
        
        return ApiResponse.ok(
            "KPIs del sistema " + systemId,
            "kpis_success",
            kpis
        );
    }

    private ObjectId resolveTenantId(Authentication auth) {
        return new ObjectId(); // Implementar
    }
}
```

---

## 🎨 FRONTEND EXAMPLE (React)

```jsx
// DashboardKpis.jsx
import React, { useEffect, useState } from 'react';
import axios from 'axios';

export const DashboardKpis = ({ systemId }) => {
  const [kpis, setKpis] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadKpis();
  }, [systemId]);

  const loadKpis = async () => {
    try {
      const response = await axios.get(`/api/analytics/kpis/${systemId}`, {
        headers: { Authorization: `Bearer ${getToken()}` }
      });
      setKpis(response.data.data);
    } catch (error) {
      console.error('Error loading KPIs:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) return <div>Cargando KPIs...</div>;
  if (!kpis) return <div>No hay KPIs configurados</div>;

  return (
    <div className="dashboard-kpis">
      <h2>{kpis.systemName}</h2>
      <p className="date">Fecha: {kpis.date}</p>
      
      <div className="kpis-grid">
        {kpis.kpis.map(kpi => (
          <KpiCard key={kpi.id} kpi={kpi} />
        ))}
      </div>
    </div>
  );
};

const KpiCard = ({ kpi }) => {
  const renderValue = () => {
    if (kpi.error) {
      return <span className="error">{kpi.error}</span>;
    }

    switch (kpi.displayType) {
      case 'NUMBER_CARD':
        return <h3 className="value">{formatNumber(kpi.value)}</h3>;
      
      case 'PERCENTAGE':
        const percentage = kpi.target 
          ? (kpi.value / kpi.target * 100).toFixed(1)
          : kpi.value;
        return (
          <div>
            <h3 className="value">{percentage}%</h3>
            {kpi.target && (
              <p className="target">Meta: {kpi.target}</p>
            )}
          </div>
        );
      
      case 'DURATION':
        return <h3 className="value">{formatDuration(kpi.value)}</h3>;
      
      default:
        return <h3 className="value">{kpi.value}</h3>;
    }
  };

  return (
    <div className="kpi-card">
      <div className="kpi-header">
        <span className="icon">{kpi.icon}</span>
        <h4>{kpi.name}</h4>
      </div>
      {renderValue()}
      {kpi.description && (
        <p className="description">{kpi.description}</p>
      )}
    </div>
  );
};

function formatNumber(value) {
  return new Intl.NumberFormat('es-MX').format(value);
}

function formatDuration(seconds) {
  const minutes = Math.floor(seconds / 60);
  return `${minutes} min`;
}

function getToken() {
  return localStorage.getItem('auth_token');
}
```

---

## ✅ CHECKLIST ENTREGABLE 1.3

- [ ] `KpiCalculatorService` implementado
- [ ] Soporta operaciones: COUNT, COUNT_DISTINCT, AVG, SUM
- [ ] Parser de queries simple funciona
- [ ] `DashboardKpiService` implementado
- [ ] Controller `/api/analytics/kpis/{systemId}` creado
- [ ] Probado con KPIs de TRUSTVALUE
- [ ] Probado con KPIs de TICKETS
- [ ] Frontend renderiza KPIs correctamente
- [ ] KPIs se actualizan automáticamente

**Tiempo:** 5-7 días

---

## 🧪 TESTING

### Caso de Prueba 1: TRUSTVALUE - Asistencias del día

**Plugin KPI:**
```json
{
  "id": "asistencias_dia",
  "query": "COUNT WHERE eventType='ASISTENCIA_MARCADA' AND date=TODAY"
}
```

**Pasos:**
1. Crear 45 logs de `ASISTENCIA_MARCADA` hoy
2. Llamar `GET /api/analytics/kpis/TRUSTVALUE`
3. Verificar que KPI `asistencias_dia` retorna `value: 45`

### Caso de Prueba 2: TICKETS - Tickets pendientes

**Plugin KPI:**
```json
{
  "id": "tickets_pendientes",
  "query": "COUNT_DISTINCT caseId WHERE status='PENDING'"
}
```

**Pasos:**
1. Crear logs de 25 tickets diferentes con `status=PENDING`
2. Llamar API
3. Verificar `value: 25`

---

## 🎉 FIN DE FASE 1

Con esto completamos los 3 entregables de la Fase 1:

✅ **1.1 Plugin Engine** - Cargar y gestionar plugins  
✅ **1.2 Analytics Engine** - Detectar anomalías automáticamente  
✅ **1.3 KPIs Dinámicos** - Dashboard configurable por sistema  

**Próximo:** Fase 2 - Eva Context-Aware

