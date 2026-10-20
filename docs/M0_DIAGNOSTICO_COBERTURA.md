# INFORME M0 — Diagnóstico de Cobertura de Datos
## Módulo: Inteligencia de Actividad y Usuarios

**Fase:** M0 (diagnóstico previo a M1) · **Referencia:** `PROPUESTA_INTELIGENCIA_ACTIVIDAD_USUARIOS.md` v2 · **Fecha:** 2026-10-16 · **Actualización:** 2026-10-20 (cierre técnico: corrección de bug, verificación de índices, calidad de datos y 15 pruebas automatizadas — §6.1)
**Alcance:** calidad, cobertura y confiabilidad de `log_events` por tenant y sistema.

> **Advertencia de acceso a datos (leer primero).** En este entorno de desarrollo **no hubo acceso a las bases de producción** (`logs_system` en AWS / `logs_quintanaroo`). Las únicas bases locales encontradas (`logs_dev`, `logs_empresa_pruebas`, `logs_system` local) contienen únicamente documentos de smoke/prueba (1 doc por colección relevante, en esquema legacy) o están vacías. Por tanto:
>
> - **Ningún porcentaje de cobertura sobre datos reales pudo medirse.** Todo lo que este informe afirma sobre el comportamiento de los datos está **inferido del código, la documentación del repo y un documento real aislado** citado en docs (ver §2.2).
> - Las consultas de medición fueron **implementadas y validadas sobre datos sintéticos de prueba** en una instancia MongoDB temporal local (ver §6), listas para ejecutarse contra producción o réplica.
> - **Los dictámenes de preparación por sistema son condicionales**, no veredictos sobre datos reales. La conclusión explícita sobre TrustValue está en §8.

---

## 1. Resumen ejecutivo

1. El modelo activo `model.log.LogEvent` confirma los campos que la propuesta v2 asume (`actor.{id,type,username,fullName}`, `remoteConnection.{sessionId,authResult,…}`, `eventTime`). **No existe `createdAt`/`ingestedAt`** en el esquema universal: el retraso de ingesta solo es estimable por el timestamp embebido en `_id` (ObjectId), aproximación válida y ya validada (§5.2).
2. **No existe índice sobre `actor.username` ni sobre `remoteConnection.sessionId`** (verificado en `LogEvent.java:26-68`): el índice de actor es `idx_tenant_system_actor_time_v2` sobre `actor.id`. Además `auto-index-creation: false` (`application.yml:15`) y el inicializador de índices (`MongoTenantConfig`) **solo crea 3 índices globales**, no los `idx_tenant_*_v2` que los servicios usan como hint → los índices compuestos por tenant deben existir por creación manual en cada despliegue; **su existencia en producción no está verificada**.
3. Eventos semánticos de autenticación: **no hay eventTypes de registro ni logout en código**; solo `AUTH_LOGIN` aparece en semillas/ejemplos y un documento real. Los de asistencia (`INICIAR_ASISTENCIA`, `FINALIZAR_ASISTENCIA`, `REGISTRAR_ASISTENCIA`) existen hardcodeados en `AttendanceAnomalyService`. La heurística "eventTypes que actúan como login" es medible y está implementada.
4. La colección **coexiste con esquemas legacy** (modelo `runtime.LogEvent` deprecated, colección `logs` antigua, y al menos un documento real en esquema prototipo `event_at/service_code` en `logs_empresa_pruebas`). Los documentos **sin `eventTime` son invisibles para toda consulta acotada por tiempo** (validado en §6) — riesgo de subconteo sistémico si hay emisores que no mandan `eventTime`.
5. La identidad humana se apoya en strings sin FK: `actor.id` puede ser **CURP, hostname de agente (p. ej. `WIN-MHRBTECSCC7-8`) o UUID** según emisor (docs). Esto confirma el riesgo central de la propuesta (contar 3–4 veces al mismo humano) y la necesidad del modelo de identidad de M1, pero **la cardinalidad real ids-por-persona no pudo medirse sin datos**.
6. Se implementó `DataDiagnosticsService` + endpoint de solo lectura `GET /api/intelligence/diagnostics/coverage` siguiendo las convenciones del proyecto (MongoTemplate + hint + `maxTime(3000)` + `allowDiskUse`, `ApiResponse`, `PERM_LOG_READ`, aislamiento por `tenant_id`). **Estado (2026-10-20): implementación completa y verificada** — controlador, servicio, 9 DTOs, autorización y compilación confirmadas. En el cierre se corrigió un bug real que impedía cualquier ejecución (`List.of(..., null)` lanzaba NPE al construir los pipelines de presencia y de conflictos) y se añadieron: verificación de existencia del índice hint antes de agregar (rechazo seguro 503 con explicación si falta), conteo de documentos sin `eventTime`, exclusión de eventos futuros del lag (con conteo aparte), detección heurística de posibles duplicados y 15 pruebas automatizadas ejecutadas (§6.1). El endpoint **rechaza con 503** si falta `idx_tenant_system_time_v2` o no se puede verificar, sin ejecutar agregaciones costosas.
7. **Bug crítico encontrado al ejecutar las pruebas por primera vez:** las agregaciones de identidad nunca llegaron a correr — el NPE de `List.of` se producía al construir el pipeline, antes de `aggregateQuietly`. Quedó corregido (`Arrays.asList` para listas con null) y cubierto por las pruebas; sin este cierre el diagnóstico habría fallado siempre en su primera faceta.

