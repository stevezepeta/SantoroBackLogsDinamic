# VALIDACIÓN BACKEND — INFORME EJECUTIVO PASSPORT_GY

**Fecha:** 2026-10-19
**Alcance:** código fuente del backend (`src/main/java`, `src/main/resources`) del proyecto SantoroBackLogsDinamic.
**Restricciones respetadas:** no se modificó código, no se ejecutaron migraciones, no se consultó ni alteró producción, no se desplegaron cambios. Todo lo que no se pudo verificar contra datos reales se marca expresamente.

---

## 0. Nota previa: documentos base de Codex no disponibles

Los documentos `INFORME_EJECUTIVO_PASSPORT_GY_BASE.md` y `MATRIZ_TRAZABILIDAD_PASSPORT_GY.md` **no existen en este repositorio** (se buscó en todo el árbol de trabajo, incluidos `docs/` y `docs/archive/`). En consecuencia:

- La validación se realizó **contra el código real del backend**, que es la fuente primaria.
- La sección "afirmaciones de Codex: confirmar / corregir / pendiente" (sección 7) queda **parcialmente pendiente**: no es posible cotejar afirmaciones texto a texto sin esos archivos. Se lista qué clases de afirmaciones el backend **sí respalda o refuta**, para que el cotejo sea inmediato cuando se aporten los documentos.
- `PASSPORT_GY` no aparece en ningún archivo de código, recursos, seeds ni configuración del backend. Solo aparece en dos documentos no versionados (`docs/M0_DIAGNOSTICO_COBERTURA.md`, `docs/PROPUESTA_INTELIGENCIA_ACTIVIDAD_USUARIOS.md`) y en `prompt.txt`. **El backend no "conoce" a PASSPORT_GY como entidad**: cualquier sistema que envíe eventos con `system="PASSPORT_GY"` es aceptado sin registro previo.

---

## 1. Catálogo de procesos

### 1.1 Cómo el backend recibe, normaliza, almacena y clasifica eventos

**Recepción.** La vía real y única de ingesta universal es:

- `POST /api/logs/events` — evento individual (`controller/logs/LogEventController.java:29`).
- `POST /api/logs/events/batch` — lote, tope duro de 500 eventos por solicitud; los ítems fallidos se omiten silenciosamente con conteo `{received, saved, skipped}` (`LogEventController.java:50-60`).

Autenticación exclusiva por **API key** (header `X-Api-Key`), validada en `infra/security/ApiKeyTenantFilter.java` (hash SHA-256, estado `active`, scope `LOGS_INGEST|INGEST|ALL`), con rate limit por key y por tenant, y doble blindaje en servicio (`LogEventService.ingest:373-377` rechaza llamadas con JWT humano). La key fija `TenantContext` (tenantId, systemId, environmentId).

Existe una vía heredada `POST /api/logs` (`LogIngestController`) con formato antiguo de biometría (LogEntry: personId, scanType, baseCode…), no es el esquema universal y no aplica a PASSPORT_GY.

**Normalización** (`service/logs/LogEventService.java:365-542` y helpers):

- `system` → mayúsculas; `environment/status/outcome/severity` → mayúsculas; trim; `"null"` → null.
- `eventTime`: si el emisor no lo manda, se usa `Instant.now()`.
- `EventTypeNormalizer` (líneas 28-46): divide un sufijo numérico `CATEGORIA_1034 → eventType="CATEGORIA", eventCode="1034", eventTypeRaw` completo. Sin sufijo, `eventCode=null`; en blanco, `"UNKNOWN"`.
- Severidad: `INFO/WARN/ERROR/FATAL` pasan directo; mapping Elyctis `LOW→INFO, MEDIUM→WARN, CRITICAL→ERROR`; valores desconocidos pasan sin cambio.
- `messageKey`: clave de deduplicación (sin acentos, máscara de UUID/ObjectIds/patrones `pass-\d{4}-\d+`, `bio-\d{4}-\d+`, números), truncada a 160 caracteres.
- `isError` (`LogNormalizationUtils.computeIsError:55-60`): `true` si severity ∈ {ERROR, FATAL}, o `outcome=FAILURE`, o `status ∈ {REJECTED, ERROR}`, u `outcome` contiene "ERR".
- Validación de geo (rango lon/lat) y normalización de tags. Sin otra validación de negocio.

