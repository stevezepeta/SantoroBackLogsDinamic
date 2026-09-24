# Documento de Especificación de Mejoras y Arquitectura
## Panel Operativo para Supervisores de Operaciones

**Proyecto:** BackLogs Dinámico (SaaS multisistema de logs)  
**Frontend objetivo:** Quasar / Vue con WebSockets STOMP y mapa de dispositivos geolocalizados  
**Fecha:** 2026-07-13  
**Objetivo:** Transformar la interfaz y los datos expuestos por el backend desde un formato orientado a desarrolladores hacia un panel operativo intuitivo para Supervisores de Operaciones, sin degradar la experiencia real ni saturar WebSockets.

---

## 1. Diagnóstico del Estado Actual del Backend

### 1.1 Modelo de datos actual: dos realidades coexistiendo

El backend persiste eventos principalmente en la colección `log_events`, pero existen dos modelos concurrentes:

| Modelo | Colección | Ubicación | Estado |
|--------|-----------|-----------|--------|
| `backlogs.dinamico.model.log.LogEvent` | `log_events` | `src/main/java/backlogs/dinamico/model/log/LogEvent.java` | **Activo en ingest y consultas** |
| `backlogs.dinamico.model.runtime.LogEvent` | `log_events` | `src/main/java/backlogs/dinamico/model/runtime/LogEvent.java` | Referenciado por servicios legacy/dashboard/IA |
| `LogEntry` | `logs` | `src/main/java/backlogs/dinamico/model/log/LogEntry.java` | Legacy, aún usado por `LogIngestService`/`LogCommandService` |

> **Riesgo crítico identificado:** dos clases distintas apuntan a la misma colección `log_events`. Esto puede causar ambigüedad de mapeo, errores de deserialización y resultados inconsistentes entre analytics e IA. **La Fase 0 de este plan obliga a consolidar en `model.log.LogEvent`.**

### 1.2 Campos técnicos expuestos actualmente (`LogEvent`)

El documento de MongoDB contiene alta carga técnica:

```json
{
  "_id": { "$oid": "..." },
  "tenant_id": { "$oid": "..." },
  "schemaVersion": 1,
  "system": "TRUSTVALUE",
  "environment": "PROD",
  "caseId": "CAS-2026-001",
  "eventTime": "2026-07-13T14:32:00Z",
  "eventType": "APP_EVENT",
  "eventCode": "ENVIAR_EVIDENCIAS",
  "eventTypeRaw": "ENVIAR_EVIDENCIAS_SUCCESS",
  "status": "SUCCESS",
  "outcome": "COMPLETED",
  "severity": "INFO",
  "severityRaw": "LOW",
  "message": "Evidencias recibidas correctamente",
  "messageKey": "evidence.upload.ok",
  "isError": false,
  "actor": { "id": "USR-123", "type": "USER", "username": "jlopez", "fullName": "Juan López" },
  "location": { "id": "OFF-TOL", "name": "Toluca Centro", "city": "Toluca", "country": "MX" },
  "correlation": { "requestId": "REQ-abc", "traceId": "trace-123", "spanId": "span-456" },
  "http": { "method": "POST", "path": "/api/evidence", "statusCode": 200, "latencyMs": 450 },
  "geo": { "type": "Point", "coordinates": [-99.6557, 19.2826], "accuracyMeters": 8 },
  "payload": { "photoCount": 3, "syncMode": "online" },
  "meta": { "sdkVersion": "2.1.0", "deviceModel": "SM-G991B" }
}
```

### 1.3 Endpoints analíticos actuales (`/api/analytics`)

Solo existen **2 endpoints** bajo `/api/analytics`:

| Método | Endpoint | Respuesta actual | Observación para el supervisor |
|--------|----------|------------------|-------------------------------|
| `GET` | `/api/analytics/funnel/{systemName}` | `FunnelResponseDto` con `eventType`, `conversionRate`, `dropRate` | Útil, pero aún habla en términos técnicos (`eventType`) |
| `GET` | `/api/analytics/funnel-systems/available` | `List<String>` de sistemas | Básico, solo lista IDs de sistemas |

**Ejemplo real de respuesta de funnel hoy:**

```json
{
  "ok": true,
  "code": "ok",
  "message": "Funnel analysis completed",
  "path": "funnel_analysis",
  "timestamp": "2026-07-13T18:19:54Z",
  "data": {
    "system": "TRUSTVALUE",
    "funnelName": "Flujo de Jornada Laboral",
    "summary": {
      "totalStarted": 250,
      "totalCompleted": 180,
      "globalConversionRate": 72.0
    },
    "steps": [
      { "step": 1, "label": "Inicio de Sesión", "eventType": "INICIO_SESION", "count": 250, "conversionRate": 100.0, "dropRate": 0.0 },
      { "step": 2, "label": "Selección de Sucursal", "eventType": "SELECCION_SUCURSAL", "count": 220, "conversionRate": 88.0, "dropRate": 12.0 },
      { "step": 3, "label": "Envío de Evidencias", "eventType": "ENVIAR_EVIDENCIAS", "count": 200, "conversionRate": 80.0, "dropRate": 9.1 },
      { "step": 4, "label": "Cierre de Jornada", "eventType": "FINALIZAR_ASISTENCIA", "count": 180, "conversionRate": 72.0, "dropRate": 10.0 }
    ]
  }
}
```

