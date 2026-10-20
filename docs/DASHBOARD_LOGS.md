# Dashboard de Logs — Documentación Técnica Completa

> Sistema centralizado de ingesta, almacenamiento, consulta y visualización de eventos de log en tiempo real para múltiples tenants y sistemas.

---

## Tabla de Contenidos

1. [Visión General](#1-visión-general)
2. [Flujo de Datos End-to-End](#2-flujo-de-datos-end-to-end)
3. [Estructura del Log Event](#3-estructura-del-log-event)
   - [Campos Raíz](#31-campos-raíz)
   - [Actor](#32-actor)
   - [Location](#33-location)
   - [Correlation](#34-correlation)
   - [HttpInfo](#35-httpinfo)
   - [SlaInfo](#36-slainfo)
   - [ReasonInfo](#37-reasoninfo)
   - [GeoPoint](#38-geopoint)
   - [RemoteConnection](#39-remoteconnection)
   - [Tags, Payload, Meta](#310-tags-payload-meta)
   - [Campos Calculados](#311-campos-calculados)
4. [Ingesta de Logs](#4-ingesta-de-logs)
   - [Endpoint Individual](#41-endpoint-individual)
   - [Endpoint Batch](#42-endpoint-batch)
   - [Pipeline de Normalización](#43-pipeline-de-normalización)
5. [Consulta y Búsqueda de Logs](#5-consulta-y-búsqueda-de-logs)
6. [Timeline de Casos](#6-timeline-de-casos)
7. [Dashboard — Endpoints y Agregaciones](#7-dashboard--endpoints-y-agregaciones)
   - [Stats](#71-stats)
   - [Series de Tiempo](#72-series-de-tiempo)
   - [Métricas HTTP](#73-métricas-http)
   - [Mapa Geográfico](#74-mapa-geográfico)
   - [Salud de Sistemas](#75-salud-de-sistemas)
8. [Registro de Dispositivos](#8-registro-de-dispositivos)
9. [Seguridad y Control de Acceso (RBAC)](#9-seguridad-y-control-de-acceso-rbac)
10. [Notificaciones en Tiempo Real (WebSocket)](#10-notificaciones-en-tiempo-real-websocket)
11. [Retención de Logs](#11-retención-de-logs)
12. [Índices de MongoDB](#12-índices-de-mongodb)
13. [Colecciones de MongoDB](#13-colecciones-de-mongodb)
14. [Mapa de Clases Clave](#14-mapa-de-clases-clave)
15. [Ejemplo de Payload Completo](#15-ejemplo-de-payload-completo)

---

## 1. Visión General

El sistema de Dashboard de Logs es el núcleo de la plataforma. Permite que **múltiples sistemas externos** (apps móviles, servidores, dispositivos, APIs) envíen sus eventos de log a través de una **API Key**; luego los usuarios de cada organización consultan, filtran y visualizan esos eventos en el dashboard.

```
Sistema Externo
  (TICKETS, HID, SERVER, etc.)
         │
         │  POST /api/logs/events
         │  Header: X-Api-Key: <key>
         ▼
  ┌─────────────────────────────────┐
  │  ApiKeyTenantFilter             │  Resuelve el tenant desde la API Key
  │  TenantResolutionFilter         │  Pone el tenantId en TenantContext (ThreadLocal)
  │  LogEventService.ingest()       │  Normaliza y guarda en MongoDB
  │  DeviceRegistryService          │  Auto-registra el dispositivo (upsert)
  │  DashboardNotifier (WebSocket)  │  Notifica al frontend en tiempo real
  └─────────────────────────────────┘
         │
         │  colección: log_events
         ▼
  ┌─────────────────────────────────┐
  │  MongoDB (multi-tenant)         │
  │  Índices compuestos             │
  └─────────────────────────────────┘
         │
         │  GET /api/logs/dashboard/*
         │  Header: Authorization: Bearer <jwt>
         ▼
  ┌─────────────────────────────────┐
  │  LogDashboardService            │  Aggregation pipelines
  │  LogEventService (search/all)   │  Consultas filtradas por tenant + RBAC
  └─────────────────────────────────┘
         │
         ▼
     Frontend / Dashboard UI
```

> **Nota:** existe además una **API legacy** (`POST /api/logs`, colección `logs`, modelo `LogEntry`) que convive con la nueva. Se mantiene por compatibilidad pero el flujo principal descrito en este documento es el de `log_events`.

---

## 2. Flujo de Datos End-to-End

```
[1] Sistema envía POST /api/logs/events
        ↓
[2] ApiKeyTenantFilter valida X-Api-Key → resuelve tenantId → guarda en TenantContext
        ↓
[3] LogEventService.ingest()
    ├── Valida que sea ApiKey (no JWT humano)
    ├── Valida geo.coordinates si viene
    ├── Normaliza todos los campos string (trim, uppercase, null-check)
    ├── EventTypeNormalizer: "APP_EVENT_1034" → {category:"APP_EVENT", code:"1034"}
    ├── LogNormalizationUtils:
    │   ├── normalizeSeverity: "CRITICAL" → "ERROR", "LOW" → "INFO"
    │   ├── buildMessageKey: normaliza el mensaje para agrupación/deduplicación
    │   └── computeIsError: calcula boolean isError desde severity+status+outcome
    ├── Construye LogEvent y guarda en MongoDB (colección: log_events)
    │   └── Al persistir, SeverityNormalizer (listener/callback de Mongo)
    │       recalcula severity y deja el valor original en severityRaw
    ├── DeviceRegistryService.upsertFromLog() → actualiza colección devices_registry
    └── DashboardNotifier.notifyNewLog() → publica en WebSocket /topic/dashboard/{tenantId}/{system}
        ↓
[4] Frontend recibe evento WebSocket → re-llama /api/logs/dashboard/stats
        ↓
[5] LogDashboardService ejecuta aggregation pipeline de MongoDB
        ↓
[6] Frontend renderiza cards, gráficas, mapa
```

---

## 3. Estructura del Log Event

Colección MongoDB: **`log_events`**  
Clase Java: `backlogs.dinamico.model.log.LogEvent`  
DTO de ingesta: `backlogs.dinamico.api.dto.logs.LogEventIngestReq` (record con records anidados)

### 3.1 Campos Raíz

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `_id` | `ObjectId` | Auto | ID único del evento generado por MongoDB |
| `tenant_id` | `ObjectId` | Auto | ID de la organización dueña del log, inyectado desde el `TenantContext` — **nunca viene del cliente** |
| `schemaVersion` | `Integer` | No (default: 1) | Versión del schema del log, para compatibilidad futura |
| `system` | `String` | **Sí** | Identificador del sistema que originó el log (ej: `TICKETS`, `HID_BIOMETRIC`, `SERVER_PRINCIPAL`). Se normaliza a **UPPERCASE** |
| `environment` | `String` | No | Entorno de despliegue: `PROD`, `STAGING`, `DEV` |
| `caseId` | `String` | **Sí** | Identificador del caso, transacción o dispositivo. Agrupa eventos relacionados bajo la misma "historia". Usado en el timeline |
| `eventTime` | `Instant` | **Sí** | Timestamp del evento en UTC |
| `eventType` | `String` | **Sí** | Categoría del tipo de evento normalizada (ej: `APP_EVENT`, `SYSTEM_STARTUP`, `LOGIN`). Ver normalización abajo |
| `eventCode` | `String` | Auto | Código numérico extraído del `eventType` raw (ej: `"1034"` de `APP_EVENT_1034`) |
| `eventTypeRaw` | `String` | Auto | Valor original del eventType antes de normalizar (ej: `APP_EVENT_1034`) |
| `status` | `String` | **Sí** | Estado del evento: `PENDING`, `ACTIVE`, `COMPLETED`, `ERROR`, `REJECTED`. Se normaliza a **UPPERCASE** |
| `outcome` | `String` | No | Resultado del evento: `SUCCESS`, `FAILURE`, `IN_PROGRESS`, `CANCELED`. Se normaliza a **UPPERCASE** |
| `severity` | `String` | No | Nivel de severidad normalizado: `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL`. Valores de terceros se mapean automáticamente (ej: `CRITICAL` → `ERROR`, `LOW` → `INFO`, `MEDIUM` → `WARN`). Ver §3.11 |
| `severityRaw` | `String` | Auto | Valor original de `severity` tal como lo envió el emisor, **antes** de normalizar. Se preserva automáticamente al persistir |
| `message` | `String` | **Sí** | Descripción legible del evento. Se normaliza (trim, null-check) |
| `messageKey` | `String` | Auto | Clave de normalización del mensaje: UUIDs, ObjectIds, números, IDs de sesión son reemplazados por tokens genéricos (`{uuid}`, `{n}`, etc.). Útil para agrupación y detección de patrones. Truncada a 160 chars |
| `isError` | `Boolean` | Auto | `true` si severity es `ERROR`/`FATAL`, outcome es `FAILURE` o status es `REJECTED`/`ERROR` |

### 3.2 Actor

Quién realizó la acción que originó el evento.

| Campo | Tipo | Descripción |
|---|---|---|
| `actor.id` | `String` | Identificador único del actor (ej: CURP, ID de empleado, UUID de servicio) |
| `actor.type` | `String` | Tipo de actor: `USER`, `SERVICE`, `DEVICE`, `SYSTEM`. Se normaliza a UPPERCASE |
| `actor.username` | `String` | Nombre de usuario o alias del actor |
| `actor.fullName` | `String` | Nombre completo del actor. También se usa como `deviceId` preferente en el auto-registro de dispositivos |

### 3.3 Location

Dónde físicamente o lógicamente ocurrió el evento.

| Campo | Tipo | Descripción |
|---|---|---|
| `location.id` | `String` | ID de la ubicación en el catálogo de la organización |
| `location.name` | `String` | Nombre descriptivo de la ubicación (ej: `"Planta Baja - Recepción"`) |
| `location.city` | `String` | Ciudad |
| `location.country` | `String` | País en UPPERCASE (ej: `MX`, `US`) |

### 3.4 Correlation

Datos de trazabilidad distribuida para correlacionar con otros sistemas de observabilidad.

| Campo | Tipo | Descripción |
|---|---|---|
| `correlation.requestId` | `String` | ID único del request HTTP que originó el evento |
| `correlation.traceId` | `String` | Trace ID de OpenTelemetry o Zipkin para trazabilidad distribuida |
| `correlation.spanId` | `String` | Span ID dentro del trace |

### 3.5 HttpInfo

Aplica cuando el evento corresponde a una petición HTTP.

| Campo | Tipo | Descripción |
|---|---|---|
| `http.method` | `String` | Método HTTP en UPPERCASE: `GET`, `POST`, `PUT`, `DELETE` |
| `http.path` | `String` | Ruta del endpoint (ej: `/api/auth/login`) |
| `http.statusCode` | `Integer` | Código de respuesta HTTP (ej: `200`, `401`, `500`) |
| `http.latencyMs` | `Long` | Latencia en milisegundos |

### 3.6 SlaInfo

Para eventos que deben cumplir tiempos de servicio (SLA).

| Campo | Tipo | Descripción |
|---|---|---|
| `sla.startTime` | `Instant` | Inicio del proceso que se mide |
| `sla.endTime` | `Instant` | Fin del proceso |
| `sla.elapsedSeconds` | `Long` | Tiempo total transcurrido en segundos |

### 3.7 ReasonInfo

Razón o causa del evento, especialmente útil en errores y rechazos.

| Campo | Tipo | Descripción |
|---|---|---|
| `reason.code` | `String` | Código del error o razón en UPPERCASE (ej: `AUTH_TOKEN_EXPIRED`). También se considera en la normalización de severidad (si contiene `ERR` → severity `ERROR`) |
| `reason.description` | `String` | Descripción legible de la razón |

### 3.8 GeoPoint

Coordenadas geográficas del evento (formato GeoJSON estándar compatible con índice 2dsphere de MongoDB).

| Campo | Tipo | Descripción |
|---|---|---|
| `geo.type` | `String` | Siempre `"Point"` (GeoJSON) |
| `geo.coordinates` | `List<Double>` | Array `[longitud, latitud]` — **orden: lng primero, lat segundo** (estándar GeoJSON). Validado: lng ∈ [-180,180], lat ∈ [-90,90] |
| `geo.accuracyMeters` | `Integer` | Radio de precisión en metros del punto GPS |

> ⚠️ **Gotcha:** El orden es `[lng, lat]`, no `[lat, lng]`. Invertirlo causa que los puntos aparezcan en el océano.

### 3.9 RemoteConnection

Para eventos de conexiones remotas: SSH, RDP, VPN, acceso a base de datos, etc. Los campos llegan tal cual desde el emisor y se persisten sin cálculos adicionales en el backend; el consumidor principal actual es el **certificado de auditoría** (usa `remoteConnection.sourceIp` como fallback de extracción de IP, ver §14).

| Campo | Tipo | Descripción |
|---|---|---|
| `remoteConnection.sourceIp` | `String` | IP de origen de la conexión |
| `remoteConnection.sourcePort` | `Integer` | Puerto origen |
| `remoteConnection.destinationIp` | `String` | IP destino |
| `remoteConnection.destinationPort` | `Integer` | Puerto destino (ej: `22` para SSH, `3389` para RDP) |
| `remoteConnection.protocol` | `String` | Protocolo: `SSH`, `RDP`, `VPN`, `FTP`, `SFTP` |
| `remoteConnection.authMethod` | `String` | Método de autenticación: `PASSWORD`, `KEY`, `CERTIFICATE`, `2FA` |
| `remoteConnection.authResult` | `String` | Resultado: `SUCCESS`, `FAILURE`, `TIMEOUT` |
| `remoteConnection.user` | `String` | Usuario del sistema operativo usado en la conexión |
| `remoteConnection.sessionId` | `String` | ID único de la sesión |
| `remoteConnection.sessionDuration` | `Long` | Duración de la sesión en segundos |
| `remoteConnection.clientType` | `String` | Tipo de cliente: `TERMINAL`, `WEB_CONSOLE`, `APP` |
| `remoteConnection.sourceCountry` | `String` | País de origen de la IP |
| `remoteConnection.sourceCity` | `String` | Ciudad de origen de la IP |
| `remoteConnection.isLocalNetwork` | `Boolean` | `true` si la IP origen está en red local/privada (la calcula el emisor) |
| `remoteConnection.riskScore` | `Double` | Puntuación de riesgo (0.0 a 1.0) calculada por el emisor |
| `remoteConnection.metadata` | `Map<String,Object>` | Datos adicionales de la conexión de formato libre |

### 3.10 Tags, Payload, Meta

| Campo | Tipo | Descripción |
|---|---|---|
| `tags` | `List<String>` | Etiquetas libres del evento. Se normalizan a **lowercase**. Ej: `["auth", "mobile", "biometric"]` |
| `payload` | `Map<String,Object>` | Datos del dominio del evento en formato libre. No se indexan ni normalizan. Para datos estructurados específicos del negocio |
| `meta` | `Map<String,Object>` | Metadatos técnicos del cliente emisor. El campo `meta.ip` es leído por `DeviceRegistryService` para registrar la IP del dispositivo |

### 3.11 Campos Calculados

Generados automáticamente en `LogEventService.ingest()` y al persistir (listener/callback de Mongo) — **nunca los envía el cliente como valores finales**:

| Campo | Cómo se calcula |
|---|---|
| `tenant_id` | Del `TenantContext` (resuelto desde la API Key en el filtro HTTP) |
| `eventType` | `EventTypeNormalizer` extrae la categoría del raw (`APP_EVENT_1034` → `APP_EVENT`) |
| `eventCode` | `EventTypeNormalizer` extrae el sufijo numérico (`APP_EVENT_1034` → `"1034"`) |
| `eventTypeRaw` | El valor original del eventType en UPPERCASE |
| `severity` | Normalización en dos capas: `LogNormalizationUtils.normalizeSeverity()` en ingest, y `SeverityNormalizer` al persistir (`LogEventNormalizationListener` / `LogEventNormalizationCallback`). Reglas: si `status` es `ERROR`/`FATAL` → `ERROR`; si `outcome` o `reason.code` contienen `ERR` → `ERROR`; si no, `severityRaw` mapeado: `DEBUG`/`TRACE`→`DEBUG`, `WARN`/`WARNING`→`WARN`, `LOW`→`INFO`, `MEDIUM`→`WARN`, `CRITICAL`→`ERROR`, fallback `INFO` |
| `severityRaw` | El valor original de `severity` preservado antes de normalizar (lo llenan el listener/callback al persistir) |
| `messageKey` | `LogNormalizationUtils.buildMessageKey()` normaliza el mensaje para deduplicación (quita acentos, minúsculas, tokens `{uuid}`, `{oid}`, `{n}`, `{id}`, trunca a 160 chars) |
| `isError` | `LogNormalizationUtils.computeIsError()` combina severity + status + outcome |

---

## 4. Ingesta de Logs

### 4.1 Endpoint Individual

```http
POST /api/logs/events
X-Api-Key: <tu-api-key>
Content-Type: application/json
```

- Solo acepta **API Key** (no JWT). Si se envía JWT, responde `403 human_jwt_cannot_ingest_logs`.
- Body: `LogEventIngestReq` (ver §3 para todos los campos). Validaciones: `system`, `caseId`, `eventType`, `status`, `message` obligatorios; `eventTime` obligatorio; `outcome` ∈ `SUCCESS|FAILURE|IN_PROGRESS|CANCELED`; `severity` ∈ `DEBUG|INFO|ERROR|FATAL` (en la práctica acepta otros valores y los normaliza, ej. `CRITICAL`).
- Límite: 1 evento por request.
- Responde con `id`, `tenantId`, `system`, `caseId`, `eventTime` del log guardado.

### 4.2 Endpoint Batch

```http
POST /api/logs/events/batch
X-Api-Key: <tu-api-key>
Content-Type: application/json

[ { ...evento1 }, { ...evento2 }, ... ]
```

- Máximo **500 eventos** por request (el excedente se ignora silenciosamente).
- Los errores en eventos individuales no abortan el batch — se cuentan en `skipped`.
- Responde con `{ received, saved, skipped }`.

### 4.3 Pipeline de Normalización

Cada log pasa por el siguiente pipeline antes de guardarse:

```
Input raw del cliente
    │
    ├─ Validar geo.coordinates (si existe): [lng, lat], rango válido
    ├─ system          → trim + UPPERCASE
    ├─ environment     → trim, null si vacío
    ├─ caseId          → trim, null si vacío o "null"
    │
    ├─ EventTypeNormalizer.normalize(eventType)
    │     "APP_EVENT_1034" → {category:"APP_EVENT", code:"1034", raw:"APP_EVENT_1034"}
    │     "LOGIN"          → {category:"LOGIN",     code:null,   raw:"LOGIN"}
    │
    ├─ LogNormalizationUtils.normalizeSeverity("CRITICAL") → "ERROR"
    ├─ LogNormalizationUtils.buildMessageKey(msg, reasonDesc) → clave normalizada
    ├─ LogNormalizationUtils.computeIsError(severity, status, outcome) → boolean
    │
    ├─ Actor, Location, Correlation, Http → trim + UPPERCASE en campos código
    ├─ Tags → lowercase + distinct + filter(blank)
    │
    └─ Save → MongoDB (log_events)
           │   └─ SeverityNormalizer (listener/callback onBeforeConvert):
           │        severityRaw ← severity original
           │        severity    ← recalculada (status/outcome/reason.code + mapeo raw)
           │        messageKey / isError ← recalculados
           ↓
    DeviceRegistryService.upsertFromLog()   ← actualiza devices_registry
    DashboardNotifier.notifyNewLog()        ← WebSocket al frontend
    DashboardNotifier.broadcastLogIngested() ← LogEvent completo a /topic/logs/{system}
```

---

## 5. Consulta y Búsqueda de Logs

### Búsqueda avanzada

```http
GET /api/logs/events?system=TICKETS&status=ERROR&severity=ERROR&fromDate=2025-01-01&toDate=2025-01-31&page=0&size=20
Authorization: Bearer <jwt>
```

**Parámetros disponibles:**

| Parámetro | Descripción |
|---|---|
| `system` | **Requerido.** Sistema a consultar. El usuario debe tener acceso a él (RBAC) |
| `caseId` | Filtrar por caso/transacción específica |
| `eventType` | Filtrar por categoría de tipo de evento |
| `eventCode` | Filtrar por código numérico del evento |
| `status` | Estado del evento |
| `outcome` | Resultado del evento |
| `severity` | Nivel de severidad |
| `actorId` | ID del actor |
| `locationId` | ID de la ubicación |
| `requestId` | ID de correlación del request |
| `deviceId` | Filtrar por dispositivo. **Coincidencia exacta** (sin prefijos) contra `meta.deviceId`, `actor.id` o `actor.fullName` de los eventos — las tres son identidades legítimas del MISMO equipo: el agente escribe `meta.deviceId`/`actor.id` = hostname + último octeto de IP (p. ej. `WIN-MHRBTECSCC7-8`) y el registro (`GET /api/devices`, marcador = `actor.fullName`) usa el hostname (p. ej. `WIN-MHRBTECSCC7`). El filtro se aplica junto con `tenant_id`, `system`, fechas y paginación; `totalElements` refleja solo el conjunto filtrado |
| `text` | Búsqueda de texto libre en `message` y `reason.description` (regex case-insensitive) |
| `fromDate` / `toDate` | Rango de fechas (formato ISO date: `YYYY-MM-DD`) |
| `page` / `size` | Paginación (default: 0 / 10) |
| `sortBy` / `sortDir` | Ordenamiento. Default: `eventTime DESC` |

### Listado general (ALL)

```http
GET /api/logs/events/all?system=TICKETS&page=0&size=1000
Authorization: Bearer <jwt>
```

Mismos filtros que search pero `system` es opcional. Útil para el panel principal del dashboard que muestra todos los eventos dentro del scope del usuario.

### Detalle de un log

```http
GET /api/logs/events/{id}
Authorization: Bearer <jwt>
```

### API legacy

`GET /api/logs`, `GET /api/logs/{id}`, `GET /api/logs/stats/summary`, `GET /api/logs/stats/timeline` y `POST /api/logs` operan sobre la colección legacy `logs` (modelo `LogEntry`: `level`, `processType`, `device`, `scanDevice`, `scanType`, `officeId`, `personId`, `baseCode`, `errorCode`, `sessionToken`, `extra`). Se mantienen por compatibilidad con emisores antiguos.

---

## 6. Timeline de Casos

Permite ver la historia cronológica de un `caseId` — todos los eventos de un caso en orden temporal ascendente.

```http
GET /api/logs/timeline?system=TICKETS&caseId=TKT-2025-001&page=0&size=100
Authorization: Bearer <jwt>
```

- Ordenado por `eventTime ASC` (más antiguo primero).
- Paginación tipo cursor: la respuesta incluye `hasNext` para cargar más.
- Máximo `size=500` por página.
- Devuelve campos resumidos: `eventTime`, `eventType`, `status`, `outcome`, `severity`, `message`, `actor`, `location`, `requestId`, `geo`.
- `LogFilterCriteria.apply()` se aplica aquí también — el VIEWER solo ve los eventos que su perfil permite.

---

## 7. Dashboard — Endpoints y Agregaciones

Base path: `/api/logs/dashboard`  
Requiere permiso: `PERM_LOG_READ`  
Todos los endpoints aceptan `?system=` (opcional) y rango `?from=&to=` (ISO 8601 datetime), o `?fromDate=&toDate=`.  
Default de rango: últimos **30 días**.  
`?range=ALL` (o ausencia de fechas en algunos endpoints) → **sin filtro de fecha**, historial completo.

### 7.1 Stats

```http
GET /api/logs/dashboard/stats?system=TICKETS&from=2025-01-01T00:00:00Z&to=2025-02-01T00:00:00Z
```

**Alimenta:** tarjetas de totales + gráficas de barras/pie del dashboard.

Respuesta `DashboardStatsDto`:

| Campo | Descripción |
|---|---|
| `total` / `totalEvents` | Total de eventos en el rango (mismo valor, dos alias) |
| `errorCount` | Eventos de error en el rango (criterio multicampo) |
| `successCount` | Eventos exitosos en el rango |
| `errorRate` | Tasa de error en el rango (%) |
| `healthStatus` | Salud del rango: `STABLE`, `WARNING`, `CRITICAL`, `INACTIVE` |
| `overallHealth` | Salud general del sistema (histórica) |
| `activeCases` | `caseId` únicos en el rango |
| `totalHistoricalLogs` | Total histórico de logs del sistema (sin filtro de fecha) |
| `topEventTypes` | Top tipos de eventos con conteo y % |
| `outcomes` | Distribución de outcomes (`SUCCESS`, `FAILURE`, etc.) |
| `severities` | Distribución de severidades |
| `statuses` | Distribución de estados |
| `topTags` | Top tags más usados (desanida el array `tags` con `$unwind`) |
| `topLocations` | Top ubicaciones por nombre |
| `topActors` | Top actores por username |
| `environments` | Distribución por entorno |

Cada ítem de distribución tiene: `{ value, count, pct }` donde `pct` es porcentaje con 1 decimal.

### 7.2 Series de Tiempo

```http
GET /api/logs/dashboard/series?system=TICKETS
```

**Alimenta:** gráficas de líneas temporales.

Respuesta `DashboardSeriesDto`:

| Campo | Descripción |
|---|---|
| `byDay` | Conteo por día (`YYYY-MM-DD`, timezone: `America/Mexico_City`) |
| `byWeek` | Conteo por semana (`Semana WW-YYYY`) |
| `byMonth` | Conteo por mes (`MM-YYYY`) |
| `statusOverTime` | Conteo por `{fecha, status}` para gráfica multicolor por estado |

### 7.3 Métricas HTTP

```http
GET /api/logs/dashboard/http?system=API_GATEWAY
```

**Alimenta:** gráfica radar de latencias y tabla de métodos HTTP.

Respuesta `DashboardHttpDto`:

| Campo | Descripción |
|---|---|
| `latencyByStatusAndMethod` | Array de `{statusCode, method, p95Ms, avgMs, count}` — agrupa por código HTTP + método |
| `methodSummary` | Resumen por método: `{method, count, avgMs}` ordenado por count desc |

- `p95Ms`: percentil 95 de latencia. Calculado en memoria sobre los resultados de la agregación.
- `avgMs`: latencia promedio.

### 7.4 Mapa Geográfico

```http
GET /api/logs/dashboard/geo?system=ACCESS_CONTROL
```

**Alimenta:** mapa de puntos y mapa de calor del dashboard.

- Solo incluye logs que tengan `geo.coordinates`.
- Limitado a **2000 puntos** para no saturar el navegador.
- Agrupa por `caseId` (identificador del dispositivo) y toma el log más reciente de cada dispositivo.
- Aplica filtro de integridad sobre `caseId` (debe cumplir patrón `^[A-Z]+[-_][A-Za-z0-9]` y mínimo 5 chars).

Respuesta `DashboardGeoDto`:

```json
{
  "total": 1500,
  "points": [
    { "lon": -99.1332, "lat": 19.4326, "count": 45,
      "caseId": "DEV-001", "usuario": "jgarcia", "ip": "192.168.1.100" },
    ...
  ]
}
```

### 7.5 Salud de Sistemas

```http
GET /api/logs/dashboard/systems-health
```

**Alimenta:** panel de estado de sistemas — tarjetas de salud en el dashboard principal.

- Analiza las **últimas 24 horas** para todos los sistemas del tenant.
- Calcula `errorRate = failures / total` donde `failures` = eventos con `outcome = FAILURE`.
- Umbrales fijos por tasa de error.

Estados posibles:

| Status | Condición |
|---|---|
| `INACTIVE` | Sin logs en las últimas 24h |
| `CRITICAL` | errorRate > 10% |
| `WARNING` | 1% ≤ errorRate ≤ 10% |
| `STABLE` | errorRate < 1% |

- Resultado ordenado: `CRITICAL` → `WARNING` → `STABLE` → `INACTIVE`.
- El RBAC aplica: un VIEWER solo ve los sistemas a los que tiene acceso.

---

## 8. Registro de Dispositivos

**Colección:** `devices_registry`  
**Clase Java:** `backlogs.dinamico.model.log.Device`  
**Service:** `DeviceRegistryService`

Cada vez que llega un log, el sistema **auto-registra o actualiza** el dispositivo que lo envió usando `upsertFromLog()`. No requiere configuración previa — el registro es automático. Se aplican filtros de integridad: se rechazan `deviceId` nulos, `"-"` o de menos de 5 caracteres.

### Campos del Dispositivo

| Campo | Fuente en el Log | Descripción |
|---|---|---|
| `deviceId` | `actor.fullName` del log (fallback: `caseId`) | Identificador único del dispositivo |
| `system` | `system` del log | Sistema al que pertenece |
| `type` | `actor.type` del log | Tipo: `SERVER`, `PC`, `SWITCH`, `CAMERA` |
| `ip` | `meta.ip` del log | IP principal del dispositivo |
| `ipInterfaces` | Calculado (`classifyIp`) | Mapa de interfaces clasificadas por ámbito: claves `privada`, `publica`, `loopback` |
| `hostname` | `actor.fullName` del log | Nombre del host |
| `latitude` / `longitude` | `geo.coordinates` del log | Ubicación geográfica |
| `locationName` | `location.name` del log | Nombre de la ubicación |
| `firstSeen` | Fecha de primer upsert | Cuándo se detectó por primera vez |
| `lastSeen` | Se actualiza en cada log | Último log recibido |
| `lastPowerOn` | eventType `SYSTEM_STARTUP` / `SYSTEM_BOOT_INFO` | Último encendido detectado |
| `lastPowerOff` | eventType `SYSTEM_SHUTDOWN` / `SYSTEM_SHUTDOWN_INIT` | Último apagado detectado |
| `status` | Calculado dinámicamente | `ONLINE` si `lastSeen` < 10 min — `OFFLINE` en caso contrario |

### Endpoint de Dispositivos

```http
GET /api/devices?system=SERVERS&status=OFFLINE
Authorization: Bearer <jwt>
```

| Parámetro | Descripción |
|---|---|
| `system` | Filtrar por sistema (case-insensitive) |
| `status` | `ONLINE` u `OFFLINE` — filtro post-cálculo dinámico |

Respuesta: `{ total, online, offline, byType: {servers, pcs, switches, cameras}, devices: [...] }`

> **Nota:** El status (`ONLINE`/`OFFLINE`) **no se almacena** en MongoDB — se calcula en cada consulta comparando `lastSeen` contra el umbral de **10 minutos** (`OFFLINE_THRESHOLD_MINUTES`).

---

## 9. Seguridad y Control de Acceso (RBAC)

### Roles Relevantes

| Rol | Acceso |
|---|---|
| `ORG_OWNER` | Todos los sistemas del tenant |
| `ORG_ADMIN` | Todos los sistemas del tenant |
| `VIEWER` | Solo los sistemas listados en su `allowedSystems` |

### Lógica de Scope en Servicios

```java
// Pseudocódigo de la lógica aplicada en LogEventService, LogDashboardService, etc.

if (isAdmin || isOwner) {
    // Ve todo el tenant — sin restricción de sistemas
} else if (orgWide && allowedSystems.isEmpty()) {
    // Ve todo el tenant (usuario con acceso amplio sin restricciones)
} else if (allowedSystems tiene valores) {
    query.and("system").in(allowedSystems)  // ← MongoDB filtra en la DB
} else {
    return pageVacía  // Sin acceso
}
```

El acceso por sistema se valida con `ScopeGuard.requireSystemAccess` + `AuthUser.allowedSystems` / `isOrgWide` (bypass para `ORG_ADMIN`/`ORG_OWNER`).

### LogFilterCriteria — Filtros de Visibilidad de Logs

`infra/security/LogFilterCriteria.apply(criteria)` añade restricciones adicionales de visibilidad al nivel de campo:

- Un VIEWER puede tener `logFilters` en su rol que limitan qué **outcomes, statuses, severities o eventTypes** puede ver (`UserRole.LogFilter`: `allowedOutcomes`, `allowedStatuses`, `allowedSeverities`, `allowedEventTypes`).
- Se invoca explícitamente en: `LogEventService.all`, `LogEventService.search`, `LogEventService.timeline` y `LogDashboardService.buildBaseCriteria` (cubre stats, series, http, geo y systems-health).
- Si el usuario no tiene filtros configurados, devuelve el `Criteria` sin cambios.

```java
// Uso estándar en cualquier servicio de logs — una sola línea:
finalCriteria = LogFilterCriteria.apply(finalCriteria);
```

### Endpoint de Sistemas (Dropdown del Dashboard)

```http
GET /api/catalog/systems?page=0&size=100
Authorization: Bearer <jwt>
```

- Devuelve **solo los sistemas** a los que el usuario tiene acceso.
- Un VIEWER con `allowedSystems=["TICKETS"]` recibe únicamente `["TICKETS"]` — nunca ve otros sistemas en el selector.

---

## 10. Notificaciones en Tiempo Real (WebSocket)

**Clase:** `DashboardNotifier`  
**Protocolo:** STOMP sobre WebSocket  
**Endpoint de conexión:** `/ws` (con y sin SockJS; broker en memoria `/topic` y `/queue`, prefijo de aplicación `/app`)

Cuando se ingesta un log, el backend publica automáticamente en:

```
/topic/dashboard/{tenantId}/{SYSTEM_UPPERCASE}   ← evento ligero "hay logs nuevos"
/topic/logs/{SYSTEM_UPPERCASE}                   ← el LogEvent completo recién persistido
/topic/system-health                             ← salud de sistemas (mapa por sistema)
```

Ejemplo:
```
/topic/dashboard/696a76bddc3d6cd1487cdd35/TICKETS
```

El frontend se suscribe a estos topics cuando el usuario selecciona un sistema en el dashboard. Al recibir el evento, el frontend **re-llama** los endpoints de `/stats`, `/series`, etc. para actualizar las gráficas sin necesidad de polling.

**Payload del evento WebSocket** (`/topic/dashboard/{tenantId}/{system}`):
```json
{
  "type": "NEW_LOGS",
  "system": "TICKETS",
  "count": 1,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Payload de `/topic/system-health`:** mapa `{system, tenantId, timestamp, totalEvents24h, errorCount24h, errorRate, healthStatus}` calculado con `DashboardLogService.getStats` sobre las últimas 24h. Cada publicación también **invalida la entrada del caché Caffeine** (`SYSTEM_STATS_CACHE`, clave `tenantId_system_*`) para que la próxima consulta recalcule.

> Si el WebSocket falla al publicar, se loguea un `WARN` pero **no interrumpe** el flujo de ingesta del log.

---

## 11. Retención de Logs

**Scheduler:** `LogRetentionScheduler` — `@Scheduled(cron = "0 0 3 * * *", zone = "America/Mexico_City")`  
**Service:** `LogRetentionService`  
**Ejecución:** Diaria a las **3:00 AM hora Ciudad de México**

- Elimina logs de `log_events` más antiguos que `retentionDays` configurado por organización.
- **Default: 90 días** (`DEFAULT_RETENTION_DAYS = 90`) si la organización no tiene configuración de retención.
- Las **métricas agregadas** (`ai_metrics`) y **alertas** (`ai_alerts`) **NO se eliminan**.
- Procesa en **lotes de 1000** documentos con pausa de 100ms entre lotes para no bloquear MongoDB.
- Itera sobre todas las organizaciones activas (estado ≠ `disabled`).

```
Organización.settings.retentionDays → días a conservar
Si null o 0 → usa DEFAULT_RETENTION_DAYS = 90
```

---

## 12. Índices de MongoDB

La colección `log_events` tiene índices compuestos diseñados para los patrones de consulta más comunes:

| Índice | Campos | Uso principal |
|---|---|---|
| `idx_tenant_system_time_v2` | `tenant_id, system, eventTime DESC` | Listado general por sistema + fecha |
| `idx_tenant_system_case_time_v2` | `tenant_id, system, caseId, eventTime DESC` | Timeline de casos |
| `idx_tenant_system_eventType_time_v2` | `tenant_id, system, eventType, eventTime DESC` | Filtro por tipo de evento |
| `idx_tenant_system_status_time_v2` | `tenant_id, system, status, eventTime DESC` | Filtro por estado |
| `idx_tenant_system_outcome_time_v2` | `tenant_id, system, outcome, eventTime DESC` | Filtro por outcome |
| `idx_tenant_system_severity_time_v2` | `tenant_id, system, severity, eventTime DESC` | Filtro por severidad |
| `idx_tenant_system_requestId_time_v2` | `tenant_id, system, correlation.requestId, eventTime DESC` | Búsqueda por requestId |
| `idx_tenant_system_actor_time_v2` | `tenant_id, system, actor.id, eventTime DESC` | Filtro por actor |
| `idx_tenant_system_location_time_v2` | `tenant_id, system, location.id, eventTime DESC` | Filtro por ubicación |
| `idx_tenant_time_isError_msgKey_v1` | `tenant_id, eventTime DESC, isError, messageKey` | Análisis de errores frecuentes por Eva |
| `idx_tenant_system_eventCode_time_v2` | `tenant_id, system, eventCode, eventTime DESC` | Filtro por código de evento |
| `idx_system_time_v1` | `system, eventTime DESC` | Listado global por sistema + fecha (sin tenant) |
| `idx_system_time_status_outcome_severity_v1` | `system, eventTime DESC, status, outcome, severity` | Agregaciones del dashboard por sistema |
| `idx_time_status_v1` | `eventTime DESC, status` | Series temporales por estado |
| `geo_2dsphere` | `geo` (GeoSpatial) | Consultas geoespaciales y mapa de calor |

**Índice único en `devices_registry`:**
```
idx_device_tenant_deviceId: { tenantId: 1, deviceId: 1 } unique: true
```

> ⚠️ Existe una clase huérfana `model/runtime/LogEvent` (deprecada, `@Deprecated(forRemoval=true)`) que declara índices obsoletos (`ix_main_time`, `ix_sev_time`, `ix_err_time`) sobre la misma colección. Si la auto-creación de índices de Spring Data sigue activa pueden crearse en Mongo; es candidata a eliminación.

---

## 13. Colecciones de MongoDB

| Colección | Descripción | Retención |
|---|---|---|
| `log_events` | Eventos de log — colección principal | Configurable por org (default 90 días) |
| `devices_registry` | Dispositivos detectados automáticamente | Permanente (upsert) |
| `ai_metrics` | Métricas agregadas calculadas por Eva | **No eliminadas** por retención |
| `ai_alerts` | Alertas generadas por Eva | **No eliminadas** por retención |
| `logs` | Logs legacy (modelo `LogEntry`) — API antigua | Según retención |

---

## 14. Mapa de Clases Clave

```
controller/logs/
├── LogEventController       → POST /api/logs/events (ingest, batch) + GET (search, all, detail, timeline)
├── LogDashboardController   → GET /api/logs/dashboard/* (stats, series, http, geo, systems-health)
├── DeviceController         → GET /api/devices (listado de dispositivos filtrable)
├── LogIngestController      → POST /api/logs (ingesta legacy)
└── LogQueryController       → GET /api/logs* (consulta legacy)

service/logs/
├── LogEventService          → Lógica de ingest, search, all, timeline, getById
├── LogDashboardService      → Aggregation pipelines para todas las vistas del dashboard
├── DeviceRegistryService    → Auto-registro y consulta de dispositivos
├── LogRetentionService      → Eliminación de logs expirados por org
├── LogRetentionScheduler    → Cron diario 3AM que ejecuta la retención
├── EventTypeNormalizer      → Separa "APP_EVENT_1034" → {category, code, raw}
└── LogNormalizationUtils    → normalizeSeverity, buildMessageKey, computeIsError

service/normalization/
├── SeverityNormalizer           → Recalcula severity al persistir (status/outcome/reason + mapeo raw)
├── LogEventNormalizationListener → AbstractMongoEventListener: onBeforeConvert del LogEvent
└── LogEventNormalizationCallback → BeforeConvertCallback (duplica la normalización del listener)

service/dashboard/
└── DashboardLogService      → Stats optimizadas ($facet, hint, caché Caffeine) usadas por DashboardNotifier

infra/security/
├── LogFilterCriteria        → Aplica filtros de visibilidad del usuario a cualquier Criteria
└── ApiKeyTenantFilter       → Resuelve tenant desde X-Api-Key en rutas /api/logs*

infra/ws/
└── DashboardNotifier        → Publica eventos WebSocket (dashboard, logs, system-health)

model/log/
├── LogEvent                 → Documento MongoDB principal (log_events)
├── Device                   → Documento MongoDB de dispositivos (devices_registry)
└── LogEntry                 → Documento MongoDB legacy (logs)

api/dto/logs/
├── LogEventIngestReq        → DTO de entrada para ingesta (record con nested records)
├── DashboardStatsDto        → Stats + distribuciones
├── DashboardSeriesDto       → Series de tiempo
├── DashboardHttpDto         → Métricas HTTP (latencia, métodos)
├── DashboardGeoDto          → Puntos geográficos para el mapa
├── SystemHealthDto          → Estado de salud por sistema
└── LogTimelineResponse      → Timeline de un caseId
```

---

## 15. Ejemplo de Payload Completo

```json
POST /api/logs/events
X-Api-Key: sk_prod_abc123...
Content-Type: application/json

{
  "schemaVersion": 1,
  "system": "TICKETS",
  "environment": "PROD",
  "caseId": "TKT-2025-001",
  "eventTime": "2025-01-15T10:30:00Z",
  "eventType": "APP_EVENT_1034",
  "status": "COMPLETED",
  "outcome": "SUCCESS",
  "severity": "INFO",
  "message": "Ticket TKT-2025-001 resuelto por el agente",

  "actor": {
    "id": "USR-456",
    "type": "USER",
    "username": "jgarcia",
    "fullName": "Juan García López"
  },

  "location": {
    "id": "LOC-001",
    "name": "Centro de Soporte CDMX",
    "city": "Ciudad de México",
    "country": "MX"
  },

  "geo": {
    "type": "Point",
    "coordinates": [-99.1332, 19.4326],
    "accuracyMeters": 50
  },

  "correlation": {
    "requestId": "req-xyz-789",
    "traceId": "trace-abc-123",
    "spanId": "span-def-456"
  },

  "http": {
    "method": "PATCH",
    "path": "/api/tickets/TKT-2025-001/resolve",
    "statusCode": 200,
    "latencyMs": 145
  },

  "sla": {
    "startTime": "2025-01-15T09:00:00Z",
    "endTime": "2025-01-15T10:30:00Z",
    "elapsedSeconds": 5400
  },

  "reason": {
    "code": "RESOLVED_BY_AGENT",
    "description": "El agente resolvió el ticket tras diagnóstico remoto"
  },

  "remoteConnection": {
    "sourceIp": "187.190.45.12",
    "sourcePort": 51234,
    "destinationIp": "10.0.0.15",
    "destinationPort": 22,
    "protocol": "SSH",
    "authMethod": "KEY",
    "authResult": "SUCCESS",
    "user": "soporte",
    "sessionId": "ssh-9f8e7d",
    "sessionDuration": 1800,
    "clientType": "TERMINAL",
    "sourceCountry": "MX",
    "sourceCity": "Ciudad de México",
    "isLocalNetwork": false,
    "riskScore": 0.12,
    "metadata": { "clientVersion": "OpenSSH_8.9" }
  },

  "tags": ["support", "resolved", "priority-high"],

  "payload": {
    "ticketCategory": "NETWORK",
    "resolutionCode": "NET_CONFIG_FIX",
    "satisfactionScore": 4.5
  },

  "meta": {
    "ip": "192.168.1.100",
    "appVersion": "2.3.1",
    "platform": "web"
  }
}
```

**Lo que guarda el backend automáticamente (campos calculados):**

```json
{
  "tenant_id": "ObjectId('64a1b2c3d4e5f678901234ab')",
  "eventType": "APP_EVENT",
  "eventCode": "1034",
  "eventTypeRaw": "APP_EVENT_1034",
  "severity": "INFO",
  "severityRaw": "INFO",
  "messageKey": "ticket tkt-{n} resuelto por el agente",
  "isError": false,
  "schemaVersion": 1
}
```