**Almacenamiento.** Colección única `log_events`, esquema v2 en `model/log/LogEvent.java`, con 13 índices compuestos (tenant/system/eventTime/status/outcome/severity/caseId/requestId/actor/location/eventCode) + geo 2dsphere. ⚠️ Existe un segundo modelo `model/runtime/LogEvent.java` marcado `@Deprecated(since="2026-07-13", forRemoval=true)` y sin ningún uso — es código muerto; cualquier documento que lo cite como esquema activo debe corregirse.

**Clasificación — hallazgo central.** Los catálogos `EventType`, `ErrorCode` y `SystemApp` (`model/catalog/`) existen y tienen CRUD, pero **la ingesta jamás los consulta**: `eventType`, `eventCode`, `system`, `status` y `outcome` se aceptan como cadenas libres; ni siquiera se contrasta `system` contra el sistema ligado a la API key. La clasificación es 100 % dinámica: **lo que el emisor envía, se almacena**. El único artefacto con clasificación por sistema es `src/main/resources/operational-messages.yml` (textos legibles por sistema/código para explicaciones operativas), informativo, no validador.

### 1.2 Los 12 procesos del alcance

Búsqueda exhaustiva de `FACIAL, SESION, EXPEDICION, AUTORIZACION, DATOS_BIOGRAFICOS, FIRMA, HUELLAS, IMPRESION, QA, LAMINACION, ENTREGA, PASSPORT_EXP` en `src/`, recursos y tests:

| Proceso | En código/recursos | Naturaleza |
|---|---|---|
| FACIAL | 0 ocurrencias | No instrumentado |
| SESION | Solo como subcadena de `INICIO_SESION` (TrustValue, seeds de funnel) y regex de diagnóstico `AUTH_EVENT_REGEX` | No instrumentado como proceso propio |
| EXPEDICION | 0 | No instrumentado |
| AUTORIZACION | 0 | No instrumentado |
| DATOS_BIOGRAFICOS | 0 | No instrumentado |
| FIRMA | Solo subcadena de `CONFIRMACION_CITA` | No instrumentado |
| HUELLAS | 0 | No instrumentado |
| IMPRESION | 0 | No instrumentado |
| QA | 0 | No instrumentado |
| LAMINACION | 0 | No instrumentado |
| ENTREGA | 0 (palabra común solo en docs) | No instrumentado |
| PASSPORT_EXP | 0 | No instrumentado |

**Conclusión: ninguno de los 12 procesos está instrumentado en el backend.** El backend es agnóstico de esquema; si PASSPORT_GY emite eventos con `eventType="FACIAL"`, `eventType="EXPEDICION"`, etc., se almacenan y son consultables como cadenas, sin validación, catálogo ni semántica propia. "Recibido" ≠ "procesado como proceso conocido".

Lo más parecido a procesos de pasaporte "esperados" es la plantilla de funnel sembrada `Flujo de Tramitación de Pasaporte` (`insert-funnel-templates.ps1:189-225`, `docs/FUNNEL_TEMPLATES_SEED.md`): pasos `SOLICITUD_PASAPORTE → VALIDACION_DOCUMENTOS → APROBACION_PASAPORTE → EMISION_PASAPORTE`, `systemName="PASSPORT"`. Es un artefacto de analítica en BD, **no** validación en ingesta, y usa `system="PASSPORT"`, no `PASSPORT_GY` ni los 12 nombres del alcance.

Adicional: `PassportDashboardController.java:32` tiene el sistema de pasaportes **hardcodeado a `PASSPORT_PA`**. Los endpoints `/api/dashboard/passports/*` (summary, by-office, by-type, events, timeline) agregan `log_events WHERE system="PASSPORT_PA"`. **Eventos de PASSPORT_GY llegarían a la búsqueda genérica de logs y a la línea de tiempo universal, pero NO al dashboard de pasaportes** sin cambio de código.

### 1.3 Matriz de validación

> No existe EVENTTYPE/EVENTCODE canónico para ninguno de los 12 procesos; los valores indicados son los que PASSPORT_GY debería enviar para ser registrados, según el contrato de ingesta.