**Veredicto global M0 (actualizado 2026-10-20):** el diagnóstico como herramienta está **implementado al completo, corregido (bug NPE), endurecido (autorrechazo seguro si falta el índice) y validado con 15 pruebas automatizadas en verde**; el **dictamen de datos reales queda PENDIENTE de ejecución contra producción/réplica**, que es el criterio de salida de M0 según la propuesta (§12). Recomendación: ejecutar `GET /api/intelligence/diagnostics/coverage?system=<S>` por cada sistema en producción siguiendo `M0_PROCEDIMIENTO_EJECUCION_CONTROLADA.md` (costo acotado, <5 min/sistema según aceptación §13) antes de definir M1. **La herramienta está lista para despliegue controlado.**

---

## 2. Qué se verificó con datos reales y qué se infirió del código

### 2.1 Verificado con datos reales (acceso directo este entorno)

| # | Verificación | Resultado |
|---|---|---|
| R1 | Bases Mongo locales existentes | `logs_dev`, `logs_empresa_pruebas`, `logs_system` (y `config` del motor). No existe `logs_quintanaroo` local. |
| R2 | Volumen local de `log_events` | `logs_system`: 0 docs. `logs_empresa_pruebas`: 1 doc. `logs_dev`: 0 (solo `smoke`). |
| R3 | Documento real aislado en esquema prototipo | `logs_empresa_pruebas.log_events`: doc con `event_at`, `ingested_at`, `service_code: VENTAS`, `trace_id` — **esquema distinto al universal**, confirma heterogeneidad histórica. |
| R4 | Índices presentes en colecciones locales | Colecciones vacías; los índices de anotaciones `@CompoundIndex` **no se crean automáticamente** (`auto-index-creation: false`), solo 3 índices globales por `MongoTenantConfig`. Presencia de `idx_tenant_*_v2` en producción: no verificable desde aquí. |
| R5 | Pipelines de medición | Validados contra dataset sintético de 18 docs con resultados exactos (§6). |
| R6 | Carpeta biométrica `data/fp/<tenantId>/<CURP>/` | Existen huellas reales indexadas por CURP ( TrustValue ), confirmando CURP como identificador de persona real en ese sistema. |

### 2.2 Verificado vía documentación del repo (evidencia indirecta, no re-verificada hoy)

| # | Hallazgo | Fuente |
|---|---|---|
| D1 | Tenant real `696a76bddc3d6cd1487cdd35` / `TRUSTVALUE` con evento real `AUTH_LOGIN` (`status REJECTED`, `outcome FAILURE`, `eventTime 2026-07-14`) | `PIPELINE_AGREGACION_FIX.md` |
| D2 | Agentes Windows reales escriben `actor.id = hostname + último octeto IP` (p. ej. `WIN-MHRBTECSCC7-8`), `actor.fullName = hostname` | `DASHBOARD_LOGS.md` §5, §8 |
| D3 | Eventos reales confirmados en BD para TRUSTVALUE: `INICIO_SESION`, `SELECCION_SUCURSAL`, `ENVIAR_EVIDENCIAS`, `FINALIZAR_ASISTENCIA` (semilla marcada "FLUJO REAL") | `RESUMEN_REFACTORIZACION.md`, `FUNNEL_TEMPLATES_SEED.md` |
| D4 | `caseId` vacío/`null`/`"-"` observados en datos reales; patrón válido `^[A-Z]+[-_][A-Za-z0-9]` | `DEVICE_MAP_FIX.md`, `DEVICE_MAP_INTEGRITY_FIX.md` |
| D5 | Bug real jul-2026: agregaciones filtraban `tenantId` en vez de `tenant_id` y fallaban silenciosamente en producción | `PIPELINE_AGREGACION_FIX.md` |
| D6 | Retención: scheduler diario 03:00 America/Mexico_City, borrado por lotes de 1000, default 90 días por org, sin TTL index | `DASHBOARD_LOGS.md` §11 |