### 1.4 Diagnóstico de la carga técnica

| Campo técnico | Problema para el supervisor | Impacto |
|---------------|----------------------------|---------|
| `eventType` + `eventCode` | No indica qué pasó en negocio | El supervisor no sabe si es error o éxito sin leer `status` |
| `payload` / `meta` | JSON arbitrario | Puede contener campos irrelevantes o sensibles |
| `correlation.traceId` / `spanId` | Términos de tracing | Inútiles para toma de decisiones operativas |
| `severity` / `severityRaw` | Dos niveles de severidad | Confusión: `INFO` vs `LOW` |
| `http.statusCode` | Código numérico | Requiere traducción mental |
| `reason.code` + `reason.description` | A veces vacíos | Inconsistente para explicar errores |

**Conclusión del diagnóstico:** el backend tiene la información necesaria, pero la presenta cruda. Se requiere una **capa de presentación operativa** que traduzca los datos técnicos a conceptos de negocio, sin modificar el modelo de persistencia.

---

## 2. Módulo de "Traducción Operativa" (Backend Humano)

### 2.1 Objetivo

Crear una capa de servicio en Java que, a partir de un `LogEvent`, genere un objeto operativo amigable para el supervisor. Esta capa se ubicará entre los repositorios de MongoDB y los controladores REST orientados al frontend operativo.

### 2.2 Arquitectura propuesta

```
┌─────────────────┐     ┌──────────────────────────┐     ┌─────────────────┐
│   Controlador   │────▶│  OperationalTranslator   │────▶│   Repositorio   │
│   (analytics)   │     │       Service            │     │  (MongoDB)      │
└─────────────────┘     └──────────────────────────┘     └─────────────────┘
                                 │
                                 ▼
                        ┌─────────────────┐
                        │  MessageCatalog │
                        │  (YAML/DB)      │
                        └─────────────────┘
```

### 2.3 Componentes del módulo

#### A) Catálogo operativo (`OperationalMessageCatalog`)

Fuente de verdad de mensajes amigables. Puede almacenarse en:
- Archivos YAML en `src/main/resources/operational-messages.yml` (v1).
- Colección MongoDB `operational_message_catalog` (v2, editable por administradores).

Estructura sugerida del documento/catálogo:

```yaml
messages:
  ENVIAR_EVIDENCIAS:
    SUCCESS:
      title: "Evidencias cargadas"
      description: "El usuario completó la carga de fotografías con éxito."
      action: "Continuar con el siguiente paso del trámite."
      icon: "image-check"
    REJECTED:
      title: "Fallo al enviar evidencias"
      description: "No se pudieron recibir las fotografías del usuario."
      action: "Verificar conectividad y permisos de cámara."
      icon: "image-broken"
  INICIO_SESION:
    SUCCESS:
      title: "Inicio de sesión"
      description: "El usuario inició sesión correctamente en la app."
      icon: "login"
```

#### B) Servicio de traducción (`OperationalTranslationService`)

```java
@Service
@RequiredArgsConstructor
public class OperationalTranslationService {

    private final OperationalMessageCatalog catalog;

    public OperationalEventCard translate(LogEvent event) {
        String key = resolveKey(event);
        OperationalMessage msg = catalog.find(key, event.getStatus());

        return OperationalEventCard.builder()
            .timestamp(event.getEventTime())
            .system(event.getSystem())
            .caseId(event.getCaseId())
            .actorName(event.getActor() != null ? event.getActor().getFullName() : null)
            .locationName(event.getLocation() != null ? event.getLocation().getName() : null)
            .latitude(extractLatitude(event))
            .longitude(extractLongitude(event))
            .status(mapStatus(event.getStatus(), event.getOutcome(), event.getIsError()))
            .title(msg != null ? msg.getTitle() : event.getEventType())
            .description(msg != null ? msg.getDescription() : event.getMessage())
            .suggestedAction(msg != null ? msg.getAction() : null)
            .icon(msg != null ? msg.getIcon() : "info")
            .hasErrorExplanation(needsExplanation(event))
            .errorExplanation(null) // lazy loading via POST /api/analytics/explain-error/{logId}
            .build();
    }

    private String resolveKey(LogEvent event) {
        if (StringUtils.hasText(event.getEventCode())) {
            return event.getEventCode();
        }
        if (StringUtils.hasText(event.getEventTypeRaw())) {
            return event.getEventTypeRaw();
        }
        return event.getEventType();
    }
}
```

#### C) Estados operativos unificados

| Estado técnico | Estado operativo | Color sugerido |
|----------------|------------------|----------------|
| `SUCCESS` + `COMPLETED` | `Éxito` | Verde |
| `REJECTED`, `FAILED`, `ERROR`, `isError=true` | `Error` | Rojo |
| `PENDING`, `IN_PROGRESS` | `En progreso` | Amarillo |
| `TIMEOUT`, `PARTIAL` | `Advertencia` | Naranja |
| Otros | `Información` | Gris/Azul |

