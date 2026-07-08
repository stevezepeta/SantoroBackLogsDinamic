# ⚡ PLAN DE IMPLEMENTACIÓN - Sistema Modular de Plugins

> **3 Fases Claras** para transformar el sistema en plataforma universal escalable

---

## 📋 RESUMEN EJECUTIVO

### ¿Qué vamos a hacer?

Convertir el sistema actual de logs en una **PLATAFORMA MODULAR** donde:

✅ Cada sistema (TRUSTVALUE, TICKETS, futuros) se configura con un **Plugin JSON**  
✅ Sin modificar código para agregar nuevos sistemas  
✅ Eva entiende el contexto de cada sistema automáticamente  
✅ Dashboard se personaliza según el sistema seleccionado  

### Beneficios

- ⏱️ **Agregar nuevo sistema:** De 2 semanas → 1 hora
- 🔧 **Mantenimiento:** Solo actualizar JSON (no código)
- 📊 **KPIs:** Específicos por negocio (no genéricos)
- 🤖 **Eva:** Explica en términos de negocio
- ♾️ **Escalabilidad:** Ilimitada

---

## 🎯 FASE 1: FUNDAMENTOS (2-3 semanas)

### Objetivo
Plugin Engine funcionando + Detección configurable + KPIs dinámicos

---

### 📦 ENTREGABLE 1.1: PLUGIN ENGINE (Semana 1)

**Tiempo estimado:** 5-7 días

#### Archivos a crear:

##### 1. Modelo de Plugin

```java
package backlogs.dinamico.model.plugin;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Data
@Document(collection = "system_plugins")
public class SystemPlugin {
    
    @Id
    private ObjectId id;
    
    private ObjectId tenantId;
    
    private String systemId;        // "TRUSTVALUE", "TICKETS"
    private String displayName;     // "TrustValue - Asistencias"
    private String type;            // "MOBILE_APP", "WEB_APP", "IOT"
    private String version;
    private boolean active;
    
    // Definición de eventos del sistema
    private Map<String, EventTypeDefinition> eventTypes;
    
    // KPIs configurados
    private List<KpiDefinition> kpis;
    
    // Reglas de detección de anomalías
    private List<AnomalyRule> anomalyRules;
    
    // Widgets del dashboard
    private List<DashboardWidget> dashboardWidgets;
    
    private java.time.Instant createdAt;
    private java.time.Instant updatedAt;
}

@Data
class EventTypeDefinition {
    private String displayName;
    private String category;      // AUTHENTICATION, CORE_OPERATION, DATA_ACCESS
    private String icon;
    private String businessImpact; // LOW, MEDIUM, HIGH
    private boolean sensitive;
    private List<String> requiredFields;
}

@Data
class KpiDefinition {
    private String id;
    private String name;
    private String description;
    private String query;          // Lenguaje simple de query
    private String displayType;    // NUMBER_CARD, PERCENTAGE, CHART
    private String icon;
    private Integer target;        // Valor objetivo (opcional)
}

@Data
class AnomalyRule {
    private String id;
    private String name;
    private String condition;      // Expresión lógica
    private Integer riskScore;     // 0-100
    private String severity;       // LOW, MEDIUM, HIGH, CRITICAL
    private String description;
    private List<String> recommendations;
}

@Data
class DashboardWidget {
    private String type;           // MAP_HEATMAP, TIME_SERIES, TOP_LOCATIONS
    private String title;
    private String dataSource;     // Query simple
    private Map<String, Object> config;
}
```

##### 2. Repository

```java
package backlogs.dinamico.repository.plugin;

import backlogs.dinamico.model.plugin.SystemPlugin;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SystemPluginRepository extends MongoRepository<SystemPlugin, ObjectId> {
    
    Optional<SystemPlugin> findBySystemIdAndTenantId(String systemId, ObjectId tenantId);
    
    List<SystemPlugin> findByTenantIdAndActiveTrue(ObjectId tenantId);
    
    boolean existsBySystemIdAndTenantId(String systemId, ObjectId tenantId);
}
```

##### 3. Plugin Service