### 2.3 Inferido exclusivamente del análisis de código

- Cobertura esperada de campos por emisor (no medida): todo lo relativo a `%` de `actor.id`, `actor.username`, `actor.type`, `sessionId`, `authResult`.
- Cardinalidad de identificadores y conflictos usuario↔ids.
- Presencia de índices `idx_tenant_*_v2` en producción.
- Volumen por sistema y distribución temporal real.
- `remoteConnection.authResult`: valores reales no mostrados en ningún doc.

---

## 3. Estado técnico verificado (modelo, índices, servicios)

### 3.1 Modelo activo (`model/log/LogEvent.java`)

Confirmado campo a campo contra la propuesta §1.1. Matiz: la tabla de la propuesta lista `eventTime / createdAt`; **el modelo activo no tiene `createdAt`** (el campo `createdAt`/`ingestedAt` existe solo en el modelo legacy `runtime.LogEvent` y en el doc prototipo de `logs_empresa_pruebas`). Consecuencia: `authResult` y sesión viven en `remoteConnection` tal como anticipa la propuesta, y **la ingesta no deja timestamp propio** → lag estimable solo por `_id` (§5.2).

### 3.2 Índices

| Índice | Estado |
|---|---|
| `idx_tenant_system_time_v2` (hint principal) | Definido en anotaciones; **creado solo si existe manualmente en la BD**. Usado por DashboardLogService, ExecutiveAnalyticsService, AttendanceAnomalyService, SystemHealthService. |
| `idx_tenant_system_actor_time_v2` (`actor.id`) | Igual; único índice de actor. |
| `idx_tenant_system_eventType_time_v2` | Usado por FunnelAnalyticsService. |
| `idx_system_time_v1`, `idx_system_time_status_outcome_severity_v1`, `idx_time_status_v1` | **Sí creados al arranque** por `MongoTenantConfig`. |
| Sobre `actor.username` | **No existe.** quickAuditUsers, GHOST_USER, certificados de auditoría agrupan/filtran por `actor.username` dentro del rango → scan acotado. |
| Sobre `remoteConnection.sessionId` | **No existe.** |
| `geo_2dsphere` | Definido; requiere documentos con `geo.type="Point"`. |

**Riesgo:** si en producción no existen los `idx_tenant_*_v2`, el diagnóstico (y los dashboards) degradan a COLLSCAN por tenant. **Mitigación implementada (2026-10-20):** `DataDiagnosticsService` verifica en metadata (solo lectura, `indexOps.getIndexInfo()`, sin escaneo de documentos) que existe `idx_tenant_system_time_v2` **antes** de agregar; si falta o la verificación falla, rechaza con HTTP 503 (`required_index_missing` / `index_verification_failed`) explicando el motivo y sin ejecutar ninguna agregación. **Acción M0 previa recomendada:** `db.log_events.getIndexes()` en producción antes de ejecutar el reporte (procedimiento §2 de `M0_PROCEDIMIENTO_EJECUCION_CONTROLADA.md`).

### 3.3 Servicios relevantes existentes

