# 🧪 Guía de Pruebas - Endpoints de Agregación Corregidos

## Fecha: 2026-07-16

---

## 📋 Configuración de Pruebas

### Prerrequisitos
1. ✅ Backend ejecutándose (`.\iniciar-backend-principal.ps1`)
2. ✅ MongoDB con colección `log_events` poblada
3. ✅ Token de autenticación válido
4. ✅ Usuario con permisos de lectura de logs

### Variables de Entorno
```bash
export BASE_URL="http://localhost:8080"
export TOKEN="tu_token_jwt_aqui"
```

---

## 🎯 Endpoints Principales Corregidos

### 1. Executive Summary (Resumen Ejecutivo Global)

**Endpoint:** `GET /api/analytics/executive-summary`

**Descripción:** Devuelve métricas agregadas de todos los sistemas del tenant.

#### ✅ Casos de Prueba

##### Test 1: Resumen de últimos 7 días (por defecto)
```bash
curl -X GET "$BASE_URL/api/analytics/executive-summary" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

**Resultado esperado:**
```json
{
  "data": {
    "generatedAt": "2026-07-16T10:30:00Z",
    "period": {
      "from": "2026-07-09",
      "to": "2026-07-16"
    },
    "overallHealth": "WARNING",
    "systems": [
      {
        "system": "TRUSTVALUE",
        "systemLabel": "Control de Asistencia",
        "status": "WARNING",
        "totalEvents": 1523,
        "errorCount": 87,
        "errorRate": 5.71,
        "activeCases": 234,
        "lastIncident": "2026-07-14T17:40:56.041Z"
      }
    ],
    "activeAlerts": [],
    "topMetrics": {
      "totalEvents": 1523,
      "totalErrors": 87,
      "globalErrorRate": 5.71,
      "activeCases": 234
    }
  }
}
```

##### Test 2: Resumen con rango de fechas personalizado
```bash
curl -X GET "$BASE_URL/api/analytics/executive-summary?fromDate=2026-07-01&toDate=2026-07-15" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

**Verificaciones:**
- ✅ `systems` NO debe estar vacío si hay logs en el rango
- ✅ `totalEvents` debe ser > 0
- ✅ `errorRate` debe estar entre 0 y 100
- ✅ `lastIncident` debe tener timestamp UTC válido

---

### 2. Top Frictional Events (Eventos con Mayor Fricción)

**Endpoint:** `GET /api/analytics/top-frictional-events`

**Descripción:** Devuelve ranking de eventos que más afectan a los usuarios.

#### ✅ Casos de Prueba

##### Test 1: Top 5 eventos del día actual
```bash
curl -X GET "$BASE_URL/api/analytics/top-frictional-events" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

**Resultado esperado:**
```json
{
  "data": {
    "date": "2026-07-16",
    "events": [
      {
        "rank": 1,
        "eventCode": "AUTH_LOGIN",
        "title": "Error de inicio de sesión",
        "description": "El usuario no pudo iniciar sesión correctamente",
        "occurrenceCount": 45,
        "affectedCases": 28,
        "percentageOfTotalErrors": 32.18,
        "trend": "STABLE",
        "trendPercentage": 0.0,
        "system": "TRUSTVALUE",
        "topLocation": "Cancún Centro",
        "recommendedAction": "Verificar credenciales del usuario"
      }
    ]
  }
}
```

##### Test 2: Top 10 eventos filtrados por sistema
```bash
curl -X GET "$BASE_URL/api/analytics/top-frictional-events?system=TRUSTVALUE&limit=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

##### Test 3: Eventos de una fecha específica
```bash
curl -X GET "$BASE_URL/api/analytics/top-frictional-events?date=2026-07-14&limit=5" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

**Verificaciones:**
- ✅ `events` NO debe estar vacío si hay errores en el día
- ✅ `affectedCases` debe ser <= `occurrenceCount`
- ✅ `percentageOfTotalErrors` debe sumar <= 100% entre todos
- ✅ `eventCode` debe tener valor (no puede ser null, usa eventType como fallback)
- ✅ Lista ordenada por `affectedCases` DESC

---

### 3. Executive Timeline (Timeline Individual)

**Endpoint:** `GET /api/analytics/executive-timeline`

**Descripción:** Timeline simplificado de un caso específico.

#### ✅ Casos de Prueba

##### Test 1: Timeline completo de un caso
```bash
curl -X GET "$BASE_URL/api/analytics/executive-timeline?system=TRUSTVALUE&caseId=CASE-12345" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

**Resultado esperado:**
```json
{
  "data": {
    "header": {
      "system": "TRUSTVALUE",
      "caseId": "CASE-12345",
      "actorName": "Juan Pérez",
      "locationName": "Cancún Centro",
      "fromDate": "2026-07-14T08:00:00Z",
      "toDate": "2026-07-14T18:00:00Z"
    },
    "events": [
      {
        "eventTime": "2026-07-14T08:15:30Z",
        "stepLabel": "Registro",
        "status": "SUCCESS",
        "title": "Registro exitoso",
        "description": "Usuario registrado correctamente"
      }
    ],
    "page": {
      "page": 0,
      "size": 20,
      "hasNext": false
    }
  }
}
```

