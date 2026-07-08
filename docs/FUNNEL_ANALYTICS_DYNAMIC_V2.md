# 📊 Funnel Analytics v2.0 - Arquitectura Dinámica

## 🎯 **Resumen Ejecutivo**

**Fecha de Refactorización:** 2026-06-16  
**Versión:** 2.0 (Arquitectura Dinámica)  
**Estado:** ✅ COMPLETADO Y OPERACIONAL

---

## 🔄 **¿Qué Cambió?**

### **❌ ANTES (v1.0 - Hardcoded)**

```java
// Sistemas hardcoded en código Java
private static final Map<String, FunnelDefinition> FUNNEL_CONFIGS = Map.of(
    "TRUSTVALUE", new FunnelDefinition(...),
    "CITA_GUYANA", new FunnelDefinition(...),
    // ❌ Para agregar un sistema: Editar código + Recompilar + Redesplegar
);
```

**Problemas:**
- ❌ Sistemas grabados a fuego en código Java
- ❌ Eventos inventados que no existen en BD
- ❌ Requerir recompilación para agregar sistemas
- ❌ Bug 404 en ruta `/api/analytics/funnel/available-systems`
- ❌ No escalable para multi-tenant

### **✅ AHORA (v2.0 - Dinámico)**

```javascript
// Configuración en MongoDB (colección: funnel_templates)
db.funnel_templates.insertOne({
  "systemName": "TRUSTVALUE",
  "funnelName": "Flujo de Jornada Laboral",
  "active": true,
  "steps": [
    { "order": 1, "eventType": "INICIO_SESION", "label": "Inicio de Sesión" },
    { "order": 2, "eventType": "SELECCION_SUCURSAL", "label": "Selección de Sucursal" },
    // ✅ Solo insertar en BD - Sin recompilar
  ]
});
```

**Beneficios:**
- ✅ 100% dinámico desde MongoDB
- ✅ Agregar sistemas sin recompilar
- ✅ Rutas corregidas (bug 404 solucionado)
- ✅ Flujos basados en eventos REALES de la BD
- ✅ Preparado para multi-tenant

---

## 🏗️ **Arquitectura Nueva**

### **Entidades Creadas**

#### 1. **FunnelTemplate** (MongoDB Entity)
```java
@Document("funnel_templates")
public class FunnelTemplate {
    @Id private ObjectId id;
    
    @Indexed(unique = true)
    private String systemName;       // ej: "TRUSTVALUE"
    
    private String funnelName;        // ej: "Flujo de Jornada Laboral"
    private List<FunnelStepDefinition> steps;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String description;
}
```

**Ubicación:** `backlogs.dinamico.model.analytics.FunnelTemplate`

#### 2. **FunnelTemplateRepository** (Spring Data Repository)
```java
@Repository
public interface FunnelTemplateRepository extends MongoRepository<FunnelTemplate, ObjectId> {
    Optional<FunnelTemplate> findBySystemNameAndActive(String systemName, Boolean active);
    List<FunnelTemplate> findByActive(Boolean active);
    boolean existsBySystemNameAndActive(String systemName, Boolean active);
}
```

**Ubicación:** `backlogs.dinamico.repository.analytics.FunnelTemplateRepository`

---

## 🔧 **Cambios en el Servicio**

### **FunnelAnalyticsService** - Refactorización Completa

**ANTES:**
```java
@RequiredArgsConstructor
public class FunnelAnalyticsService {
    private final FunnelStepsConfig funnelStepsConfig;  // ❌ Hardcoded
    
    public FunnelResponseDto calculateFunnel(...) {
        FunnelDefinition def = funnelStepsConfig.getFunnelDefinition(systemName);
        // ...
    }
}
```

**AHORA:**
```java
@RequiredArgsConstructor
public class FunnelAnalyticsService {
    private final FunnelTemplateRepository funnelTemplateRepository;  // ✅ Dinámico
    
    public FunnelResponseDto calculateFunnel(...) {
        FunnelTemplate template = funnelTemplateRepository
            .findBySystemNameAndActive(normalizedSystem, true)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ...));
        // ✅ Lee desde MongoDB en tiempo real
    }
}
```