```java
package backlogs.dinamico.service.plugin;

import backlogs.dinamico.model.plugin.SystemPlugin;
import backlogs.dinamico.repository.plugin.SystemPluginRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PluginService {

    private final SystemPluginRepository pluginRepository;
    private final ObjectMapper objectMapper;
    private final PluginValidator pluginValidator;
    
    /**
     * Cargar plugin desde archivo JSON
     */
    public SystemPlugin loadPluginFromFile(String filePath, ObjectId tenantId) 
            throws IOException {
        
        File file = new File(filePath);
        SystemPlugin plugin = objectMapper.readValue(file, SystemPlugin.class);
        
        // Validar estructura
        pluginValidator.validate(plugin);
        
        // Asignar tenant
        plugin.setTenantId(tenantId);
        plugin.setActive(true);
        plugin.setCreatedAt(Instant.now());
        plugin.setUpdatedAt(Instant.now());
        
        // Verificar si ya existe
        if (pluginRepository.existsBySystemIdAndTenantId(
                plugin.getSystemId(), tenantId)) {
            throw new IllegalStateException(
                "Plugin para sistema " + plugin.getSystemId() + " ya existe"
            );
        }
        
        // Guardar
        SystemPlugin saved = pluginRepository.save(plugin);
        
        log.info("Plugin {} cargado exitosamente para tenant {}", 
            plugin.getSystemId(), tenantId);
        
        return saved;
    }
    
    /**
     * Obtener plugin activo de un sistema
     */
    public Optional<SystemPlugin> getActivePlugin(String systemId, ObjectId tenantId) {
        return pluginRepository.findBySystemIdAndTenantId(systemId, tenantId)
            .filter(SystemPlugin::isActive);
    }
    
    /**
     * Listar todos los plugins activos del tenant
     */
    public List<SystemPlugin> listActivePlugins(ObjectId tenantId) {
        return pluginRepository.findByTenantIdAndActiveTrue(tenantId);
    }
    
    /**
     * Actualizar plugin
     */
    public SystemPlugin updatePlugin(ObjectId pluginId, SystemPlugin updates) {
        SystemPlugin existing = pluginRepository.findById(pluginId)
            .orElseThrow(() -> new IllegalArgumentException("Plugin not found"));
        
        // Validar updates
        pluginValidator.validate(updates);
        
        // Actualizar campos
        existing.setEventTypes(updates.getEventTypes());
        existing.setKpis(updates.getKpis());
        existing.setAnomalyRules(updates.getAnomalyRules());
        existing.setDashboardWidgets(updates.getDashboardWidgets());
        existing.setUpdatedAt(Instant.now());
        
        return pluginRepository.save(existing);
    }
    
    /**
     * Desactivar plugin
     */
    public void deactivatePlugin(ObjectId pluginId) {
        SystemPlugin plugin = pluginRepository.findById(pluginId)
            .orElseThrow(() -> new IllegalArgumentException("Plugin not found"));
        
        plugin.setActive(false);
        plugin.setUpdatedAt(Instant.now());
        pluginRepository.save(plugin);
        
        log.info("Plugin {} desactivado", plugin.getSystemId());
    }
}
```

##### 4. Plugin Validator

```java
package backlogs.dinamico.service.plugin;

import backlogs.dinamico.model.plugin.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class PluginValidator {
    
    public void validate(SystemPlugin plugin) {
        
        // Validar campos requeridos
        if (!StringUtils.hasText(plugin.getSystemId())) {
            throw new IllegalArgumentException("systemId es requerido");
        }
        
        if (!StringUtils.hasText(plugin.getDisplayName())) {
            throw new IllegalArgumentException("displayName es requerido");
        }
        
        // Validar event types
        if (plugin.getEventTypes() == null || plugin.getEventTypes().isEmpty()) {
            throw new IllegalArgumentException("Debe definir al menos 1 eventType");
        }
        
        // Validar KPIs
        if (plugin.getKpis() != null) {
            for (KpiDefinition kpi : plugin.getKpis()) {
                validateKpi(kpi);
            }
        }
        
        // Validar reglas de anomalías
        if (plugin.getAnomalyRules() != null) {
            for (AnomalyRule rule : plugin.getAnomalyRules()) {
                validateAnomalyRule(rule);
            }
        }
    }
    
    private void validateKpi(KpiDefinition kpi) {
        if (!StringUtils.hasText(kpi.getId())) {
            throw new IllegalArgumentException("KPI id es requerido");
        }
        if (!StringUtils.hasText(kpi.getQuery())) {
            throw new IllegalArgumentException("KPI query es requerido");
        }
    }
    
    private void validateAnomalyRule(AnomalyRule rule) {
        if (!StringUtils.hasText(rule.getId())) {
            throw new IllegalArgumentException("Anomaly rule id es requerido");
        }
        if (!StringUtils.hasText(rule.getCondition())) {
            throw new IllegalArgumentException("Anomaly rule condition es requerido");
        }
        if (rule.getRiskScore() == null || rule.getRiskScore() < 0 || rule.getRiskScore() > 100) {
            throw new IllegalArgumentException("Risk score debe estar entre 0 y 100");
        }
    }
}
```