### 2.4 Endpoint de Historial Ejecutivo / Timeline Simplificado

Propuesta de endpoint:

```
GET /api/analytics/executive-timeline
```

**Query params:**

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `system` | String | Sí | Sistema a consultar |
| `caseId` | String | No | Caso específico |
| `actorId` | String | No | Actor específico |
| `locationId` | String | No | Sucursal/oficina |
| `fromDate` | LocalDate | No | Fecha inicial |
| `toDate` | LocalDate | No | Fecha final |
| `page` | int | No | Página (default 0) |
| `size` | int | No | Tamaño (default 20) |

**DTO de respuesta:**

```java
@Data @Builder
public class ExecutiveTimelineResponse {
    private Header header;
    private List<ExecutiveEventCard> events;
    private PageMeta page;

    @Data @Builder
    public static class Header {
        private String system;
        private String caseId;
        private String actorName;
        private String locationName;
        private Instant periodStart;
        private Instant periodEnd;
    }
}

@Data @Builder
public class ExecutiveEventCard {
    private String id;
    private Instant timestamp;
    private OperationalStatus status;
    private String title;
    private String description;
    private String suggestedAction;
    private String icon;
    private String actorName;
    private String locationName;
    private Double latitude;
    private Double longitude;
    private String stepLabel;
    private boolean hasErrorExplanation;
    private String errorExplanation; // null por defecto; usar endpoint lazy
}
```

> **Nota de arquitectura Frontend:** el campo `errorExplanation` siempre será `null` en las respuestas de lista y timeline. El frontend mostrará un botón "Consultar a IA" cuando `hasErrorExplanation == true`. Al hacer clic, se invocará `POST /api/analytics/explain-error/{logId}` y se renderizará la explicación sin bloquear el listado inicial.

**Ejemplo de respuesta JSON:**

```json
{
  "ok": true,
  "code": "ok",
  "message": "Historial ejecutivo generado",
  "path": "executive_timeline",
  "timestamp": "2026-07-13T18:30:00Z",
  "data": {
    "header": {
      "system": "TRUSTVALUE",
      "caseId": "CAS-2026-001",
      "actorName": "Juan López",
      "locationName": "Toluca Centro",
      "periodStart": "2026-07-13T08:00:00Z",
      "periodEnd": "2026-07-13T18:00:00Z"
    },
    "events": [
      {
        "id": "6869abcd1234...",
        "timestamp": "2026-07-13T08:05:00Z",
        "status": "ÉXITO",
        "title": "Inicio de sesión",
        "description": "El usuario inició sesión correctamente en la app.",
        "suggestedAction": null,
        "icon": "login",
        "actorName": "Juan López",
        "locationName": "Toluca Centro",
        "latitude": 19.2826,
        "longitude": -99.6557,
        "stepLabel": "Inicio de Sesión",
        "hasErrorExplanation": false,
        "errorExplanation": null
      },
      {
        "id": "6869abcd5678...",
        "timestamp": "2026-07-13T08:12:00Z",
        "status": "ERROR",
        "title": "Fallo al enviar evidencias",
        "description": "No se pudieron recibir las fotografías.",
        "suggestedAction": "Verificar conectividad y permisos de cámara.",
        "icon": "image-broken",
        "actorName": "Juan López",
        "locationName": "Toluca Centro",
        "latitude": 19.2831,
        "longitude": -99.6549,
        "stepLabel": "Envío de Evidencias",
        "hasErrorExplanation": true,
        "errorExplanation": null
      }
    ],
    "page": { "page": 0, "size": 20, "hasNext": false }
  }
}
```

### 2.5 Viabilidad de implementación

- **Bajo riesgo:** no modifica el modelo de persistencia.
- **Reutilizable:** el mismo servicio puede usarse en `executive-summary`, `user-journey` y `top-frictional-events`.
- **Escalable:** el catálogo se puede migrar de YAML a MongoDB sin cambiar la interfaz del servicio.
- **Seguridad:** respeta `ScopeGuard.requireSystemAccess(...)` y `LogFilterCriteria.apply(...)` existentes.
- **Performance:** la extracción de latitud/longitud desde `geo.coordinates` es O(1) y no requiere joins.

---

## 3. Integración Avanzada de Inteligencia Artificial (IA)

### 3.1 Estado actual de IA en el proyecto

Ya existe una arquitectura de IA consolidada en `src/main/java/backlogs/dinamico/service/ai/`:

| Servicio | Función | Usa LLM externo |
|----------|---------|-----------------|
| `EvaDeepAnalysisService` | Llama a OpenAI `gpt-4o-mini` | ✅ Sí |
| `AiLlmPrettyService` | Embellece resúmenes con Spring AI | ✅ Sí |
| `AiAlertContextService` | Arma contexto de alertas | ❌ Heurístico |
| `AiAlertOperatorExplainService` | Explicación para operadores | ❌ Heurístico |
| `AiDailyManagerService` | Resumen gerencial | ❌ Heurístico |
| `SummaryInsightsService` | Detección de anomalías | ❌ Heurístico |

Configuración actual:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.2
          max-tokens: 700