| PROCESO | EVENTTYPE | EVENTCODE | CAMPOS (al aceptarse el evento) | CORRELACIÓN | RESULTADOS | FUENTE DE EVIDENCIA | ESTADO |
|---|---|---|---|---|---|---|---|
| FACIAL | `FACIAL` (libre) | `FACIAL_####` si aplica sufijo numérico | Todos los de `LogEventIngestReq` (system, caseId, eventType, status, eventTime, message obligatorios; outcome, severity, actor, correlation, reason, payload, etc. opcionales) | caseId obligatorio; correlation.requestId opcional indexado | status/outcome/severity normalizados; isError calculado | `LogEventService.java:365-542`, `LogEventIngestReq.java`, `EventTypeNormalizer.java` | **Recibible, no instrumentado** (validación de disponibilidad, no de negocio) |
| SESION | `SESION` (libre) | —"— | —"— | —"— | —"— | idem | **Recibible, no instrumentado** |
| EXPEDICION | `EXPEDICION` (libre) | —"— | —"— | —"— | —"— | idem | **Recibible, no instrumentado** |
| AUTORIZACION | `AUTORIZACION` (libre) | —"— | —"— | —"— | —"— | idem | **Recibible, no instrumentado** |
| DATOS_BIOGRAFICOS | `DATOS_BIOGRAFICOS` (libre) | —"— | —"— | —"— | —"— | idem | **Recibible, no instrumentado** |
| FIRMA | `FIRMA` (libre) | —"— | —"— | —"— | —"— | idem | **Recibible, no instrumentado** |
| HUELLAS | `HUELLAS` (libre) | —"— | —"— | —"— | —"— | idem | **Recibible, no instrumentado** |
| IMPRESION | `IMPRESION` (libre) | —"— | —"— | —"— | —"— | idem | **Recibible, no instrumentado** |
| QA | `QA` (libre) | —"— | —"— | —"— | —"— | idem | **Recibible, no instrumentado** |
| LAMINACION | `LAMINACION` (libre) | —"— | —"— | —"— | —"— | idem | **Recibible, no instrumentado** |
| ENTREGA | `ENTREGA` (libre) | —"— | —"— | —"— | —"— | idem | **Recibible, no instrumentado** |
| PASSPORT_EXP | `PASSPORT_EXP` (libre) | —"— | —"— | —"— | —"— | idem | **Recibible, no instrumentado** |
| Dashboard de pasaportes | — | — | Lee `log_events` con `system="PASSPORT_PA"` fijo | timeline por (tenant, system, caseId) | Totales por status (EMITIDO/RECHAZADO/CANCELADO/EN_TRAMITE), por oficina y por tipo | `PassportDashboardController.java:32`, `PassportOverviewService.java` | **No aplica a PASSPORT_GY sin cambio de código** |

---

## 2. Trazabilidad de expedientes

### 2.1 Campos de correlación (esquema activo `model/log/LogEvent.java`)

| Campo | Tipo | Obligatorio | Índice |
|---|---|---|---|
| `caseId` | String | Sí (`@NotBlank` en ingesta) | Sí: `{tenant_id, system, caseId, eventTime}` |
| `correlation.requestId` | String | No | Sí: `{tenant_id, system, requestId, eventTime}` |
| `correlation.traceId`, `spanId` | String | No | **Sin índice dedicado** |
| `eventTime` | Instant | Sí | En todos los compuestos |
| `sessionId` | — | **No existe en el esquema activo** | — |

### 2.2 ¿Qué es `caseId`?

Un **String libre, obligatorio, sin validación**, provisto por el sistema emisor. Identifica lo que el emisor decida — en la práctica un expediente/trámite *según lo declare PASSPORT_GY*. No es identificador de sesión (el esquema activo no tiene sesión; `remoteConnection.sessionId` es un campo opcional de telemetría de conexión) ni de evento (muchos eventos comparten un caseId). **El backend no verifica** que eventos con el mismo caseId compartan actor, persona o pasaporte; la integridad correlacional es enteramente disciplina del emisor.

### 2.3 ¿Qué reconstrucción está garantizada?