**Cambios específicos:**
- ✅ Eliminada dependencia de `FunnelStepsConfig`
- ✅ Agregada dependencia de `FunnelTemplateRepository`
- ✅ Método `calculateFunnel()` lee desde MongoDB
- ✅ Método `getAvailableSystems()` consulta BD en tiempo real

---

## 🚦 **Corrección del Bug 404 en Rutas**

### **Problema Original**

```
GET /api/analytics/funnel/available-systems  ❌ 404 Not Found
GET /api/analytics/funnel/TRUSTVALUE         ✅ 200 OK
```

**Causa:** Spring Boot confundía `/funnel/available-systems` con `/funnel/{systemName}` donde `systemName = "available-systems"`.

### **Solución Implementada**

**ANTES:**
```java
@GetMapping("/funnel/{systemName}")          // Parametrizado
@GetMapping("/funnel/available-systems")     // ❌ Conflicto!
```

**AHORA:**
```java
@GetMapping("/funnel/{systemName}")          // Parametrizado
@GetMapping("/funnel-systems/available")     // ✅ Ruta separada, sin conflicto
```

### **Nuevos Endpoints**

| Método | Ruta | Descripción | Status |
|--------|------|-------------|--------|
| `GET` | `/api/analytics/funnel/{systemName}` | Calcula el embudo de conversión | ✅ Operacional |
| `GET` | `/api/analytics/funnel-systems/available` | Lista sistemas disponibles | ✅ Operacional |

---

## 📦 **Sistemas Pre-Configurados**

Los siguientes sistemas están listos para usar tras ejecutar el script de seed:

### 1. **TRUSTVALUE** - Flujo de Jornada Laboral ⭐ NUEVO
```javascript
{
  "systemName": "TRUSTVALUE",
  "funnelName": "Flujo de Jornada Laboral",
  "steps": [
    { "order": 1, "eventType": "INICIO_SESION", "label": "Inicio de Sesión" },
    { "order": 2, "eventType": "SELECCION_SUCURSAL", "label": "Selección de Sucursal" },
    { "order": 3, "eventType": "ENVIAR_EVIDENCIAS", "label": "Envío de Evidencias" },
    { "order": 4, "eventType": "FINALIZAR_ASISTENCIA", "label": "Cierre de Jornada" }
  ]
}
```

### 2. **CITA_GUYANA** - Flujo de Tramitación de Citas
```javascript
{
  "systemName": "CITA_GUYANA",
  "funnelName": "Flujo de Trámite de Cita",
  "steps": [
    { "order": 1, "eventType": "AUTH_LOGIN", "label": "Inicio de Sesión" },
    { "order": 2, "eventType": "CONSULTA_ESTADO_REGISTRO", "label": "Validación de Registro" },
    { "order": 3, "eventType": "RESERVA_DE_CITA", "label": "Cita Completada" }
  ]
}
```

### 3. **TICKETS** - Flujo de Atención de Tickets
### 4. **PASSPORT** - Flujo de Tramitación de Pasaportes
### 5. **CITA_QUINTANAROO** - Flujo de Citas Quintana Roo

**Ver:** `docs/FUNNEL_TEMPLATES_SEED.md` para la lista completa.

---

## 🚀 **Instalación y Configuración**

### **Paso 1: Insertar Templates en MongoDB**

#### **Opción A: Script PowerShell Automatizado (Recomendado)**

```powershell
# Ejecutar desde el directorio raíz del proyecto
.\insert-funnel-templates.ps1

# Con opciones avanzadas
.\insert-funnel-templates.ps1 -MongoUri "mongodb://localhost:27017" -Database "logs_system" -DeleteExisting -Verbose
```

#### **Opción B: MongoDB Shell Manual**

```bash
mongosh "mongodb://localhost:27017/logs_system"
```

Luego copiar y pegar el script desde `docs/FUNNEL_TEMPLATES_SEED.md`.

### **Paso 2: Compilar el Proyecto**

```powershell
.\mvnw.cmd clean package -DskipTests
```

### **Paso 3: Iniciar el Servidor**