- `AttendanceAnomalyService` — patrón a generalizar (TRUSTVALUE hardcoded; ORPHAN_CLOSURE, INCOMPLETE_SHIFT >12h, GHOST_USER >48h; lee `actor.role` y `requestInfo.userAgent/clientIp` **no modelados** → dependencia frágil confirmada).
- `DashboardLogService` (optimizado: `$facet`, hint, maxTime 3s, caché) — patrón de ejecución adoptado para el diagnóstico.
- `ExecutiveAnalyticsService`, `FunnelAnalyticsService` (catálogo `funnel_templates`), `AuditReportService` (certificados por `actor.username`), `LogDashboardService`/`CatalogService` (**sin hints** — riesgo COLLSCAN).
- `quickAuditUsers`: regex case-insensitive sobre `actor.fullName/username`, `userKey = username → email → fullName` — evidencia del problema de identidad dispersa.
- Normalización: `EventTypeNormalizer` solo parte `CATEGORIA_NUMERO` (p. ej. `APP_EVENT_1034`); **no hay catálogo semántico de eventos**; colección `event_types` existe pero vacía (sin seed).

---

## 4. Matriz de capacidades por sistema (estado M0)

Estado de preparación para analítica de usuarios. Leyenda: ✅ disponible según código+docs · ⚠️ parcial/degradado (razón) · ❌ no disponible · **?** requiere medición en producción (no verificable aquí).

| Capacidad | TRUSTVALUE | CITA_GUYANA / PASSPORT_GY | CITA_QUINTANAROO | TICKETS | Servidores/servicios |
|---|---|---|---|---|---|
| Eventos con actor humano | ✅ (agentes Windows/móviles) | ? (ciudadanos con caseId; propuesta sugiere EXTERNAL_PERSON) | ? (mismo patrón que Guyana) | ? (técnicos) | ❌ (SERVICE/DEVICE) |
| `actor.id` estable | ⚠️ **hostil a DAU**: hostname-IP en agentes; CURP en personas; UUID-? en móviles (medir) | ? | ? | ? | ✅ (SERVICE id) |
| `actor.username` | ⚠️ cobertura desconocida; sin índice | ? | ? | ? | n/a |
| `actor.type` | ⚠️ USER/SERVICE/DEVICE existe; cobertura real desconocida | ? | ? | ? | ✅ |
| Sesiones EXPLICIT (`sessionId`) | ? | ? | ? | ? | ? (medir; es el candidato natural) |
| Sesiones INFERIDAS (par INICIAR/FINALIZAR o gap) | ✅ por código (asistencia) | ⚠️ por flujo de cita (definir pares) | ⚠️ idem | ⚠️ idem | ❌ |
| Eventos login | ⚠️ `AUTH_LOGIN` observado (real) + `INICIO_SESION` (semilla); sin logout | ? | ? | ? | ? |
| Eventos registro / logout | ❌ no existen en código | ❌ | ❌ | ❌ | ❌ |
| `authResult` | ? (existe en modelo; doc real muestra status/outcome, no authResult) | ? | ? | ? | ? |
| Cuentas administrativas | ❌ UNKNOWN (no `technicalAccounts` definido; `actor.role` no modelado) | ❌ UNKNOWN | ❌ UNKNOWN | ❌ UNKNOWN | ✅ por `actor.type` |
| Directorio de usuarios | ❌ (no hay integración; solo observación en logs) | ❌ | ❌ | ❌ | n/a |
| Anomalías | ✅ ENABLED (AttendanceAnomalyService) — **no se habilitaron nuevas reglas** | ⬜ por catálogo | ⬜ | ⬜ | ⬜ |

**Nota:** "❌ no disponible" = no existe la señal en el código/datos revisados; no implica que el emisor no la mande en campos crudos (`payload`/`meta`) — eso es justo lo que el reporte de cobertura mide en producción.

---

## 5. Medición: qué puede calcularse hoy, degradado o no

Con el estado actual (sin directorio de identidades, sin nuevas colecciones):

