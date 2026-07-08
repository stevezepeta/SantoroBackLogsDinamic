# 📊 Módulo de Análisis de Embudos de Conversión (Funnels)

## 🎯 Objetivo Estratégico

Transformar los logs de auditoría en métricas estratégicas de rendimiento operativo mediante el análisis de conversión y abandono de usuarios a través de flujos secuenciales de eventos.

---

## 🚀 Funcionalidad Implementada

### **Endpoint Principal: Análisis de Funnel**

```http
GET /api/analytics/funnel/{systemName}?from={fecha_inicio}&to={fecha_fin}
```

#### **Parámetros**

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `systemName` | Path | ✅ Sí | Nombre del sistema a analizar (ej: `CITA_GUYANA`, `TICKETS`) |
| `from` | Query | ❌ No | Fecha de inicio del rango (ISO-8601). Default: últimos 30 días |
| `to` | Query | ❌ No | Fecha de fin del rango (ISO-8601). Default: ahora |

#### **Headers Requeridos**

```http
Authorization: Bearer <JWT_TOKEN>
X-Tenant: <tenant_name>
```

---

## 📈 Ejemplo de Respuesta

### **Request:**
```bash
curl -X GET "http://localhost:8005/api/analytics/funnel/CITA_GUYANA?from=2026-01-01T00:00:00Z&to=2026-06-15T23:59:59Z" \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "X-Tenant: quintanaroo"
```

### **Response (200 OK):**
```json
{
  "status": "ok",
  "message": "Funnel analysis completed",
  "messageKey": "funnel_analysis",
  "data": {
    "system": "CITA_GUYANA",
    "funnelName": "Flujo de Trámite de Cita",
    "summary": {
      "totalStarted": 100,
      "totalCompleted": 40,
      "globalConversionRate": 40.0
    },
    "steps": [
      {
        "step": 1,
        "label": "Inicio de Sesión",
        "eventType": "AUTH_LOGIN",
        "count": 100,
        "conversionRate": 100.0,
        "dropRate": 0.0
      },
      {
        "step": 2,
        "label": "Validación de Registro",
        "eventType": "CONSULTA_ESTADO_REGISTRO",
        "count": 80,
        "conversionRate": 80.0,
        "dropRate": 20.0
      },
      {
        "step": 3,
        "label": "Cita Completada",
        "eventType": "RESERVA_DE_CITA",
        "count": 40,
        "conversionRate": 40.0,
        "dropRate": 50.0
      }
    ]
  }
}
```

---

## 📊 Métricas Calculadas

### **Por Paso (Step):**

- **`count`**: Cantidad de `caseId` únicos que alcanzaron este paso
- **`conversionRate`**: Porcentaje de conversión respecto al paso 1 (paso 1 siempre es 100%)
- **`dropRate`**: Porcentaje de abandono respecto al paso anterior (paso 1 siempre es 0%)

### **Resumen Global (Summary):**

- **`totalStarted`**: Total de casos que iniciaron el flujo (paso 1)
- **`totalCompleted`**: Total de casos que completaron el flujo (último paso)
- **`globalConversionRate`**: Tasa de conversión global (% del paso 1 al último paso)

---

## 🎨 Sistemas Soportados

### **1. CITA_GUYANA - Flujo de Trámite de Cita**

| Paso | EventType | Descripción |
|------|-----------|-------------|
| 1 | `AUTH_LOGIN` | Inicio de Sesión |
| 2 | `CONSULTA_ESTADO_REGISTRO` | Validación de Registro |
| 3 | `RESERVA_DE_CITA` | Cita Completada |

### **2. TRUSTVALUE - Flujo de Verificación de Identidad**

| Paso | EventType | Descripción |
|------|-----------|-------------|
| 1 | `CREAR_SESION` | Inicio de Verificación |
| 2 | `CAPTURA_BIOMETRIA` | Captura Biométrica |
| 3 | `VALIDACION_IDENTIDAD` | Validación Completada |

### **3. TICKETS - Flujo de Atención de Tickets**

| Paso | EventType | Descripción |
|------|-----------|-------------|
| 1 | `CREAR_TICKET` | Creación de Ticket |
| 2 | `ASIGNAR_TECNICO` | Asignación a Técnico |
| 3 | `RESOLVER_TICKET` | Ticket Resuelto |

### **4. PASSPORT - Flujo de Tramitación de Pasaporte**

| Paso | EventType | Descripción |
|------|-----------|-------------|
| 1 | `SOLICITUD_PASAPORTE` | Solicitud Iniciada |
| 2 | `VALIDACION_DOCUMENTOS` | Documentos Validados |
| 3 | `APROBACION_PASAPORTE` | Pasaporte Aprobado |
| 4 | `EMISION_PASAPORTE` | Pasaporte Emitido |