```

### 3.2 Estrategia de carga: IA bajo demanda (Lazy Loading)

> **Regla de oro:** la generación de explicaciones con LLM **NUNCA** se ejecutará de forma síncrona durante la ingesta de logs ni en el streaming de WebSockets.

**Motivación:**
- Evitar latencia en el dashboard operativo.
- Proteger la estabilidad de WebSockets STOMP.
- Reducir costos de API de OpenAI (solo se paga por explicaciones realmente consultadas).
- Permitir al supervisor decidir cuándo necesita ayuda de la IA.

**Flujo en el Frontend (Quasar/Vue):**

1. El listado/timeline carga rápidamente con `hasErrorExplanation: true/false`.
2. Si `hasErrorExplanation == true`, se muestra un botón "Consultar a IA" en la tarjeta del evento.
3. Al hacer clic, el frontend llama a `POST /api/analytics/explain-error/{logId}`.
4. Se muestra un spinner mientras llega la explicación.
5. La respuesta se cachea en memoria del cliente (Vuex/Pinia) para evitar re-llamadas.

### 3.3 Endpoint de explicación de errores bajo demanda

```
POST /api/analytics/explain-error/{logId}
```

**Descripción:** genera una explicación en lenguaje natural de un evento de error específico.

**Path variable:** `logId` — identificador del `LogEvent`.

**DTO de respuesta:**

```java
@Data @Builder
public class ErrorExplanationResponse {
    private String logId;
    private String system;
    private String caseId;
    private String eventCode;
    private ErrorExplanation explanation;
    private Instant generatedAt;
    private String source; // LLM | HEURISTIC | CACHE
}

@Data @Builder
public class ErrorExplanation {
    private String summary;
    private String likelyCause;
    private String businessImpact;
    private String recommendedAction;
    private String confidence;
}
```

**Ejemplo de respuesta JSON:**

```json
{
  "ok": true,
  "code": "ok",
  "message": "Explicación generada",
  "path": "explain_error",
  "timestamp": "2026-07-13T18:35:12Z",
  "data": {
    "logId": "6869abcd5678...",
    "system": "TRUSTVALUE",
    "caseId": "CAS-2026-001",
    "eventCode": "ENVIAR_EVIDENCIAS",
    "explanation": {
      "summary": "El empleado no pudo enviar las fotografías de evidencia.",
      "likelyCause": "El dispositivo perdió conexión con el servidor durante la carga.",
      "businessImpact": "La jornada laboral no queda registrada con evidencia fotográfica.",
      "recommendedAction": "Pedir al empleado que verifique su conexión a internet y reintente la carga.",
      "confidence": "ALTA"
    },
    "generatedAt": "2026-07-13T18:35:10Z",
    "source": "LLM"
  }
}
```

### 3.4 Servicio Java propuesto

```java
@Service
@RequiredArgsConstructor
public class OperationalErrorExplainerService {

    private final AiLlmPrettyService llmPrettyService;
    private final LogEventRepository logEventRepository;
    private final ObjectMapper objectMapper;

    public ErrorExplanationResponse explain(Authentication auth, ObjectId logId) {
        LogEvent event = logEventRepository.findByIdAndTenantId(logId, TenantContext.requireTenantId())
            .orElseThrow(() -> new NotFoundException("log_event", logId.toHexString()));

        scopeGuard.requireSystemAccess(auth, event.getSystem());

        if (!needsExplanation(event)) {
            return ErrorExplanationResponse.builder()
                .logId(logId.toHexString())
                .system(event.getSystem())
                .caseId(event.getCaseId())
                .eventCode(event.getEventCode())
                .explanation(null)
                .source("NOT_APPLICABLE")
                .build();
        }

        String context = buildContext(event);
        String json = llmPrettyService.explainErrorForSupervisor(context);

        ErrorExplanation explanation;
        String source;
        try {
            explanation = objectMapper.readValue(json, ErrorExplanation.class);
            source = "LLM";
        } catch (Exception e) {
            explanation = fallbackExplanation(event);
            source = "HEURISTIC";
        }

        return ErrorExplanationResponse.builder()
            .logId(logId.toHexString())
            .system(event.getSystem())
            .caseId(event.getCaseId())
            .eventCode(event.getEventCode())
            .explanation(explanation)
            .generatedAt(Instant.now())
            .source(source)
            .build();
    }

    private boolean needsExplanation(LogEvent event) {
        return Boolean.TRUE.equals(event.getIsError())
            || "REJECTED".equalsIgnoreCase(event.getStatus())
            || "ERROR".equalsIgnoreCase(event.getOutcome())
            || "FAILED".equalsIgnoreCase(event.getOutcome())
            || (event.getHttp() != null && event.getHttp().getStatusCode() != null
                && event.getHttp().getStatusCode() >= 400);
    }
}
```

### 3.5 Prompt sugerido para el LLM

```
Eres un asistente de operaciones que explica errores técnicos a supervisores no técnicos.
Usa ÚNICAMENTE los datos proporcionados. No inventes causas.
Si no hay suficiente evidencia, indícalo como "posible causa" y baja la confianza.
Responde con un JSON válido con esta estructura:
{
  "summary": "Una frase simple que entienda un supervisor.",
  "likelyCause": "Causa probable basada solo en los datos.",
  "businessImpact": "Impacto en la operación del usuario o cliente.",
  "recommendedAction": "Acción concreta que puede tomar el supervisor.",
  "confidence": "ALTA|MEDIA|BAJA"
}

