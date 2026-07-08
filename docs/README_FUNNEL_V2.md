# 🎉 Funnel Analytics v2.0 - Refactorización Completada

## ✅ **ESTADO: LISTO PARA PRODUCCIÓN**

**Fecha de Actualización:** 2026-06-16  
**Versión:** 2.0 (Arquitectura Dinámica)  
**Compilación:** ✅ BUILD SUCCESS

---

## 🎯 **¿Qué Cambió en Esta Versión?**

### **ANTES (v1.0) - Arquitectura Hardcoded ❌**
- Sistemas configurados en código Java estático
- Eventos inventados que no existían en la base de datos
- Bug 404 en rutas `/funnel/available-systems`
- Requerir recompilación para agregar nuevos sistemas

### **AHORA (v2.0) - Arquitectura Dinámica ✅**
- Configuraciones 100% dinámicas desde MongoDB
- Eventos REALES de la base de datos (flujo operativo de TRUSTVALUE)
- Bug 404 completamente corregido
- Agregar sistemas sin recompilar (insertar en BD y listo)

---

## 🚀 **INICIO RÁPIDO (3 Pasos)**

### **1. Insertar Templates en MongoDB**

```powershell
.\insert-funnel-templates.ps1
```

**Esto insertará 5 sistemas pre-configurados:**
- ✅ TRUSTVALUE - Flujo de Jornada Laboral (4 pasos reales)
- ✅ CITA_GUYANA - Flujo de Tramitación de Citas
- ✅ TICKETS - Flujo de Atención de Soporte
- ✅ PASSPORT - Flujo de Tramitación de Pasaportes
- ✅ CITA_QUINTANAROO - Flujo de Citas QR

### **2. Compilar y Iniciar el Servidor**

```powershell
# Compilar
.\mvnw.cmd clean package -DskipTests

# Iniciar servidor
.\mvnw.cmd spring-boot:run
```

### **3. Probar los Endpoints**

```powershell
# Ejecutar suite de pruebas
.\test-funnel-dynamic.ps1 -Token "YOUR_JWT_TOKEN" -Tenant "quintanaroo"
```

**O probar manualmente:**

```bash
# Listar sistemas disponibles
curl http://localhost:8040/api/analytics/funnel-systems/available \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "X-Tenant: quintanaroo"

# Analizar embudo de TRUSTVALUE
curl http://localhost:8040/api/analytics/funnel/TRUSTVALUE \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "X-Tenant: quintanaroo"
```

---

## 📊 **Endpoints Disponibles**

| Método | Ruta | Descripción | Autenticación |
|--------|------|-------------|---------------|
| `GET` | `/api/analytics/funnel-systems/available` | Lista sistemas con funnel configurado | JWT + `PERM_LOG_READ` |
| `GET` | `/api/analytics/funnel/{systemName}` | Calcula métricas de embudo | JWT + `PERM_LOG_READ` |
| `GET` | `/api/analytics/funnel/{systemName}?from=...&to=...` | Embudo con rango de fechas | JWT + `PERM_LOG_READ` |

---

## 🏗️ **Arquitectura Nueva (v2.0)**

### **Colección MongoDB: `funnel_templates`**

```javascript
{
  "_id": ObjectId("..."),
  "systemName": "TRUSTVALUE",              // Único (índice)
  "funnelName": "Flujo de Jornada Laboral",
  "active": true,
  "createdAt": ISODate("2026-06-16T..."),
  "updatedAt": ISODate("2026-06-16T..."),
  "createdBy": "system",
  "description": "Flujo completo de registro de asistencia",
  "steps": [
    {
      "order": 1,
      "eventType": "INICIO_SESION",
      "label": "Inicio de Sesión",
      "description": "Usuario inicia sesión en el sistema"
    },
    {
      "order": 2,
      "eventType": "SELECCION_SUCURSAL",
      "label": "Selección de Sucursal",
      "description": "Usuario selecciona la sucursal"
    },
    // ... más pasos
  ]
}
```

### **Entidades Java Creadas:**