**Sí garantizada, con evidencia:**
- Línea de tiempo cronológica de un expediente **dentro de un solo (tenant, system)**: `GET /api/logs/timeline?system&caseId` (`LogEventService.java:311-314`), con soporte de índice. Emite `correlation.requestId` por ítem.
- *User journey* por caso, con estados `COMPLETADO / EN_PROGRESO / COMPLETADO_CON_ERRORES` (`ExecutiveAnalyticsService.java:180-186`) — **exige `system`**: es single-system.
- Búsqueda inversa: si no se da caseId, `PassportTimelineService.resolveCaseId:61-108` hace `findOne` sobre `payload.passportNumber OR payload.personId OR correlation.requestId` y adopta el caseId del primer documento.

**No garantizada:**
1. **Sin trazabilidad transversal entre módulos.** Toda consulta por caso exige `system`. Dos módulos que emitan el mismo caseId con distinto `system` jamás se unen. La reconstrucción de un expediente "a través de diferentes módulos" solo es posible si PASSPORT_GY reutiliza deliberadamente un único caseId y system en todo el ciclo; el backend no la demuestra ni la valida.
2. `resolveCaseId` es arbitrario (primer match por OR de tres identidades distintas; puede confundir expedientes).
3. `traceId/spanId` no indexados y sin ruta de consulta: sin reconstrucción de traza distribuida.
4. Funneles: `FunnelAnalyticsService` cuenta caseIds únicos por paso asumiendo continuidad de caseId; eventos sin caseId quedan excluidos (`exists(true).ne("")`); la continuidad no se verifica.
5. Hallazgo de bug: `PassportEventService.java:76-77` filtra por `location.locationId` y `actor.actorId`, rutas que **no existen** en LogEvent (`location.id`, `actor.id`) — los filtros por oficina/usuario del buscador de pasaportes no devuelven nada. Corregir si Codex afirma que ese filtrado funciona.

**Veredicto:** trazabilidad = *"confiar en el caseId del emisor, acotado a un sistema y un tenant"*. Timeline por expediente dentro de PASSPORT_GY: real y demostrable. Trazabilidad integral del expediente entre módulos/sistemas: **no afirmable**.

---

## 3. Métricas — definiciones verificadas

Todos los cálculos leen `log_events` filtrando `tenant_id + system + eventTime` (índice `idx_tenant_system_time_v2`, timeout de agregación 3000 ms en la mayoría de servicios).

### 3.1 Qué es "error" en este backend (punto crítico)

No hay un discriminador único; coexisten **tres criterios no equivalentes**:

| Métrica | Criterio de error | Incluye RECHAZOS de negocio |
|---|---|---|
| Resumen ejecutivo: totales, tasa de error, lastIncident (`ExecutiveAnalyticsService.buildErrorCriteria:539-546`) | OR amplio: `isError` o status∈{ERROR,FAIL,FAILURE,KO,CRITICAL} o outcome∈{FAILURE,ERROR,FAILED} o severity∈{ERROR,CRITICAL,FATAL} | No (sin REJECTED) |
| Ranking de fricción — endpoint (`countTotalErrors:754`, agregación `:784`) | **Estricto: `isError==true` solamente** | No |
| Ranking global en resumen ejecutivo (`aggregateGlobalTopFrictionalEvents:890-900`) | OR amplio **con REJECTED** | Sí |
| Salud de sistemas (`SystemHealthService:68`) | Estricto: `isError==true` | No |
| Dashboard stats/series (`DashboardLogService.isErrorCriteria:587-594`) | OR amplio **con REJECTED** | Sí |
| Explicador de errores (elegibilidad) | `isError` o status∈{REJECTED,FAILED,ERROR} o outcome∈{ERROR,FAILED} o HTTP≥400 | Sí |
| Journey (mapStatus) | status∈{REJECTED,FAILED,ERROR} o outcome∈{ERROR,FAILED} o isError | Sí |

**Conclusión: "error" mezcla falla técnica y rechazo de negocio** en casi todas las vistas. Advertencias: (a) solo el token inglés literal `REJECTED` es reconocido; un `status=RECHAZADO` en español **no** entra por la rama status, solo si el productor también fija `isError`/severity/outcome; (b) ninguna analítica distingue técnico vs. negocio — salvo el dashboard de pasaportes que separa `RECHAZADO` como categoría de negocio propia.