| Métrica | Estado | Base |
|---|---|---|
| Volumen por sistema/día, retención, rango de fechas | ✅ disponible (eventTime; hint tenant+system+time) | log_events |
| DAU/WAU/MAU por `actor.id` | ⚠️ **DEGRADED**: contable pero sesgado (ids de hostname rotan con IP/DHCP; CURP ≠ UUID del móvil; mismo humano >1 id) | log_events |
| DAU/WAU/MAU canónicos | ❌ NOT_AVAILABLE (requiere `identity_directory`/`identity_daily_stats` — M1, fuera de M0) | — |
| Sesiones EXPLICIT | ⚠️ DEGRADED si coverage sessionId parcial (no medida) | remoteConnection.sessionId |
| Sesiones INFERIDAS (gap G=30) | ⚠️ DEGRADED (heurística; sin validación de G con datos reales) | eventTime |
| Logins / intentos fallidos (`AUTH_FAILURE_BURST`) | ⚠️ DEGRADED: heurística por eventType regex + `authResult`; sin evento de logout | eventType/authResult |
| Registros nuevos (`newRegistered` oficial) | ❌ NOT_AVAILABLE (sin directorio); observado como `newObserved` por firstSeen | — |
| Recurrentes / dormidos | ⚠️ DEGRADED (misma base inestable que DAU) | — |
| Retraso de ingesta | ⚠️ DEGRADED: estimado por `_id` − `eventTime` (no hay `ingestedAt`) | ObjectId |
| Duplicidades / conflictos de identidad | ✅ detectables (username→ids, co-ocurrencia) pero **no resolubles** sin M1 | log_events |
| `remoteConnection.authResult` burst en caliente | ? depende de cobertura real | — |

---

## 6. Validación ejecutada (datos sintéticos, instancia temporal local)

Se levantó `mongod` temporal contra `C:\data\db` (solo lectura sobre datos existentes; base desechable `m0_validation` con 18 docs de prueba: identidad variable, conflicto plantado `juan.perez`→2 ids, doc legacy sin `eventTime`, doc con `eventTime` futuro, segundo sistema y segundo tenant). Resultados de los pipelines de medición (forma idéntica a las implementadas en `DataDiagnosticsService`):

| Medida | Esperado (manual) | Obtenido | ✓ |
|---|---|---|---|
| Total ventana 30d | 14 | 14 | ✅ |
| `withActorId` | 10 | 10 | ✅ |
| `withUsername` | 7 | 7 | ✅ |
| `withFullName` | 9 | 9 | ✅ |
| `withType` | 10 | 10 | ✅ |
| Distinct ids / usernames | 5 / 2 | 5 / 2 | ✅ |
| Conflicto detectado (top) | juan.perez→2 ids | igual, sample correcto, nulls excluidos | ✅ |
| `withSession` / distinctSessions | 5 / 5 | 5 / 5 | ✅ |
| `withAuthResult` | 4 | 4 | ✅ |
| eventTypes auth-like top | AUTH_LOGIN=5 | 5 | ✅ |
| Lag medio (min) | 63 480/14 = 4 534.3 | 4 534.28 | ✅ (exacto) |
| Doc legacy sin `eventTime` | visible vía query específica | detectado (`service_code: VENTAS`) | ✅ |
| Rango histórico min/max | = límites de ventana | correcto | ✅ |

Hallazgos de la validación:
1. **Docs sin `eventTime` son invisibles** para todo pipeline con `$match eventTime≥desde` (el doc legacy no entró a ninguna medida temporal). El diagnóstico incluye conteo separado de docs sin `eventTime` (detección de emisores rotos) — **implementado y probado el 2026-10-20** (`dataQuality.eventsWithoutEventTimeInWindow`).
2. **Eventos con `eventTime` futuro contaminan el lag medio** (skew de -2 880 min en la muestra): el reporte acota el lag a valores no negativos y cuenta los futuros aparte (`dataQuality.futureEventsInWindow`, nota `future_events_detected`) — **implementado y probado el 2026-10-20**.
3. `$addToSet` con `$$REMOVE` excluye nulos correctamente; los nulos deben excluirse **antes** de medir conflictos (si no, "username con id null" parece conflicto).

### 6.1 Pruebas automatizadas ejecutadas (2026-10-20) — 15/15 en verde

Pruebas unitarias con `MongoTemplate` mockeado (`DataDiagnosticsServiceTest`, 12 tests) y autorización del endpoint (`IntelligenceDiagnosticsControllerSecurityTest`, 3 tests). Suite completa del proyecto: 35/36 verde; el único fallo es `BacklogsApplicationTests.contextLoads`, **fallo ambiental preexistente** (no hay MongoDB local; el contexto muere en el inicializador de índices `MongoTenantConfig`), no relacionado con M0.