DATOS DEL EVENTO:
System: {system}
Sucursal: {locationName}
Evento: {eventType} / {eventCode}
Estado: {status}
Mensaje técnico: {message}
HTTP status: {httpStatusCode}
Reason code: {reasonCode}
Reason description: {reasonDescription}
Payload relevante: {payload}
```

### 3.6 Análisis predictivo de embudos (Funnels)

#### A) Concepto

Extender `FunnelAnalyticsService` para comparar el embudo actual contra ventanas históricas y generar alertas narrativas automáticas.

#### B) Lógica de detección de anomalías en funnel

```
Para cada paso del funnel:
  dropRateHoy = abandono del paso hoy
  dropRateVentana = abandono promedio de los últimos N días (ej. 7 o 30)
  delta = dropRateHoy - dropRateVentana

  Si delta > umbral (ej. +10 puntos porcentuales):
     generar alerta operativa
```

#### C) Servicio propuesto: `FunnelInsightService`

```java
@Service
@RequiredArgsConstructor
public class FunnelInsightService {

    private final FunnelTemplateRepository funnelTemplateRepository;
    private final FunnelAnalyticsService funnelService;
    private final AiLlmPrettyService llmService;

    public List<FunnelInsight> analyzeFunnelTrends(Authentication auth,
                                                    String systemName,
                                                    Instant todayFrom,
                                                    Instant todayTo,
                                                    int lookbackDays) {

        FunnelResponseDto today = funnelService.calculateFunnel(auth, systemName, todayFrom, todayTo);

        Instant historicalFrom = todayFrom.minusSeconds(lookbackDays * 24L * 3600);
        Instant historicalTo = todayTo.minusSeconds(24L * 3600);
        FunnelResponseDto historical = funnelService.calculateFunnel(auth, systemName, historicalFrom, historicalTo);

        List<FunnelInsight> insights = new ArrayList<>();
        for (int i = 0; i < today.getSteps().size(); i++) {
            FunnelStepDto todayStep = today.getSteps().get(i);
            double historicalDrop = findHistoricalDropRate(historical, i);
            double delta = todayStep.getDropRate() - historicalDrop;

            if (Math.abs(delta) >= 10.0) {
                insights.add(FunnelInsight.builder()
                    .stepLabel(todayStep.getLabel())
                    .dropRateToday(todayStep.getDropRate())
                    .dropRateHistorical(historicalDrop)
                    .delta(delta)
                    .severity(delta >= 15.0 ? "CRITICAL" : "WARNING")
                    .message(generateMessage(todayStep, delta, today.getFunnelName()))
                    .build());
            }
        }
        return insights;
    }
}
```

#### D) Ejemplo de mensaje generado

```json
{
  "severity": "WARNING",
  "system": "TRUSTVALUE",
  "location": "Toluca Centro",
  "step": "Envío de Evidencias",
  "message": "Alerta: El abandono en el paso 'Envío de Evidencias' aumentó un 15% hoy en la sucursal Toluca.",
  "dropRateToday": 24.0,
  "dropRateAverage": 9.0,
  "recommendedAction": "Revisar conectividad WiFi y capacidad de almacenamiento en dispositivos de la sucursal."
}
```

#### E) Integración con alertas existentes

Las alertas de funnel se pueden persistir en la colección `ai_alerts` usando el modelo `AiAlertRecord`, aprovechando:

- `AiAlertRepository`
- `AiAlertContextService`
- `AlertSchedulerService` (job cada 60 min)

Así el supervisor las recibiría por los mismos canales: dashboard, email, FCM y WebSocket.

---

## 4. Propuesta de Nuevos Endpoints Analíticos para el Frontend

### 4.1 Resumen ejecutivo global

```
GET /api/analytics/executive-summary
```

**Descripción:** tablero de salud operativa con semáforos, KPIs globales y alertas activas.

**Query params:**

| Parámetro | Tipo | Requerido | Default |
|-----------|------|-----------|---------|
| `fromDate` | LocalDate | No | Hoy - 7 días |
| `toDate` | LocalDate | No | Hoy |

**DTO de respuesta:**

```java
@Data @Builder
public class ExecutiveSummaryResponse {
    private Instant generatedAt;
    private Period period;
    private HealthStatus overallHealth;
    private List<SystemHealth> systems;
    private List<ExecutiveAlert> activeAlerts;
    private TopMetrics topMetrics;
}

@Data @Builder
public class SystemHealth {
    private String system;
    private String systemLabel;
    private String status; // STABLE | WARNING | CRITICAL
    private long totalEvents;
    private long errorCount;
    private double errorRate;
    private long activeCases;
    private double completionRate;
    private String lastIncident;
}