##### 5. Controller

```java
package backlogs.dinamico.controller.plugin;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.model.plugin.SystemPlugin;
import backlogs.dinamico.service.plugin.PluginService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Plugins", description = "Gestión de plugins de sistemas")
@RestController
@RequestMapping(value = "/api/plugins", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PluginController {

    private final PluginService pluginService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<List<SystemPlugin>> listPlugins(Authentication auth) {
        ObjectId tenantId = resolveTenantId(auth);
        List<SystemPlugin> plugins = pluginService.listActivePlugins(tenantId);
        return ApiResponse.ok("Plugins activos", "plugins_list", plugins);
    }
    
    @GetMapping("/{systemId}")
    @PreAuthorize("hasAnyAuthority('ORG_OWNER', 'ORG_ADMIN', 'VIEWER')")
    public ApiResponse<SystemPlugin> getPlugin(
        Authentication auth,
        @PathVariable String systemId
    ) {
        ObjectId tenantId = resolveTenantId(auth);
        SystemPlugin plugin = pluginService.getActivePlugin(systemId, tenantId)
            .orElseThrow(() -> new RuntimeException("Plugin no encontrado"));
        return ApiResponse.ok("Plugin", "plugin_detail", plugin);
    }
    
    @PostMapping("/upload")
    @PreAuthorize("hasAnyAuthority('ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<SystemPlugin> uploadPlugin(
        Authentication auth,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        
        ObjectId tenantId = resolveTenantId(auth);
        
        // Guardar temporalmente
        Path tempFile = Files.createTempFile("plugin-", ".json");
        file.transferTo(tempFile.toFile());
        
        // Cargar plugin
        SystemPlugin plugin = pluginService.loadPluginFromFile(
            tempFile.toString(), 
            tenantId
        );
        
        // Eliminar temporal
        Files.delete(tempFile);
        
        return ApiResponse.ok(
            "Plugin cargado exitosamente", 
            "plugin_uploaded", 
            plugin
        );
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<SystemPlugin> updatePlugin(
        @PathVariable ObjectId id,
        @RequestBody SystemPlugin updates
    ) {
        SystemPlugin updated = pluginService.updatePlugin(id, updates);
        return ApiResponse.ok("Plugin actualizado", "plugin_updated", updated);
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ORG_OWNER')")
    public ApiResponse<Void> deletePlugin(@PathVariable ObjectId id) {
        pluginService.deactivatePlugin(id);
        return ApiResponse.ok("Plugin desactivado", "plugin_deactivated", null);
    }

    private ObjectId resolveTenantId(Authentication auth) {
        // Implementar según tu lógica
        return new ObjectId();
    }
}
```

##### 6. Plugins Seed (Datos Iniciales)

Crear directorio: `src/main/resources/plugins/`