### 3.2 Definiciones verificadas, métrica por métrica

- **Total de eventos**: conteo de documentos `log_events` en ventana con filtros tenant+system(s). Resumen ejecutivo: últimos 8 días por defecto (etiqueta dice "7 días"). Dashboard: últimas 24 h por defecto; rango `ALL` se **silencia a 24 h** (`DashboardLogService:449-460`).
- **Eventos exitosos** (dashboard): `status∈{SUCCESS,OK,COMPLETED}` u `outcome∈{SUCCESS,OK}`. Los buckets de éxito/error/warning/info son ORs independientes: **un mismo evento puede contar en varios**.
- **Eventos con error**: según criterio de la tabla 3.1, variable según el servicio.
- **Tasa de error**: `errors×100/total`, redondeada a 2 decimales; global = misma fórmula sobre los totales agregados.
- **Estado de salud** — **dos definiciones incompatibles coexisten**:
  - `ExecutiveAnalyticsService:1014-1028`: por sistema INACTIVE (0 eventos) / CRITICAL (tasa >10%) / WARNING (>0) / STABLE; global CRITICAL si algún sistema CRITICAL o tasa global >5.
  - `SystemHealthService:92-97`: INACTIVE / STABLE (0%) / WARNING (<5%) / CRITICAL (≥5%) sobre 24 h con criterio estricto `isError`. *Las dos vistas pueden discrepar para el mismo sistema.*
- **Ranking de mayor fricción**: endpoint — eventos con `isError==true` agrupados por `ifNull(eventCode,eventType)`, ordenados por **casos afectados** DESC (límite 1-50, día local corriente); el porcentaje usa casos afectados, no ocurrencias. Resumen ejecutivo — mismos grupos pero criterio híbrido con REJECTED, ordenados por **ocurrencias** DESC, top 5. **El "trend" está hardcodeado a `STABLE`/0.0** (`:860`) — es un stub.
- **Casos afectados**: `size(addToSet(caseId))` por grupo (incluye null si caseId falta); "casos activos" = suma de únicos por sistema.
- **Usuarios afectados**: no hay métrica de usuarios afectados por errores; `actor` solo se usa como filtro/dimensión.
- **Inconsistencias/anomalías**: `DataDiagnosticsService` (cobertura de actor.id, conflictos de identidad, lag de ingesta estimado desde ObjectId — marcado `estimated_from_objectid`) es un diagnóstico de **calidad de datos, no de errores de eventos**. `AttendanceAnomalyService` detecta ORPHAN_CLOSURE/INCOMPLETE_SHIFT/GHOST_USER pero para eventos de asistencia (`INICIAR_ASISTENCIA`/`FINALIZAR_ASISTENCIA`), orientado a TRUSTVALUE, no a pasaportes.
- **Explicación de errores**: `POST /api/analytics/explain-error/{logId}` — LLM con *fallback* heurístico ("confidence MEDIA", source `HEURISTIC`); el texto heurístico enmarca todo como problema técnico, aunque el evento sea un rechazo de negocio.

**Definiciones de métricas para el informe ejecutivo (redacción verificada):**

- Tasa de error (resumen ejecutivo) = (eventos que cumplen el OR amplio de la tabla 3.1, sin REJECTED) × 100 / total de eventos del sistema en la ventana de 8 días.
- Salud por sistema (resumen ejecutivo) = INACTIVE si 0 eventos; CRITICAL si tasa >10%; WARNING si >0; en otro caso STABLE.
- Evento friccional (endpoint) = evento con `isError==true`, agrupado por `eventCode` (o `eventType`), rankeado por casos afectados únicos.

---

## 4. Alertas y auditoría

### 4.1 Alertas para PASSPORT_GY — condiciones y canales

**Sí, el sistema emite alertas para cualquier sistema con eventos, incluido PASSPORT_GY, sin configuración previa.** Mecanismo real (`service/ai/AlertSchedulerService.java`, cada 60 min):