| # | Verificación requerida | Prueba | Resultado |
|---|---|---|---|
| T1 | Datos vacíos / incompletos | `zeroEventsReturnsNotReadyWithoutExceptions` | ✅ NOT_READY, métricas NOT_AVAILABLE |
| T2 | Porcentajes y normalización | `normalizesSystemToUpperAndComputesCoveragePercentages` | ✅ UPPER, coberturas, conflictos, lag p95 |
| T3 | Readiness con caveats | `lowActorCoverageYieldsCaveats` | ✅ READY_WITH_CAVEATS + notas |
| T4 | Ausencia de índices | `missingRequiredIndexRejectsQuerySafelyWithExplanation` | ✅ 503 `required_index_missing`, 0 agregaciones ejecutadas |
| T5 | Verificación de índices falla | `indexVerificationFailureRejectsQuerySafely` | ✅ 503 `index_verification_failed` |
| T6 | Aislamiento tenant y sistema | `scopesEveryAggregationByTenantAndSystem` | ✅ todo `$match` fija `tenant_id`+`system`; límites globales sin acotar eventTime |
| T7 | Parámetros inválidos | `rejectsNullSystemAndClampsWindowDays` | ✅ 400 sin system; days acotado a [1,90] |
| T8 | Legacy sin `eventTime` | `temporalQualityCountsLegacyFutureEventsAndDuplicates` | ✅ conteo + nota explicativa |
| T9 | Eventos futuros | `temporalQualityCounts…` + `temporalPipelineBoundsLagToNonNegativeValues` | ✅ conteo + `$match lagMinutes≥0` en lagSummary y lagBuckets |
| T10 | Posibles duplicados | `temporalQualityCounts…` | ✅ grupos y docs extra por tupla eventType+actor.id+eventTime |
| T11 | Tiempo límite / tolerancia a fallos | `partialAggregationFailureStillReturnsDegradedReport` | ✅ reporte degradado, sin excepción al caller |
| T12 | Límites de consulta | `aggregationsAreBoundedByTimeHintAndDiskUse` | ✅ maxTime 3 s, hint, allowDiskUse, batch 1000 |
| T13 | Sin datos personales innecesarios | `reportDoesNotLeakUnnecessaryPersonalData` | ✅ sin fullName/sessionId crudos; conflictos top 10, muestras ≤3 |
| T14 | Validación de permisos | `userWithPermLogReadIsAuthorized` / `userWithoutPermLogReadIsDenied` | ✅ 200 con `PERM_LOG_READ`; AccessDenied sin él |
| T15 | Sin autenticar | `unauthenticatedUserIsDenied` | ✅ `AuthenticationCredentialsNotFoundException` |

**Resultados reales vs sintéticos:** T1–T13 son **sintéticos** (mocks deterministas, sin base de datos). Los resultados de producción siguen sin medirse — ninguna de estas pruebas toca datos reales. La única validación previa contra Mongo real fue la de §6 (instancia temporal, 18 docs), también sintética.

**Bug real encontrado al correr las pruebas por primera vez:** `List.of(..., null)` en la construcción de pipelines (presencia de campos y `$nin` de conflictos) lanzaba NPE antes de ejecutar cualquier agregación — el diagnóstico habría fallado siempre en producción. Corregido con `Arrays.asList`; ahora cubierto por T1–T3.

---

## 7. Riesgos y dependencias para comenzar M1

1. **Acceso a producción/réplica** — bloqueante para cerrar M0 (criterio de salida §12). Sin él, los umbrales (gap G=30, caliente 7 días, R=3) quedan sin calibrar.
2. **Índices en producción no verificados** — si faltan `idx_tenant_*_v2`, toda analítica por tenant es COLLSCAN. Verificar antes de cualquier backfill.
3. **Estabilidad de `actor.id`** — si en TRUSTVALUE prima hostname-IP, DAU/MAU serán métricas de "dispositivos-agente", no de personas; la identidad canónica de M1 se vuelve obligatoria, no opcional.
4. **Ausencia de eventos semánticos** (registro/logout) — `newRegistered` oficial quedará NOT_AVAILABLE hasta directorio o eventos nuevos del emisor; sesiones por par login/logout imposibles.
5. **Emisores a mejorar (recomendaciones, no bloqueantes):** enviar `actor.id` estable (UUID de persona), `actor.type` siempre, `remoteConnection.sessionId`+`sourceIp` en autenticación, eventos `USER_REGISTERED`/`AUTH_LOGIN`/`AUTH_LOGOUT` con `sessionId`, `eventTime` siempre (ISO-8601 UTC), dejar de reutilizar `caseId="-"`.
6. **Dependencia de campos no modelados** (`actor.role`, `requestInfo.userAgent/clientIp`) usada por anomalías actuales — frágil; normalizar en modelo o tolerar ausencia.
7. **Retención 90 días sin TTL index** — MAU calendario solo será completo si la retención ≥ periodo medido; considerar TTL o extensión para analítica.
8. **Heterogeneidad de esquemas** en la misma colección — el backfill de M1 debe filtrar por `schemaVersion`/presencia de campos universales.