**`trustvalue-plugin.json`:**
```json
{
  "systemId": "TRUSTVALUE",
  "displayName": "TrustValue - Asistencias",
  "type": "MOBILE_APP",
  "version": "1.0",
  "eventTypes": {
    "AUTH_LOGIN": {
      "displayName": "Inicio de Sesión",
      "category": "AUTHENTICATION",
      "icon": "🔐",
      "businessImpact": "LOW",
      "sensitive": false
    },
    "ASISTENCIA_MARCADA": {
      "displayName": "Asistencia Registrada",
      "category": "CORE_OPERATION",
      "icon": "✅",
      "businessImpact": "HIGH",
      "sensitive": false,
      "requiredFields": ["location", "geo"]
    },
    "ENVIAR_EVIDENCIAS": {
      "displayName": "Evidencia Enviada",
      "category": "DOCUMENT_SUBMISSION",
      "icon": "📎",
      "businessImpact": "MEDIUM",
      "sensitive": false
    }
  },
  "kpis": [
    {
      "id": "asistencias_dia",
      "name": "Asistencias del Día",
      "description": "Total de asistencias marcadas hoy",
      "query": "COUNT WHERE eventType='ASISTENCIA_MARCADA' AND date=TODAY",
      "displayType": "NUMBER_CARD",
      "icon": "👥"
    },
    {
      "id": "sucursales_activas",
      "name": "Sucursales con Actividad",
      "description": "Número de sucursales donde se marcó asistencia",
      "query": "COUNT_DISTINCT location.name WHERE eventType='ASISTENCIA_MARCADA' AND date=TODAY",
      "displayType": "NUMBER_CARD",
      "icon": "🏢"
    }
  ],
  "anomalyRules": [
    {
      "id": "asistencia_fuera_horario",
      "name": "Asistencia fuera de horario laboral",
      "condition": "eventType='ASISTENCIA_MARCADA' AND (hour < 6 OR hour > 22)",
      "riskScore": 60,
      "severity": "MEDIUM",
      "description": "Empleado marcó asistencia fuera del horario normal (6 AM - 10 PM)",
      "recommendations": [
        "Verificar si el empleado tiene autorización para trabajar fuera de horario",
        "Revisar si la asistencia fue un error del sistema"
      ]
    },
    {
      "id": "asistencia_ubicacion_lejana",
      "name": "Asistencia desde ubicación muy lejana",
      "condition": "eventType='ASISTENCIA_MARCADA' AND geo.accuracyMeters > 1000",
      "riskScore": 75,
      "severity": "HIGH",
      "description": "Asistencia marcada con precisión GPS muy baja (>1km de error)",
      "recommendations": [
        "Contactar al empleado para confirmar ubicación real",
        "Revisar si el GPS del dispositivo funciona correctamente"
      ]
    }
  ],
  "dashboardWidgets": [
    {
      "type": "MAP_HEATMAP",
      "title": "Mapa de Asistencias",
      "dataSource": "geo.coordinates WHERE eventType='ASISTENCIA_MARCADA' AND date=TODAY",
      "config": {
        "centerLat": 19.4326,
        "centerLng": -99.1332,
        "zoom": 10
      }
    },
    {
      "type": "TIME_SERIES",
      "title": "Asistencias por Hora",
      "dataSource": "COUNT(eventType='ASISTENCIA_MARCADA') GROUP BY hour",
      "config": {
        "chartType": "bar"
      }
    }
  ]
}
```

**`tickets-plugin.json`:**
```json
{
  "systemId": "TICKETS",
  "displayName": "Sistema de Tickets de Soporte",
  "type": "WEB_APP",
  "version": "1.0",
  "eventTypes": {
    "CONSULTA_DE_CLIENTES": {
      "displayName": "Consulta de Cliente",
      "category": "DATA_ACCESS",
      "icon": "👤",
      "businessImpact": "LOW",
      "sensitive": true
    },
    "CREACION_DE_TICKET": {
      "displayName": "Ticket Creado",
      "category": "CORE_OPERATION",
      "icon": "🎫",
      "businessImpact": "HIGH",
      "sensitive": false
    },
    "RESOLUCION_TICKETS": {
      "displayName": "Ticket Resuelto",
      "category": "CORE_OPERATION",
      "icon": "✅",
      "businessImpact": "HIGH",
      "sensitive": false
    }
  },
  "kpis": [
    {
      "id": "tickets_resueltos_hoy",
      "name": "Tickets Resueltos Hoy",
      "description": "Número de tickets cerrados exitosamente",
      "query": "COUNT WHERE eventType='RESOLUCION_TICKETS' AND outcome='SUCCESS' AND date=TODAY",
      "displayType": "NUMBER_CARD",
      "icon": "✅"
    },
    {
      "id": "tickets_pendientes",
      "name": "Tickets Pendientes",
      "description": "Tickets sin resolver",
      "query": "COUNT_DISTINCT caseId WHERE status='PENDING'",
      "displayType": "NUMBER_CARD",
      "icon": "⏳"
    }
  ],
  "anomalyRules": [
    {
      "id": "acceso_masivo_datos",
      "name": "Consulta masiva de clientes",
      "condition": "COUNT(eventType='CONSULTA_DE_CLIENTES' per actor per hour) > 100",
      "riskScore": 85,
      "severity": "HIGH",
      "description": "Agente consultó datos de más de 100 clientes en 1 hora",
      "recommendations": [
        "Verificar si es una tarea legítima (generación de reporte)",
        "Revisar qué datos específicos consultó",
        "Contactar al agente para validar actividad"
      ]
    }
  ],
  "dashboardWidgets": [
    {
      "type": "AGENT_PERFORMANCE",
      "title": "Performance de Agentes",
      "dataSource": "tickets_resueltos, tiempo_promedio GROUP BY actor.username",
      "config": {
        "topN": 10
      }
    }
  ]
}
```