```powershell
.\mvnw.cmd spring-boot:run
```

### **Paso 4: Verificar Instalación**

```bash
# Listar sistemas disponibles
curl http://localhost:8040/api/analytics/funnel-systems/available \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "X-Tenant: quintanaroo"

# Respuesta esperada:
{
  "status": "ok",
  "data": ["CITA_GUYANA", "CITA_QUINTANAROO", "PASSPORT", "TICKETS", "TRUSTVALUE"]
}
```

```bash
# Probar análisis de funnel de TRUSTVALUE
curl http://localhost:8040/api/analytics/funnel/TRUSTVALUE \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "X-Tenant: quintanaroo"
```

---

## 📊 **Ejemplo de Respuesta Completa**

### **Request:**
```http
GET /api/analytics/funnel/TRUSTVALUE?from=2026-06-01T00:00:00Z&to=2026-06-16T23:59:59Z
Authorization: Bearer eyJhbGc...
X-Tenant: quintanaroo
```

### **Response:**
```json
{
  "status": "ok",
  "message": "Funnel analysis completed",
  "messageKey": "funnel_analysis",
  "data": {
    "system": "TRUSTVALUE",
    "funnelName": "Flujo de Jornada Laboral",
    "summary": {
      "totalStarted": 250,
      "totalCompleted": 180,
      "globalConversionRate": 72.0
    },
    "steps": [
      {
        "step": 1,
        "label": "Inicio de Sesión",
        "eventType": "INICIO_SESION",
        "count": 250,
        "conversionRate": 100.0,
        "dropRate": 0.0
      },
      {
        "step": 2,
        "label": "Selección de Sucursal",
        "eventType": "SELECCION_SUCURSAL",
        "count": 220,
        "conversionRate": 88.0,
        "dropRate": 12.0
      },
      {
        "step": 3,
        "label": "Envío de Evidencias",
        "eventType": "ENVIAR_EVIDENCIAS",
        "count": 200,
        "conversionRate": 80.0,
        "dropRate": 9.1
      },
      {
        "step": 4,
        "label": "Cierre de Jornada",
        "eventType": "FINALIZAR_ASISTENCIA",
        "count": 180,
        "conversionRate": 72.0,
        "dropRate": 10.0
      }
    ]
  }
}
```

---

## 🔧 **Agregar un Nuevo Sistema (Sin Recompilar)**

### **Paso 1: Insertar Template en MongoDB**

```javascript
db.funnel_templates.insertOne({
  "systemName": "MI_NUEVO_SISTEMA",
  "funnelName": "Flujo de Mi Sistema",
  "description": "Descripción del flujo",
  "active": true,
  "createdAt": new Date(),
  "updatedAt": new Date(),
  "createdBy": "admin",
  "steps": [
    {
      "order": 1,
      "eventType": "EVENTO_INICIAL",
      "label": "Primer Paso",
      "description": "Usuario inicia el proceso"
    },
    {
      "order": 2,
      "eventType": "EVENTO_INTERMEDIO",
      "label": "Paso Intermedio",
      "description": "Usuario completa validaciones"
    },
    {
      "order": 3,
      "eventType": "EVENTO_FINAL",
      "label": "Paso Final",
      "description": "Proceso completado"
    }
  ]
});
```

### **Paso 2: ¡Listo! Usar de Inmediato**