##### Test 2: Timeline con rango de fechas
```bash
curl -X GET "$BASE_URL/api/analytics/executive-timeline?system=TRUSTVALUE&caseId=CASE-12345&fromDate=2026-07-01&toDate=2026-07-15&page=0&size=50" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

**Verificaciones:**
- ✅ `events` debe incluir todos los logs del caso en el rango
- ✅ Ordenados por `eventTime` ASC
- ✅ `page.hasNext` debe ser true si hay más resultados

---

### 4. User Journey (Recorrido Completo)

**Endpoint:** `GET /api/analytics/user-journey`

**Descripción:** Recorrido completo con geolocalización y métricas.

#### ✅ Casos de Prueba

##### Test 1: Journey completo de un caso
```bash
curl -X GET "$BASE_URL/api/analytics/user-journey?caseId=CASE-12345&system=TRUSTVALUE" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

**Resultado esperado:**
```json
{
  "data": {
    "caseId": "CASE-12345",
    "system": "TRUSTVALUE",
    "summary": {
      "startTime": "2026-07-14T08:00:00Z",
      "endTime": "2026-07-14T18:00:00Z",
      "finalStatus": "COMPLETADO",
      "totalSteps": 12,
      "successfulSteps": 10,
      "failedSteps": 2,
      "durationSeconds": 36000,
      "startLocation": "Cancún Centro",
      "endLocation": "Cancún Centro"
    },
    "steps": [
      {
        "order": 1,
        "timestamp": "2026-07-14T08:15:30Z",
        "stepLabel": "Registro",
        "status": "SUCCESS",
        "title": "Registro exitoso",
        "description": "Usuario registrado correctamente",
        "suggestedAction": null,
        "latitude": 21.1619,
        "longitude": -86.8515,
        "locationName": "Cancún Centro",
        "hasErrorExplanation": false,
        "aiExplanation": null,
        "duration": "0s"
      }
    ]
  }
}
```

**Verificaciones:**
- ✅ `steps` ordenados cronológicamente
- ✅ `summary.totalSteps` debe ser igual a `steps.length`
- ✅ Coordenadas geográficas válidas (si están presentes)
- ✅ `finalStatus` debe reflejar el estado real del journey

---

## 🔍 Diagnóstico de Problemas

### ❌ Si los endpoints devuelven arrays vacíos

**1. Verificar nombre de campo en MongoDB:**
```javascript
// En MongoDB Compass o shell
db.log_events.findOne({}, {tenant_id: 1, tenantId: 1})

// Debe devolver:
{
  "_id": ObjectId("..."),
  "tenant_id": ObjectId("...")  // ✅ Correcto
}

// NO debe devolver:
{
  "_id": ObjectId("..."),
  "tenantId": "..."  // ❌ Incorrecto
}
```

**2. Verificar rango de fechas de los logs:**
```javascript
db.log_events.aggregate([
  {
    $match: {
      tenant_id: ObjectId("696a76bddc3d6cd1487cdd35"),
      system: "TRUSTVALUE"
    }
  },
  {
    $group: {
      _id: null,
      minDate: { $min: "$eventTime" },
      maxDate: { $max: "$eventTime" },
      count: { $sum: 1 }
    }
  }
])
```

**3. Verificar criterios de error:**
```javascript
db.log_events.aggregate([
  {
    $match: {
      tenant_id: ObjectId("696a76bddc3d6cd1487cdd35"),
      $or: [
        { isError: true },
        { status: { $in: ["REJECTED", "ERROR", "FAILED"] } },
        { outcome: { $in: ["FAILURE", "ERROR", "FAILED"] } }
      ]
    }
  },
  {
    $count: "totalErrors"
  }
])
```

**4. Verificar índices:**
```javascript
db.log_events.getIndexes()

// Debe incluir:
// idx_tenant_system_time_v2: { tenant_id: 1, system: 1, eventTime: -1 }
```

---

## 📊 Queries de MongoDB Directas

### Ejemplo del Pipeline Corregido

```javascript
// Pipeline de aggregateSystemMetrics (corregido)
db.log_events.aggregate([
  {
    $match: {
      tenant_id: ObjectId("696a76bddc3d6cd1487cdd35"),
      system: { $in: ["TRUSTVALUE"] },
      eventTime: {
        $gte: ISODate("2026-07-14T00:00:00.000Z"),
        $lte: ISODate("2026-07-14T23:59:59.999Z")  // ✅ Inclusivo
      }
    }
  },
  {
    $group: {
      _id: "$system",
      total: { $sum: 1 },
      errors: {
        $sum: {
          $cond: [
            {
              $or: [
                { $eq: ["$isError", true] },
                { $in: ["$status", ["REJECTED", "ERROR", "FAILED"]] },
                { $in: ["$outcome", ["FAILURE", "ERROR", "FAILED"]] }
              ]
            },
            1,
            0
          ]
        }
      },
      uniqueCases: { $addToSet: "$caseId" }
    }
  }
])
```