1. Detecta sistemas con eventos en `log_events` de las últimas 2 h por tenant/organización.
2. Resume la última hora/24 h y emite solo si el estado es WARN o CRIT, con umbrales **hardcodeados** (`SummaryInsightsService:36-48`; `AlertThresholdConfig` permite `alert.thresholds.<SISTEMA>.warn/crit` por propiedades, **hoy sin configurar** — defaults para todos):
   - Tasa de error: ≥5 % WARN, ≥15 % CRIT (con mínimo de 200 eventos para la tasa).
   - Errores absolutos: ≥50 WARN, ≥200 CRIT.
   - Pico de volumen: ≥200 eventos y ×2 (WARN) / ×3 (CRIT) del promedio.
   - Dominancia: 80 % del tráfico en un sistema.
3. Dedup por (tenant, system, día): primer envío del día `DAILY`; reenvío solo si la tasa sube ≥15 puntos (`ESCALATION`).

**Canales (estado de implementación):**

| Canal | Estado | Evidencia |
|---|---|---|
| Email HTML + PDF adjunto a ORG_OWNER/ORG_ADMIN | **Real** (perfil `mail` activo en `application.properties`; `JavaMailEmailSender` Gmail SMTP; throttling máx. 1 alerta/tenant/30 min) | `service/email/*` |
| Push FCM (WARN y CRIT) | **Real** (Firebase Admin SDK con `firebase-service-account.json` en resources; va a todos los tokens del tenant) | `service/notifications/FcmService` |
| WebSocket `/topic/alerts/{tenantId}` | **Real pero solo en CRIT**, y solo si el PDF se generó | `AlertSchedulerService` |
| Registro persistente `ai_alerts` con fingerprint SHA-1 (dedup) | Real | `SummaryInsightsService.persistAlertsIfNeeded` |
| **Motor basado en `AlertRule`/`AlertChannel`** | **Código muerto**: modelos sin repositorio, servicio, controlador ni seed; nadie los lee | `model/alerting/*` |
| **`alert_incidents`** | **Nunca se crean incidentes en código** (`new AlertIncident` no existe); la colección solo tendría filas insertadas a mano | — |

**Seguimiento:** los endpoints de *incidents* (`GET /api/incidents`, `PUT /{id}/resolve`) operan sobre la colección vacía. El seguimiento real de alertas vive en `/api/ai/alerts`: listar, `POST /{id}/ack`, `POST /{id}/resolve`, contexto, explicación y borrador de ticket. Sin filtro por sistema en la lista.

### 4.2 Auditoría y consulta/exportación de eventos

- **Modelo `Audit` (`audits`): código muerto** — nadie lo escribe ni lo lee.
- **Certificado de auditoría real**: `POST /api/analytics/reports/audit-certificate` genera un PDF (OpenPDF) por usuario, consultando `log_events` (tenant + system regex + rango + `actor.username`), con folio `AUD-<año>-<hex>`, tabla de evidencias y sello `sha256(folio|username|total|secret)`. Default system TRUSTVALUE. **Sin tope de filas** — todo el resultado va al PDF.
- **Consulta de eventos:** `GET /api/logs/events` (system obligatorio; filtros caseId/eventType/eventCode/status/outcome/severity/actorId/locationId/requestId/text/fechas; página default 10, **sin tope máximo declarado**), `GET /api/logs/events/all` (default size 1000, sin tope), `GET /api/logs/timeline` (tope 500).
- **Exportación CSV/Excel: no existe.** El permiso `LOG_EXPORT` existe pero ningún endpoint lo implementa. Los únicos PDF son el certificado de auditoría y los reportes internos de alerta/semana (email, no descarga).

**Limitaciones a declarar en el informe:** sin exportación tabular; paginación sin topes en `/events`; WebSocket sin autenticación en suscripciones; umbral de alertas por propiedades sin configurar; seguimiento de incidentes legado no operativo.

---

## 5. Evidencia de producción