@Data @Builder
public class ExecutiveAlert {
    private String id;
    private String severity;
    private String title;
    private String message;
    private String system;
    private Instant timestamp;
}
```

**Ejemplo de respuesta JSON:**

```json
{
  "ok": true,
  "code": "ok",
  "message": "Resumen ejecutivo generado",
  "path": "executive_summary",
  "timestamp": "2026-07-13T18:30:00Z",
  "data": {
    "generatedAt": "2026-07-13T18:30:00Z",
    "period": { "from": "2026-07-06", "to": "2026-07-13" },
    "overallHealth": "WARNING",
    "systems": [
      {
        "system": "TRUSTVALUE",
        "systemLabel": "Control de Asistencia",
        "status": "STABLE",
        "totalEvents": 45200,
        "errorCount": 320,
        "errorRate": 0.71,
        "activeCases": 120,
        "completionRate": 94.5,
        "lastIncident": null
      },
      {
        "system": "CITA_GUYANA",
        "systemLabel": "Citas Guyana",
        "status": "CRITICAL",
        "totalEvents": 12300,
        "errorCount": 1845,
        "errorRate": 15.0,
        "activeCases": 45,
        "completionRate": 62.0,
        "lastIncident": "2026-07-13T16:45:00Z"
      }
    ],
    "activeAlerts": [
      {
        "id": "alert-001",
        "severity": "CRITICAL",
        "title": "Caída crítica en Citas Guyana",
        "message": "La tasa de errores en Citas Guyana superó el 10% en las últimas 4 horas.",
        "system": "CITA_GUYANA",
        "timestamp": "2026-07-13T16:45:00Z"
      }
    ],
    "topMetrics": {
      "totalEvents": 87500,
      "totalErrors": 2560,
      "globalErrorRate": 2.93,
      "abandonedCases": 320,
      "avgCompletionTimeMinutes": 12.4
    }
  }
}
```

### 4.2 Línea de tiempo visual del usuario con geolocalización

```
GET /api/analytics/user-journey/{caseId}
```

**Descripción:** muestra paso a paso el recorrido de un caso específico, traducido a lenguaje operativo e incluyendo coordenadas geográficas para visualización en mapa.

**Query params:**

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `system` | String | Sí | Sistema del caso |
| `includeExplanations` | boolean | No | Incluir explicaciones IA (default true como placeholders) |

**DTO de respuesta:**

```java
@Data @Builder
public class UserJourneyResponse {
    private String caseId;
    private String system;
    private JourneySummary summary;
    private List<JourneyStep> steps;
}

@Data @Builder
public class JourneySummary {
    private Instant startedAt;
    private Instant finishedAt;
    private String finalStatus;
    private long totalSteps;
    private long successfulSteps;
    private long failedSteps;
    private Long durationSeconds;
    private String actorName;
    private String locationName;
}

@Data @Builder
public class JourneyStep {
    private int order;
    private Instant timestamp;
    private String stepLabel;
    private String status;
    private String title;
    private String description;
    private String suggestedAction;
    private Double latitude;
    private Double longitude;
    private String locationName;
    private ErrorExplanation errorExplanation;
    private boolean hasErrorExplanation;
    private String durationFromPrevious;
}
```

> **Integración con mapa del Frontend:** cada `JourneyStep` contiene `latitude`, `longitude` y `locationName`. El dashboard de Quasar/Vue puede:
> 1. Colocar marcadores numerados en el mapa para cada paso.
> 2. Trazar una polilínea animada entre los puntos para mostrar el recorrido geográfico real.
> 3. Al hacer clic en un marcador, mostrar la tarjeta operativa del paso (`title`, `description`, `status`).
> 4. Resaltar en rojo los pasos con error y mostrar el botón "Consultar a IA".

**Ejemplo de respuesta JSON:**

```json
{
  "ok": true,
  "code": "ok",
  "message": "Journey del usuario recuperado",
  "path": "user_journey",
  "timestamp": "2026-07-13T18:30:00Z",
  "data": {
    "caseId": "CAS-2026-001",
    "system": "TRUSTVALUE",
    "summary": {
      "startedAt": "2026-07-13T08:05:00Z",
      "finishedAt": "2026-07-13T08:22:00Z",
      "finalStatus": "COMPLETADO_CON_ERRORES",
      "totalSteps": 4,
      "successfulSteps": 3,
      "failedSteps": 1,
      "durationSeconds": 1020,
      "actorName": "Juan López",
      "locationName": "Toluca Centro"
    },
    "steps": [
      {
        "order": 1,
        "timestamp": "2026-07-13T08:05:00Z",
        "stepLabel": "Inicio de Sesión",
        "status": "ÉXITO",
        "title": "Inicio de sesión",
        "description": "El usuario inició sesión correctamente.",
        "latitude": 19.2826,
        "longitude": -99.6557,
        "locationName": "Toluca Centro",
        "hasErrorExplanation": false,
        "durationFromPrevious": "0s"
      },
      {
        "order": 2,
        "timestamp": "2026-07-13T08:07:00Z",
        "stepLabel": "Selección de Sucursal",
        "status": "ÉXITO",
        "title": "Sucursal seleccionada",
        "description": "El usuario eligió la sucursal Toluca Centro.",
        "latitude": 19.2828,
        "longitude": -99.6555,
        "locationName": "Toluca Centro",
        "hasErrorExplanation": false,
        "durationFromPrevious": "2m"
      },
      {
        "order": 3,
        "timestamp": "2026-07-13T08:12:00Z",
        "stepLabel": "Envío de Evidencias",
        "status": "ERROR",
        "title": "Fallo al enviar evidencias",
        "description": "No se pudieron recibir las fotografías.",
        "suggestedAction": "Verificar conectividad y permisos de cámara.",
        "latitude": 19.2831,
        "longitude": -99.6549,
        "locationName": "Toluca Centro",
        "hasErrorExplanation": true,
        "errorExplanation": null,
        "durationFromPrevious": "5m"
      },
      {
        "order": 4,
        "timestamp": "2026-07-13T08:22:00Z",
        "stepLabel": "Cierre de Jornada",
        "status": "ÉXITO",
        "title": "Jornada cerrada",
        "description": "El usuario finalizó su jornada laboral.",
        "latitude": 19.2835,
        "longitude": -99.6542,
        "locationName": "Toluca Centro",
        "hasErrorExplanation": false,
        "durationFromPrevious": "10m"
      }
    ]
  }
}
```

### 4.3 Eventos de mayor fricción

```
GET /api/analytics/top-frictional-events
```

**Descripción:** lista los 5 eventos o errores que más retrasos o abandonos causan a los usuarios en el día.

**Query params:**

| Parámetro | Tipo | Requerido | Default |
|-----------|------|-----------|---------|
| `date` | LocalDate | No | Hoy |
| `system` | String | No | Todos los permitidos |
| `limit` | int | No | 5 |

**DTO de respuesta:**

```java
@Data @Builder
public class TopFrictionalEventsResponse {
    private LocalDate date;
    private List<FrictionalEvent> events;
}