```bash
# Sin reiniciar el servidor
curl http://localhost:8040/api/analytics/funnel/MI_NUEVO_SISTEMA \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

**El sistema lee la configuración en tiempo real desde MongoDB.**

---

## 🗂️ **Archivos Modificados/Creados**

### **Archivos Nuevos (4 archivos)**

| Archivo | Ubicación | Propósito |
|---------|-----------|-----------|
| `FunnelTemplate.java` | `src/main/java/backlogs/dinamico/model/analytics/` | Entidad MongoDB |
| `FunnelTemplateRepository.java` | `src/main/java/backlogs/dinamico/repository/analytics/` | Repositorio Spring Data |
| `FUNNEL_TEMPLATES_SEED.md` | `docs/` | Documentación de seed data |
| `insert-funnel-templates.ps1` | Raíz del proyecto | Script automatizado de inserción |

### **Archivos Modificados (3 archivos)**

| Archivo | Cambios Principales |
|---------|---------------------|
| `FunnelAnalyticsService.java` | ✅ Lee desde `FunnelTemplateRepository`<br>❌ Eliminada dep. de `FunnelStepsConfig` |
| `AnalyticsController.java` | ✅ Nueva ruta `/funnel-systems/available`<br>❌ Corregido bug 404 |
| `FunnelStepsConfig.java` | ⚠️ DEPRECADO (mantener para referencia) |

---

## ⚠️ **Clase Deprecada**

### **FunnelStepsConfig.java**

```java
/**
 * @deprecated Esta clase está DEPRECADA desde 2026-06-16.
 * Usar FunnelTemplate (MongoDB) para configuración dinámica.
 */
@Deprecated(since = "2026-06-16", forRemoval = true)
@Component
public class FunnelStepsConfig {
    // ... código deprecado
}
```

**Razón de Deprecación:**
- Sistemas hardcoded en código Java
- Inflexible para cambios dinámicos
- Requerir recompilación para actualizaciones

**Migración:**
- Usar `FunnelTemplateRepository.findBySystemNameAndActive()`
- Insertar configuraciones en `funnel_templates` (MongoDB)

---

## 🔍 **Queries MongoDB Útiles**

### **Listar Todos los Sistemas Activos**
```javascript
db.funnel_templates.find(
  { active: true },
  { systemName: 1, funnelName: 1, _id: 0 }
).sort({ systemName: 1 });
```

### **Desactivar un Sistema sin Eliminarlo**
```javascript
db.funnel_templates.updateOne(
  { systemName: "TICKETS" },
  { $set: { active: false, updatedAt: new Date() } }
);
```

### **Actualizar Steps de un Sistema**
```javascript
db.funnel_templates.updateOne(
  { systemName: "TRUSTVALUE" },
  {
    $set: {
      steps: [
        { "order": 1, "eventType": "NUEVO_EVENTO_1", "label": "Nuevo Paso 1" },
        { "order": 2, "eventType": "NUEVO_EVENTO_2", "label": "Nuevo Paso 2" }
      ],
      updatedAt: new Date()
    }
  }
);
```

### **Verificar Índice Único**
```javascript
db.funnel_templates.getIndexes();
// Debe mostrar índice en { "systemName": 1 } con unique: true
```

---

## 🧪 **Testing**

### **Test 1: Listar Sistemas**
```bash
curl -X GET "http://localhost:8040/api/analytics/funnel-systems/available" \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "X-Tenant: quintanaroo"
```

**Resultado Esperado:**
```json
{
  "status": "ok",
  "data": ["CITA_GUYANA", "CITA_QUINTANAROO", "PASSPORT", "TICKETS", "TRUSTVALUE"]
}
```

### **Test 2: Análisis de Funnel**
```bash
curl -X GET "http://localhost:8040/api/analytics/funnel/TRUSTVALUE" \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "X-Tenant: quintanaroo"
```

### **Test 3: Sistema No Existente**
```bash
curl -X GET "http://localhost:8040/api/analytics/funnel/SISTEMA_INEXISTENTE" \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "X-Tenant: quintanaroo"
```

**Resultado Esperado:**
```json
{
  "status": "error",
  "message": "No active funnel template found for system: SISTEMA_INEXISTENTE"
}
```

---

## 📈 **Métricas y KPIs**

El endpoint calcula automáticamente:

### **Por Paso:**
- `count`: Cantidad de `caseId` únicos que completaron el paso
- `conversionRate`: % respecto al primer paso (paso 1 = 100%)
- `dropRate`: % de abandono respecto al paso anterior

### **Resumen Global:**
- `totalStarted`: Total de casos que iniciaron (paso 1)
- `totalCompleted`: Total que completaron todos los pasos
- `globalConversionRate`: % de casos que completaron todo el flujo

### **Fórmulas:**

```
conversionRate(step) = (count(step) / count(step1)) * 100
dropRate(step) = ((count(step-1) - count(step)) / count(step-1)) * 100
globalConversionRate = (totalCompleted / totalStarted) * 100
```

---

## 🎯 **Casos de Uso Estratégicos**

### **1. Identificar Cuellos de Botella**
```
Si dropRate > 30% en algún paso
→ Investigar y optimizar ese paso específico
```

### **2. Comparar Sistemas**
```bash
# Analizar múltiples sistemas
curl .../funnel/TRUSTVALUE
curl .../funnel/CITA_GUYANA
curl .../funnel/TICKETS