```
src/main/java/backlogs/dinamico/
├── model/analytics/
│   └── FunnelTemplate.java              ✨ NUEVO
├── repository/analytics/
│   └── FunnelTemplateRepository.java    ✨ NUEVO
├── service/analytics/
│   └── FunnelAnalyticsService.java      🔄 REFACTORIZADO
├── controller/analytics/
│   └── AnalyticsController.java         🔄 MODIFICADO
└── config/
    └── FunnelStepsConfig.java           ⚠️ DEPRECADO
```

---

## 📦 **Scripts Disponibles**

### **Inserción de Datos**

```powershell
# Insertar templates en MongoDB
.\insert-funnel-templates.ps1

# Con opciones avanzadas
.\insert-funnel-templates.ps1 `
  -MongoUri "mongodb://localhost:27017" `
  -Database "logs_system" `
  -DeleteExisting `
  -Verbose
```

### **Testing Automatizado**

```powershell
# Ejecutar suite completa de pruebas
.\test-funnel-dynamic.ps1 `
  -Token "eyJhbGc..." `
  -Tenant "quintanaroo" `
  -BaseUrl "http://localhost:8040" `
  -Verbose
```

---

## 🔧 **Agregar un Nuevo Sistema (Sin Recompilar)**

### **Opción 1: MongoDB Shell**

```javascript
use logs_system;

db.funnel_templates.insertOne({
  "systemName": "MI_NUEVO_SISTEMA",
  "funnelName": "Flujo de Mi Proceso",
  "description": "Descripción del flujo",
  "active": true,
  "createdAt": new Date(),
  "updatedAt": new Date(),
  "createdBy": "admin",
  "steps": [
    {
      "order": 1,
      "eventType": "EVENTO_INICIO",
      "label": "Inicio del Proceso",
      "description": "Primer paso del flujo"
    },
    {
      "order": 2,
      "eventType": "EVENTO_VALIDACION",
      "label": "Validación",
      "description": "Validación de datos"
    },
    {
      "order": 3,
      "eventType": "EVENTO_COMPLETADO",
      "label": "Proceso Completado",
      "description": "Proceso finalizado con éxito"
    }
  ]
});
```

### **Opción 2: MongoDB Compass**

1. Conectar a `logs_system`
2. Ir a colección `funnel_templates`
3. Click en "Insert Document"
4. Pegar el JSON del template
5. Click en "Insert"

### **¡Listo! Usar Inmediatamente**

```bash
curl http://localhost:8040/api/analytics/funnel/MI_NUEVO_SISTEMA \
  -H "Authorization: Bearer TOKEN"
```

**No se requiere recompilar ni reiniciar el servidor.**

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

## 📚 **Documentación Completa**

### **Guías Técnicas:**

| Documento | Descripción | Ubicación |
|-----------|-------------|-----------|
| **FUNNEL_ANALYTICS_DYNAMIC_V2.md** | Documentación completa v2.0 (600+ líneas) | `docs/` |
| **FUNNEL_TEMPLATES_SEED.md** | Scripts de seed data MongoDB | `docs/` |
| **RESUMEN_REFACTORIZACION.md** | Resumen ejecutivo de cambios | `docs/` |
| **FUNNEL_ANALYTICS.md** | Documentación original v1.0 | `docs/` |

### **Swagger UI:**
```
http://localhost:8040/swagger-ui.html
Tag: "Analytics" → Ver todos los endpoints
```

---

## 🔍 **Queries MongoDB Útiles**

### **Listar Todos los Sistemas Activos**
```javascript
db.funnel_templates.find(
  { active: true },
  { systemName: 1, funnelName: 1, _id: 0 }
).sort({ systemName: 1 });
```

### **Desactivar un Sistema (Sin Eliminar)**
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
        { "order": 1, "eventType": "NUEVO_EVENTO", "label": "Nuevo Paso" }
      ],
      updatedAt: new Date()
    }
  }
);
```

### **Verificar Índice Único**
```javascript
db.funnel_templates.getIndexes();
// Debe mostrar: { "systemName": 1 } con unique: true
```

---

## 🎯 **Casos de Uso Estratégicos**

### **1. Identificar Cuellos de Botella**
```
Si dropRate > 30% en un paso específico
→ Investigar y optimizar ese paso
```

### **2. Comparar Rendimiento entre Sistemas**
```bash
# Analizar múltiples sistemas
curl .../funnel/TRUSTVALUE
curl .../funnel/CITA_GUYANA
curl .../funnel/TICKETS