@Data @Builder
public class FrictionalEvent {
    private int rank;
    private String eventCode;
    private String title;
    private String description;
    private long occurrenceCount;
    private long affectedCases;
    private double percentageOfTotalErrors;
    private String trend; // UP | DOWN | STABLE
    private double trendPercentage;
    private String system;
    private String topLocation;
    private String recommendedAction;
}
```

**Ejemplo de respuesta JSON:**

```json
{
  "ok": true,
  "code": "ok",
  "message": "Top eventos de fricción generado",
  "path": "top_frictional_events",
  "timestamp": "2026-07-13T18:30:00Z",
  "data": {
    "date": "2026-07-13",
    "events": [
      {
        "rank": 1,
        "eventCode": "ENVIAR_EVIDENCIAS",
        "title": "Fallo al enviar evidencias",
        "description": "Los empleados no pueden cargar las fotografías de evidencia.",
        "occurrenceCount": 145,
        "affectedCases": 132,
        "percentageOfTotalErrors": 38.5,
        "trend": "UP",
        "trendPercentage": 15.2,
        "system": "TRUSTVALUE",
        "topLocation": "Toluca Centro",
        "recommendedAction": "Revisar conectividad WiFi y permisos de cámara en la sucursal Toluca Centro."
      },
      {
        "rank": 2,
        "eventCode": "INICIO_SESION",
        "title": "Error al iniciar sesión",
        "description": "Los usuarios no pueden acceder a la aplicación.",
        "occurrenceCount": 98,
        "affectedCases": 87,
        "percentageOfTotalErrors": 26.0,
        "trend": "STABLE",
        "trendPercentage": 0.0,
        "system": "CITA_GUYANA",
        "topLocation": "Mérida",
        "recommendedAction": "Verificar estado del servicio de autenticación."
      }
    ]
  }
}
```

---

## 5. Plan de Implementación Sugerido

### Fase 0: Consolidación de modelos (Obligatoria antes de cualquier desarrollo)

1. **Eliminar referencias a `backlogs.dinamico.model.runtime.LogEvent`** en todos los servicios de analytics, IA y dashboard.
2. Migrar `PassportEventService`, `AiAlertContextService` y cualquier controlador legacy al modelo `backlogs.dinamico.model.log.LogEvent`.
3. Validar que todas las agregaciones sobre `log_events` usen los nombres de campo correctos del modelo activo (`tenant_id`, `system`, `caseId`, `eventTime`, `eventType`, `eventCode`, `status`, `outcome`, `severity`, `message`, `actor`, `location`, `geo`, `http`, `reason`, `payload`, `meta`, `isError`, `messageKey`).
4. Ejecutar suite de tests y verificar que no haya regresiones en ingest, consultas ni funnel.
5. Marcar `model.runtime.LogEvent` como `@Deprecated` o eliminarlo tras la validación.

> **Sin esta fase, los nuevos endpoints operativos podrían leer/escribir documentos con mapeos inconsistentes, generando resultados erróneos en producción.**

### Fase 1: Cimientos de traducción operativa (1-2 semanas)

1. Crear `OperationalMessageCatalog` en YAML.
2. Implementar `OperationalTranslationService` y DTOs `OperationalEventCard`, `OperationalStatus`.
3. Agregar helpers de extracción de coordenadas desde `LogEvent.GeoPoint`.

### Fase 2: Historial ejecutivo y geolocalización (1 semana)

1. Crear `ExecutiveTimelineService` reutilizando `LogEventService.timeline(...)`.
2. Implementar `GET /api/analytics/executive-timeline` con latitud/longitud.
3. Implementar `GET /api/analytics/user-journey/{caseId}` con campos geográficos.
4. Agregar tests unitarios para el catálogo y la traducción.

### Fase 3: IA operativa bajo demanda (2 semanas)

1. Crear `OperationalErrorExplainerService` usando `AiLlmPrettyService`.
2. Implementar `POST /api/analytics/explain-error/{logId}` con fallback heurístico.
3. Asegurar que `executive-timeline` y `user-journey` devuelvan `hasErrorExplanation: true` con `errorExplanation: null`.
4. Extender `FunnelAnalyticsService` con cálculo histórico.
5. Crear `FunnelInsightService` para detección de anomalías y mensajes.
6. Integrar alertas de funnel en `ai_alerts`.

### Fase 4: Nuevos endpoints (2 semanas)

1. Implementar `GET /api/analytics/executive-summary`.
2. Implementar `GET /api/analytics/top-frictional-events`.
3. Documentar contratos en Swagger/OpenAPI.

### Fase 5: Frontend coordinado (paralelo)

1. **Mapa de User Journey:**
   - Renderizar marcadores numerados por cada `JourneyStep` usando `latitude`/`longitude`.
   - Trazar polilínea animada entre puntos con color según `status`.
   - Mostrar popup al clicar marcador con `title`, `description` y botón "Consultar a IA".
2. **Lazy loading IA:**
   - En tarjetas de error, mostrar botón "Consultar a IA" solo si `hasErrorExplanation == true`.
   - Llamar `POST /api/analytics/explain-error/{logId}` bajo demanda.
   - Cachear respuestas en Pinia/Vuex para evitar re-llamadas.
3. **Dashboard ejecutivo:**
   - Consumir `/api/analytics/executive-summary`.
   - Pintar semáforos con colores según `status` (`STABLE`, `WARNING`, `CRITICAL`).
   - Listar alertas activas y permitir navegar al detalle.
4. **WebSockets:** mantener el flujo de notificaciones push sin incluir explicaciones IA en el payload; solo notificar que hay errores nuevos disponibles.

---

## 6. Archivos Clave del Proyecto Relacionados

| Propósito | Ruta |
|-----------|------|
| Entidad de log activa | `src/main/java/backlogs/dinamico/model/log/LogEvent.java` |
| Modelo a deprecar/consolidar | `src/main/java/backlogs/dinamico/model/runtime/LogEvent.java` |
| Servicio de logs | `src/main/java/backlogs/dinamico/service/logs/LogEventService.java` |
| Controlador de logs | `src/main/java/backlogs/dinamico/controller/logs/LogEventController.java` |
| Controlador de analytics | `src/main/java/backlogs/dinamico/controller/analytics/AnalyticsController.java` |
| Servicio de funnel | `src/main/java/backlogs/dinamico/service/analytics/FunnelAnalyticsService.java` |
| Template de funnel | `src/main/java/backlogs/dinamico/model/analytics/FunnelTemplate.java` |
| Wrapper de respuesta | `src/main/java/backlogs/dinamico/api/ApiResponse.java` |
| DTOs de funnel | `src/main/java/backlogs/dinamico/api/dto/analytics/FunnelResponseDto.java` |
| Servicio LLM | `src/main/java/backlogs/dinamico/service/ai/AiLlmPrettyService.java` |
| Servicio de explicación de alertas | `src/main/java/backlogs/dinamico/service/ai/AiAlertOperatorExplainService.java` |
| Contexto de tenant | `src/main/java/backlogs/dinamico/tenant/TenantContext.java` |
| Usuario autenticado | `src/main/java/backlogs/dinamico/infra/security/AuthUser.java` |

---

## 7. Consideraciones Finales

- **No se propone modificar** el esquema de persistencia de `log_events`; solo agregar una capa de presentación.
- **La IA es lazy y opcional:** las listas devuelven `hasErrorExplanation` como indicador visual, pero la explicación real se obtiene vía `POST /api/analytics/explain-error/{logId}`. Si el LLM falla, se usa fallback heurístico.
- **Geolocalización integrada:** los pasos del `user-journey` incluyen `latitude`, `longitude` y `locationName` para permitir la visualización del recorrido en el mapa del dashboard.
- **Consolidación obligatoria:** todo el código nuevo debe acoplarse exclusivamente a `model.log.LogEvent`. La Fase 0 es prerequisito no negociable.
- **Seguridad:** todos los nuevos endpoints deben usar `@PreAuthorize("hasAuthority('PERM_LOG_READ')")`, `ScopeGuard.requireSystemAccess(...)` y `LogFilterCriteria.apply(...)` como los actuales.
- **Performance:** las agregaciones de MongoDB para `executive-summary` y `top-frictional-events` deben reutilizar los índices compuestos existentes (`tenant_id`, `system`, `eventTime`, `eventType`, `status`).
- **Extensibilidad:** el catálogo operativo permite agregar nuevos sistemas y eventos sin cambiar código.

---

*Documento refinado para evaluación de viabilidad técnica y coordinación con el equipo de Frontend Quasar/Vue.*