## 8. Conclusión explícita — TrustValue y sistemas verificables

- **TrustValue es el único sistema con evidencia real en este repo** (doc real de `AUTH_LOGIN` jul-2026, eventos de asistencia reales en BD según docs, CURP como identificador de persona confirmado por `data/fp/`, agentes Windows con actor.id=hostname-IP). **Sus datos reales actuales no fueron accesibles desde este entorno**: no se pudo verificar cobertura de `sessionId`, `authResult`, ni estabilidad de `actor.id` en producción. Dictamen honesto: **READY_WITH_CAVEATS potencial, condicionado a ejecutar el reporte en producción**; el riesgo principal es que `actor.id` de hostname no identifique personas para DAU/WAU/MAU.
- **CITA_GUYANA / PASSPORT_GY, CITA_QUINTANAROO, TICKETS:** sin evidencia de datos reales en el repo. **No verificables** — todo dictamen queda en NOT_READY/"sin datos" hasta ejecutar el diagnóstico en sus tenants.
- **Servidores/switches/servicios:** emisores plausibles (`WINDOWS_SERVER_PRINCIPAL`, `WORKSTATIONS_SANTORO` en `config-produccion.env`) pero sin eventos mostrados; su analítica (métricas técnicas) es la más independiente del modelo de identidad humana.
- **Santoro platform (`users`/`user_roles`):** son usuarios del dashboard, no de sistemas cliente; sirven para autorización (`PERM_LOG_READ`), no para DAU de sistemas.
- **No se inventaron resultados:** donde no hubo acceso a datos reales, el estado es `NOT_AVAILABLE`/pendiente de medición, conforme al contrato `{value, state, note}` de la propuesta.
- **Estado de la herramienta (2026-10-20):** `DataDiagnosticsService` + endpoint completos, corregidos y con cobertura de pruebas automatizada (§6.1). El dictamen por sistema arriba **no cambia**: sigue condicionado a ejecutar el diagnóstico en producción/réplica con el procedimiento controlado. La herramienta está **lista para despliegue controlado**; los datos reales son el único pendiente.

## 9. Anexo — qué ejecutar para cerrar M0 en producción

Procedimiento detallado (parámetros, permisos, volumen, límites, criterios de aceptación, privacidad): **`M0_PROCEDIMIENTO_EJECUCION_CONTROLADA.md`**.

1. Verificar índices: `db.log_events.getIndexes()` (debe existir `idx_tenant_system_time_v2`; recomendados `idx_tenant_system_actor_time_v2` y `idx_tenant_system_eventType_time_v2`). El servicio además **autorrechaza con 503** si el hint falta, por lo que este paso es de preparación, no de riesgo.
2. Estimar volumen/costo: `db.log_events.aggregate([{$match:{tenant_id:ObjectId("…"),system:"TRUSTVALUE"}},{$count:"n"}])` con `maxTimeMS(3000)`; revisar `collStats` (tamaño, count aprox).
3. Ejecutar `GET /api/intelligence/diagnostics/coverage?system=TRUSTVALUE&days=30` (y por cada sistema), con usuario `PERM_LOG_READ`. HTTP 503 `required_index_missing` ⇒ atender antes de continuar (nunca ignorar).
4. Cruzar 5 consultas manuales de verificación con muestreo (aceptación §13: 100% match).
5. Revisar `dataQuality` (legacy sin eventTime, futuros, duplicados) y `readinessNotes`.
6. Con los números reales: calibrar G (gap de sesión), umbral caliente/materializado, R (retraso), y dictaminar `system_capabilities` por sistema.

---
*Restricciones respetadas: no se crearon `identity_directory`/`identity_aliases`/`identity_daily_stats`; no backfills; no datos modificados (la base `m0_validation` es desechable y local); sin despliegue; sin reglas de anomalías nuevas.*