# Comparar globalConversionRate
# Sistema con menor conversión requiere atención
```

### **3. Análisis Temporal (Tendencias)**
```bash
# Semana 1
curl .../funnel/TRUSTVALUE?from=2026-06-01T00:00:00Z&to=2026-06-07T23:59:59Z

# Semana 2
curl .../funnel/TRUSTVALUE?from=2026-06-08T00:00:00Z&to=2026-06-14T23:59:59Z

# Comparar métricas para detectar mejoras o degradaciones
```

---

## 🔐 **Seguridad**

### **Autenticación:**
- ✅ JWT Token obligatorio
- ✅ Permiso requerido: `PERM_LOG_READ`
- ✅ Header `X-Tenant` obligatorio

### **Autorización:**
- ✅ Validación de acceso al sistema vía `ScopeGuard`
- ✅ Usuario debe tener permisos para el sistema solicitado

### **Multi-Tenant:**
- ✅ Filtrado automático por `tenant_id` en queries MongoDB
- ✅ Aislamiento completo de datos por tenant

---

## ⚠️ **Clase Deprecada**

### **FunnelStepsConfig.java** ⚠️

```java
/**
 * @deprecated Desde 2026-06-16
 * Usar FunnelTemplate (MongoDB) para configuración dinámica
 */
@Deprecated(since = "2026-06-16", forRemoval = true)
public class FunnelStepsConfig { ... }
```

**No usar en nuevos desarrollos. La clase se mantiene solo para referencia histórica.**

---

## ✅ **Validación de la Instalación**

### **1. Verificar que MongoDB tiene los templates:**
```javascript
db.funnel_templates.countDocuments({ active: true });
// Debe retornar: 5
```

### **2. Verificar que el servidor compila:**
```powershell
.\mvnw.cmd clean compile -DskipTests
# Debe mostrar: BUILD SUCCESS
```

### **3. Verificar endpoints:**
```powershell
.\test-funnel-dynamic.ps1 -Token "YOUR_JWT" -Tenant "quintanaroo"
# Debe mostrar: 🎉 TODAS LAS PRUEBAS PASARON EXITOSAMENTE
```

---

## 🚀 **Próximos Pasos Recomendados**

### **Para Desarrollo:**
1. Ejecutar `.\insert-funnel-templates.ps1`
2. Iniciar servidor con `.\mvnw.cmd spring-boot:run`
3. Probar en Swagger UI: `http://localhost:8040/swagger-ui.html`
4. Ejecutar tests: `.\test-funnel-dynamic.ps1`

### **Para Producción:**
1. Insertar templates en MongoDB de producción
2. Compilar: `.\mvnw.cmd clean package -DskipTests`
3. Desplegar JAR: `target/dinamico-0.0.1-SNAPSHOT.jar`
4. Configurar variables de entorno del servidor
5. Validar con checklist: `docs/RESUMEN_REFACTORIZACION.md`

---

## 📞 **Soporte**

**Email:** soporte.tecnico@grupo-santoro.com.mx  
**Documentación:** `docs/FUNNEL_ANALYTICS_DYNAMIC_V2.md`  
**Swagger UI:** `http://localhost:8040/swagger-ui.html`  
**Scripts:** `insert-funnel-templates.ps1`, `test-funnel-dynamic.ps1`

---

## 🎉 **Resumen**

✅ **Arquitectura 100% dinámica desde MongoDB**  
✅ **Bug 404 corregido completamente**  
✅ **Eventos REALES de la base de datos**  
✅ **5 sistemas pre-configurados**  
✅ **TRUSTVALUE con flujo operativo real (4 pasos)**  
✅ **Agregar sistemas sin recompilar**  
✅ **Scripts de automatización completos**  
✅ **Documentación exhaustiva (1000+ líneas)**  
✅ **Listo para producción**

---

**Última actualización:** 2026-06-16  
**Versión:** 2.0 (Arquitectura Dinámica)  
**Estado:** ✅ PRODUCCIÓN