### **5. CITA_QUINTANAROO - Flujo de Citas Quintana Roo**

| Paso | EventType | Descripción |
|------|-----------|-------------|
| 1 | `AUTH_LOGIN` | Inicio de Sesión |
| 2 | `CONSULTA_DISPONIBILIDAD` | Consulta de Disponibilidad |
| 3 | `RESERVA_CITA` | Cita Reservada |
| 4 | `CONFIRMACION_CITA` | Cita Confirmada |

---

## 🔍 Endpoint Auxiliar: Listar Sistemas Disponibles

```http
GET /api/analytics/funnel/available-systems
```

### **Response:**
```json
{
  "status": "ok",
  "message": "Available funnel systems",
  "messageKey": "available_systems",
  "data": [
    "CITA_GUYANA",
    "TRUSTVALUE",
    "TICKETS",
    "PASSPORT",
    "CITA_QUINTANAROO"
  ]
}
```

---

## 🛠️ Arquitectura Técnica

### **Componentes Implementados**

```
/api/analytics/funnel/{systemName}
          ↓
    AnalyticsController
          ↓
   FunnelAnalyticsService
          ↓
      MongoTemplate (Aggregation Pipeline)
          ↓
    MongoDB log_events collection
```

### **Clases Creadas**

#### **1. DTOs (Data Transfer Objects)**
- `FunnelResponseDto`: Respuesta completa del endpoint
- `FunnelSummaryDto`: Resumen global del funnel
- `FunnelStepDto`: Métricas de cada paso del embudo

#### **2. Configuración**
- `FunnelStepsConfig`: Definición de pasos por sistema (fácilmente extensible)

#### **3. Servicio de Negocio**
- `FunnelAnalyticsService`: Lógica de cálculo de métricas y agregaciones MongoDB

#### **4. Controlador REST**
- `AnalyticsController`: Endpoints REST con documentación Swagger completa

---

## 🔐 Seguridad

### **Permisos Requeridos**
- `PERM_LOG_READ`: Lectura de logs

### **Filtros Aplicados**
- ✅ **Tenant Isolation**: Solo logs del tenant autenticado
- ✅ **System Scope**: Solo sistemas permitidos para el usuario
- ✅ **JWT Validation**: Token válido requerido

---

## 📐 Lógica de Agregación MongoDB

### **Pipeline de Agregación**

```javascript
db.log_events.aggregate([
  // 1. Filtrar por tenant, sistema, rango de fechas y eventTypes del funnel
  {
    $match: {
      tenant_id: ObjectId("..."),
      system: "CITA_GUYANA",
      eventTime: { $gte: ISODate("..."), $lt: ISODate("...") },
      eventType: { $in: ["AUTH_LOGIN", "CONSULTA_ESTADO_REGISTRO", "RESERVA_DE_CITA"] },
      caseId: { $exists: true, $ne: null, $ne: "" }
    }
  },
  
  // 2. Agrupar por eventType y contar caseId únicos
  {
    $group: {
      _id: "$eventType",
      uniqueCases: { $addToSet: "$caseId" }
    }
  },
  
  // 3. Calcular tamaño del set de casos únicos
  {
    $project: {
      _id: 1,
      count: { $size: "$uniqueCases" }
    }
  }
])
```

### **Índices Utilizados**
```javascript
// Índice compuesto optimizado (ya existente en el proyecto)
{
  "tenant_id": 1,
  "system": 1,
  "eventType": 1,
  "eventTime": -1
}
```

---

## 🎯 Casos de Uso

### **1. Identificar Cuellos de Botella**
```
Si dropRate del paso 2 > 30% 
→ Investigar problemas en ese paso específico
```

### **2. Medir Eficiencia Operativa**
```
globalConversionRate < 50% 
→ Flujo tiene problemas graves
```

### **3. Reportes Ejecutivos**
```
Dashboard KPI:
- Sistema: CITA_GUYANA
- Conversión Global: 40%
- Paso Crítico: Validación de Registro (20% de abandono)
```

### **4. Comparación Temporal**
```
GET /funnel/TICKETS?from=2026-05-01&to=2026-05-31  (Mayo)
GET /funnel/TICKETS?from=2026-04-01&to=2026-04-30  (Abril)

Comparar globalConversionRate entre periodos
```

---

## 🧩 Extensibilidad

### **Agregar un Nuevo Sistema**

Editar `FunnelStepsConfig.java`:

```java
"NUEVO_SISTEMA", new FunnelDefinition(
    "Descripción del Flujo",
    List.of(
        new FunnelStep(1, "EVENT_STEP_1", "Etiqueta Paso 1"),
        new FunnelStep(2, "EVENT_STEP_2", "Etiqueta Paso 2"),
        new FunnelStep(3, "EVENT_STEP_3", "Etiqueta Paso 3")
    )
)
```

**¡No se requieren cambios en el código del servicio o controlador!**

---

## 📦 Testing

### **PowerShell Test Script**

```powershell
# test-funnel-analytics.ps1

$baseUrl = "http://localhost:8005"
$token = "eyJhbGc..."  # Tu JWT token
$tenant = "quintanaroo"

# 1. Listar sistemas disponibles
Invoke-RestMethod -Uri "$baseUrl/api/analytics/funnel/available-systems" `
  -Headers @{
    "Authorization" = "Bearer $token"
    "X-Tenant" = $tenant
  }

# 2. Análisis de funnel
Invoke-RestMethod -Uri "$baseUrl/api/analytics/funnel/CITA_GUYANA" `
  -Headers @{
    "Authorization" = "Bearer $token"
    "X-Tenant" = $tenant
  } | ConvertTo-Json -Depth 10

# 3. Con rango de fechas
$from = "2026-01-01T00:00:00Z"
$to = "2026-06-15T23:59:59Z"

Invoke-RestMethod -Uri "$baseUrl/api/analytics/funnel/TICKETS?from=$from&to=$to" `
  -Headers @{
    "Authorization" = "Bearer $token"
    "X-Tenant" = $tenant
  } | ConvertTo-Json -Depth 10
```

### **cURL Test**

```bash
# Listar sistemas
curl -X GET "http://localhost:8005/api/analytics/funnel/available-systems" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant: quintanaroo"

# Análisis de funnel
curl -X GET "http://localhost:8005/api/analytics/funnel/CITA_GUYANA" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant: quintanaroo"
```

---

## 📊 Swagger UI

Acceder a la documentación interactiva:

```
http://localhost:8005/swagger-ui.html
```

**Buscar:** `Analytics` en el grupo de tags

---

## 🎓 Interpretación de Métricas

### **Conversion Rate (Tasa de Conversión)**
- **100%**: Todos los usuarios del paso 1 alcanzaron este paso
- **50%**: La mitad de los usuarios del paso 1 alcanzaron este paso
- **0%**: Ningún usuario del paso 1 alcanzó este paso

### **Drop Rate (Tasa de Abandono)**
- **0%**: No hubo abandono respecto al paso anterior
- **20%**: El 20% de usuarios del paso anterior no continuó
- **50%**: La mitad de los usuarios del paso anterior abandonó

### **Ejemplo de Análisis**

```json
{
  "step": 2,
  "label": "Validación de Registro",
  "count": 80,
  "conversionRate": 80.0,  // 80% del paso 1 llegó aquí
  "dropRate": 20.0         // 20% abandonó después del paso 1
}
```

**Interpretación:**
- De 100 usuarios que iniciaron sesión (paso 1)
- 80 validaron su registro (paso 2)
- 20 usuarios (20%) abandonaron el proceso

---

## 🚀 Despliegue

### **Compilación**

```powershell
.\mvnw.cmd clean package -DskipTests
```

### **Ejecución Local**

```powershell
.\mvnw.cmd spring-boot:run
```

### **Variables de Entorno Requeridas**

```properties
# MongoDB
MONGO_URI=mongodb://localhost:27017/logsQR

# JWT
JWT_SECRET=tu_secret_super_seguro_de_minimo_32_caracteres

# Tenant
MULTITENANT_BASE_DATABASE=logsQR
```

---

## 📞 Soporte

**Email Técnico**: soporte.tecnico@grupo-santoro.com.mx  
**Swagger UI**: http://localhost:8005/swagger-ui.html  
**Última Actualización**: 2026-06-15

---

## ✅ Checklist de Implementación Completada

- [x] DTOs de respuesta (`FunnelResponseDto`, `FunnelSummaryDto`, `FunnelStepDto`)
- [x] Configuración extensible de pasos por sistema (`FunnelStepsConfig`)
- [x] Servicio de analítica con agregaciones MongoDB optimizadas
- [x] Controlador REST con documentación Swagger completa
- [x] Endpoint auxiliar para listar sistemas disponibles
- [x] Seguridad multi-tenant y validación de permisos
- [x] Índices MongoDB optimizados (ya existentes)
- [x] Cálculo de métricas (count, conversionRate, dropRate)
- [x] Soporte para múltiples sistemas (CITA_GUYANA, TICKETS, TRUSTVALUE, PASSPORT, CITA_QUINTANAROO)
- [x] Compilación exitosa sin errores
- [x] Documentación técnica completa

---

**🎉 Funcionalidad lista para producción**