- **No hay acceso a la base de producción** desde este entorno; no se realizó ninguna consulta ni modificación (conforme a la restricción).
- En el repositorio solo existe evidencia documental: `docs/M0_DIAGNOSTICO_COBERTURA.md` (diagnóstico M0 de cobertura) y `docs/PROPUESTA_INTELIGENCIA_ACTIVIDAD_USUARIOS.md`, ambos **no versionados**, y `docs/FUNNEL_TEMPLATES_SEED.md` + `insert-funnel-templates.ps1` (seeds). Ninguno contiene datos reales de PASSPORT_GY.
- **No verificable en este entorno:** volúmenes reales de eventos de PASSPORT_GY, distribución real de eventType/códigos, tasas de error reales, existencia real de API keys, reglas o alertas históricas, ni la congruencia real de caseId entre módulos. Todo lo anterior son **capacidades del código**, no observaciones de producción.
- **No se infiere ningún dato real** a partir de seeds, ejemplos o pruebas unitarias. Toda afirmación cuantitativa sobre PASSPORT_GY en producción queda **pendiente** hasta consulta autorizada a la BD.

---

## 6. Síntesis ejecutiva de hallazgos

1. **Recepción: real y robusta** (API key, rate limit, lotes de 500, WS en tiempo real). El backend acepta cualquier `system` sin alta previa — PASSPORT_GY puede integrarse solo enviando eventos.
2. **Procesamiento: agnóstico.** Los 12 procesos (FACIAL…PASSPORT_EXP) no existen como entidades; no hay validación, catálogo ni semántica. "Procesado" significa almacenado, normalizado superficialmente y clasificado por el propio emisor.
3. **Dashboard de pasaportes ciego a PASSPORT_GY**: sistema hardcodeado `PASSPORT_PA`.
4. **Trazabilidad intra-sistema real** (timeline/journey por caseId con índices); **trazabilidad transversal a módulos no demostrable** (toda consulta exige `system`; sin validación referencial).
5. **Métricas con criterios de error inconsistentes** entre servicios y dos definiciones de salud incompatibles; `trend` de fricción simulado (`STABLE` fijo); rango `ALL` del dashboard = 24 h.
6. **Alertas automáticas reales** (email/FM/WS, umbrales default, dedup diario) pero el motor declarativo de reglas/canales/incidentes es **código muerto**; el seguimiento operativo es el flujo `/api/ai/alerts` (ack/resolve).
7. **Auditoría real = certificado PDF por usuario sobre log_events**; no hay exportación tabular; el modelo `Audit` y el pipeline `passport_events`/`runtime` son código muerto.
8. **Bugs detectados** (corregir si los documentos de Codex los afirman como correctos): filtros `location.locationId`/`actor.actorId` inexistentes en `PassportEventService:76-77`; `topRequestIds` en `ErrorCorrelationService:242` coalescea desde `correlation.traceId` (posible error de copia).

## 7. Estado de las afirmaciones de Codex

Como los documentos base no están en el repositorio, el cotejo literal queda **pendiente**. Guía para el cotejo cuando se aporten:

- **Confirmables con el código:** existencia de ingesta por API key (`/api/logs/events`), normalización de eventType con sufijo numérico, timeline por caseId, certificado de auditoría PDF, alertas por scheduler horario con umbrales default.
- **Deben corregirse si se afirman:** que FACIAL/EXPEDICION/… están "instrumentados" o son "procesos conocidos" del backend; que el dashboard de pasaportes cubre PASSPORT_GY; que existe trazabilidad integral entre módulos; que `AlertRule`/incidents son el mecanismo de alerta operativo; que `model/runtime/LogEvent` o `passport_events` describen el pipeline activo; que el trend de fricción es calculado; que los errores distinguen falla técnica de resultado de negocio.
- **Pendientes de producción:** toda cifra real (volúmenes, tasas, ranking), existencia de datos de PASSPORT_GY, congruencia real de caseId entre módulos, configuración de umbrales por sistema.

---

*Documento generado a partir del código fuente en `src/main/java/backlogs/dinamico`. Referencias principales: `controller/logs/LogEventController.java`, `service/logs/LogEventService.java`, `model/log/LogEvent.java`, `service/analytics/ExecutiveAnalyticsService.java`, `service/dashboard/DashboardLogService.java`, `service/analytics/SystemHealthService.java`, `service/ai/AlertSchedulerService.java`, `service/analytics/AuditReportService.java`, `controller/dashboard/PassportDashboardController.java`, `service/dashboard/PassportEventService.java`.*