### Ejemplo del Pipeline de Frictional Events (corregido)

```javascript
db.log_events.aggregate([
  {
    $match: {
      tenant_id: ObjectId("696a76bddc3d6cd1487cdd35"),
      system: { $in: ["TRUSTVALUE"] },
      eventTime: {
        $gte: ISODate("2026-07-14T00:00:00.000Z"),
        $lte: ISODate("2026-07-14T23:59:59.999Z")
      },
      $or: [
        { isError: true },
        { status: { $in: ["REJECTED", "ERROR", "FAILED"] } },
        { outcome: { $in: ["FAILURE", "ERROR", "FAILED"] } }
      ]
    }
  },
  {
    $group: {
      _id: {
        eventKey: { $ifNull: ["$eventCode", "$eventType"] },  // ✅ Usa $ifNull
        system: "$system"
      },
      occurrenceCount: { $sum: 1 },
      affectedCases: { $addToSet: "$caseId" },
      topLocation: { $first: "$location.name" }
    }
  },
  {
    $project: {
      eventCode: "$_id.eventKey",
      system: "$_id.system",
      occurrenceCount: 1,
      affectedCases: { $size: "$affectedCases" },
      topLocation: 1
    }
  },
  {
    $sort: { affectedCases: -1 }
  },
  {
    $limit: 5
  }
])
```

---

## 🎯 Checklist de Validación

### Compilación y Despliegue
- [ ] Compilación sin errores de tipo ERROR
- [ ] Solo warnings menores (imports no usados, toString())
- [ ] Backend inicia correctamente en puerto 8080
- [ ] Logs no muestran errores de MongoDB

### Endpoints de Agregación
- [ ] `/api/analytics/executive-summary` devuelve datos
- [ ] `systems` array está poblado (no vacío)
- [ ] `totalEvents` es > 0 cuando hay logs en el rango
- [ ] `/api/analytics/top-frictional-events` devuelve eventos
- [ ] `events` array está poblado cuando hay errores
- [ ] `eventCode` nunca es null (usa fallback a eventType)

### Timeline Individual
- [ ] `/api/analytics/executive-timeline` funciona con caseId
- [ ] `/api/analytics/user-journey` incluye geolocalización

### Dashboard de Pasaportes
- [ ] `/api/passport/summary` devuelve métricas
- [ ] `/api/passport/by-office` agrupa correctamente
- [ ] `/api/passport/by-type` agrupa por eventType

---

## 📝 Notas de Logs

### Logs Útiles en Backend

```log
# ✅ CORRECTO - Query bien formada
[aggregateSystemMetrics] tenantId=696a76bddc3d6cd1487cdd35 (type=org.bson.types.ObjectId), systems=[TRUSTVALUE]
[aggregateSystemMetrics] matchJson={ "tenant_id" : { "$oid" : "696a76bddc3d6cd1487cdd35" }, "system" : { "$in" : ["TRUSTVALUE"] }, "eventTime" : { "$gte" : { "$date" : "2026-07-14T00:00:00.000Z" }, "$lte" : { "$date" : "2026-07-14T23:59:59.999Z" } } }

# ❌ INCORRECTO - Query mal formada
[aggregateSystemMetrics] matchJson={ "tenantId" : "696a76bddc3d6cd1487cdd35", ... }
```

---

## 🚀 Comandos Rápidos

### Iniciar Backend
```powershell
.\iniciar-backend-principal.ps1
```

### Verificar Correcciones
```powershell
.\verificar-pipeline-fix.ps1
```

### Ver Logs en Tiempo Real
```powershell
Get-Content -Path "logs\application.log" -Tail 50 -Wait
```

### Probar Executive Summary (PowerShell)
```powershell
$token = "tu_token_aqui"
$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}
Invoke-RestMethod -Uri "http://localhost:8080/api/analytics/executive-summary" -Headers $headers -Method Get | ConvertTo-Json -Depth 10
```

---

## 📚 Referencias
- [MongoDB Aggregation Pipeline](https://docs.mongodb.com/manual/core/aggregation-pipeline/)
- [Spring Data MongoDB Criteria](https://docs.spring.io/spring-data/mongodb/docs/current/reference/html/#mongodb.repositories.queries)
- [MongoDB $ifNull Operator](https://docs.mongodb.com/manual/reference/operator/aggregation/ifNull/)

---

**Autor:** GitHub Copilot  
**Fecha:** 2026-07-16  
**Versión:** 1.0