# Comparar globalConversionRate
# Sistema con menor conversión requiere atención
```

### **3. Análisis Temporal**
```bash
# Semana 1
curl .../funnel/TRUSTVALUE?from=2026-06-01T00:00:00Z&to=2026-06-07T23:59:59Z

# Semana 2
curl .../funnel/TRUSTVALUE?from=2026-06-08T00:00:00Z&to=2026-06-14T23:59:59Z

# Comparar tendencias
```

---

## 🔐 **Seguridad**

### **Autenticación:**
- JWT Token obligatorio (`Authorization: Bearer ...`)
- Permiso requerido: `PERM_LOG_READ`

### **Multi-Tenant:**
- Header obligatorio: `X-Tenant: {tenant}`
- Filtrado automático por `tenant_id` en MongoDB
- Aislamiento completo de datos

### **Autorización:**
- Validación de acceso al sistema vía `ScopeGuard.requireSystemAccess()`
- Usuario debe tener permisos para el sistema solicitado

---

## 📚 **Referencias y Documentación**

### **Documentos Relacionados:**
- `docs/FUNNEL_ANALYTICS.md` - Documentación original (v1.0)
- `docs/FUNNEL_TEMPLATES_SEED.md` - Scripts de seed data
- `docs/FUNNEL_ANALYTICS_RESUMEN.md` - Resumen ejecutivo

### **Archivos de Código:**
- `FunnelTemplate.java` - Entidad MongoDB
- `FunnelTemplateRepository.java` - Repositorio Spring Data
- `FunnelAnalyticsService.java` - Lógica de negocio
- `AnalyticsController.java` - Endpoints REST

### **Scripts Útiles:**
- `insert-funnel-templates.ps1` - Inserción automatizada
- `test-funnel-analytics.ps1` - Testing automatizado

---

## 🚀 **Próximos Pasos**

### **Mejoras Futuras:**

1. **Multi-Tenant Templates:**
   - Agregar campo `tenantId` a `FunnelTemplate`
   - Permitir configuraciones personalizadas por tenant

2. **API de Gestión:**
   - `POST /api/analytics/funnel-template` - Crear template
   - `PUT /api/analytics/funnel-template/{id}` - Actualizar
   - `DELETE /api/analytics/funnel-template/{id}` - Eliminar

3. **Versionamiento:**
   - Agregar campo `version` a templates
   - Mantener histórico de configuraciones

4. **Validaciones:**
   - Validar que `eventType` exista en `log_events`
   - Alertar si un paso no tiene datos

5. **Dashboard UI:**
   - Interface gráfica para gestionar templates
   - Visualización de embudos en tiempo real

---

## ✅ **Checklist de Validación**

- [x] Compilación exitosa sin errores
- [x] Templates insertados en MongoDB
- [x] Endpoint `/funnel-systems/available` funcional
- [x] Endpoint `/funnel/{systemName}` funcional
- [x] Bug 404 corregido
- [x] Clase `FunnelStepsConfig` deprecada
- [x] Documentación completa creada
- [x] Scripts de seed automatizados
- [x] Tests manuales exitosos

---

## 📞 **Soporte**

**Email:** soporte.tecnico@grupo-santoro.com.mx  
**Documentación:** `docs/FUNNEL_ANALYTICS_DYNAMIC_V2.md`  
**Swagger UI:** `http://localhost:8040/swagger-ui.html`  
**Script de Seed:** `insert-funnel-templates.ps1`

---

**Última actualización:** 2026-06-16  
**Versión:** 2.0 (Arquitectura Dinámica)  
**Estado:** ✅ PRODUCCIÓN