##### 7. Seed Service (Cargar plugins al inicio)

```java
package backlogs.dinamico.seed;

import backlogs.dinamico.service.plugin.PluginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;

@Slf4j
@Component
@RequiredArgsConstructor
public class PluginSeeder implements CommandLineRunner {

    private final PluginService pluginService;
    
    @Override
    public void run(String... args) {
        log.info("Cargando plugins seed...");
        
        try {
            // Cargar plugins de /resources/plugins/
            loadPluginIfNotExists("plugins/trustvalue-plugin.json");
            loadPluginIfNotExists("plugins/tickets-plugin.json");
            
            log.info("Plugins seed cargados exitosamente");
        } catch (Exception e) {
            log.error("Error cargando plugins seed: {}", e.getMessage());
        }
    }
    
    private void loadPluginIfNotExists(String resourcePath) {
        try {
            File file = new ClassPathResource(resourcePath).getFile();
            // Cargar para tenant por defecto (configurar según tu caso)
            ObjectId defaultTenantId = new ObjectId("64a1b2c3d4e5f678901234ab");
            
            pluginService.loadPluginFromFile(file.getAbsolutePath(), defaultTenantId);
            log.info("Plugin {} cargado", resourcePath);
        } catch (IllegalStateException e) {
            log.debug("Plugin {} ya existe, skip", resourcePath);
        } catch (Exception e) {
            log.error("Error cargando {}: {}", resourcePath, e.getMessage());
        }
    }
}
```

---

### ✅ Checklist Entregable 1.1

- [ ] Modelo `SystemPlugin` creado
- [ ] Repository creado
- [ ] `PluginService` implementado
- [ ] `PluginValidator` implementado
- [ ] Controller `/api/plugins` creado
- [ ] Archivos JSON de TRUSTVALUE y TICKETS creados
- [ ] Seed service para cargar plugins al inicio
- [ ] Probado: Cargar plugin vía API
- [ ] Probado: Listar plugins activos

**Tiempo:** 5-7 días

---

### 📊 ENTREGABLE 1.2: ANALYTICS ENGINE CONFIGURABLE (Semana 2)

**Tiempo estimado:** 5-7 días

Implementar motor que usa las `anomalyRules` de los plugins para detectar.

(Código en próximo documento)

---

### 📈 ENTREGABLE 1.3: KPIS DINÁMICOS (Semana 3)

**Tiempo estimado:** 5-7 días

Dashboard que calcula y muestra KPIs según el plugin del sistema.

(Código en próximo documento)

---

## 🚀 PRÓXIMOS PASOS

1. ✅ **Revisar y aprobar** este plan
2. ✅ **Confirmar prioridades:** ¿Empezar con Plugin Engine?
3. ✅ **Decidir:** ¿Necesitas ver código de Entregable 1.2 y 1.3 antes de empezar?

**¿Qué prefieres?**

A) Aprobar y empezar a implementar Entregable 1.1 (Plugin Engine)  
B) Ver primero todo el código completo de Fase 1  
C) Ajustar algo del plan antes

Cuando confirmes, continúo con los siguientes documentos técnicos.

