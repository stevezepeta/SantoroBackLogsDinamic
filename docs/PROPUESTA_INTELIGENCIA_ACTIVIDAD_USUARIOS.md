# PROPUESTA TÉCNICA — Módulo "Inteligencia de Actividad y Usuarios"

**Estado:** Propuesta de análisis y diseño — **versión 2 (revisada)**.
**Alcance:** Analítica de usuarios y actividad + motor de anomalías sobre el backend multi-tenant de logs.
**Fecha:** 2026-10-15

> Documento de propuesta. No contiene endpoints, colecciones ni migraciones implementadas.
> Su objetivo es validar la arquitectura antes de decidir qué se implementa primero en backend y después en frontend.
> El Anexo C mapea las 12 observaciones de revisión con las secciones que las resuelven.

---

## 1. Diagnóstico del backend actual

### 1.1 Fuente de verdad: `log_events`

El modelo activo es `backlogs.dinamico.model.log.LogEvent`
(`src/main/java/backlogs/dinamico/model/log/LogEvent.java`). Existe un segundo
`model.runtime.LogEvent` (deprecated, `since = 2026-07-13`) sobre la misma colección; **debe ignorarse**.

Campos relevantes confirmados en el modelo activo:

| Campo | Tipo | Notas |
|---|---|---|
| `tenant_id` | ObjectId | Aislamiento por tenant en todas las consultas |
| `system` | String | Normalizado a MAYÚSCULAS |
| `eventTime` / `createdAt` | Instant | `eventTime` es el tiempo del evento en origen |
| `eventType` / `eventCode` / `eventTypeRaw` | String | `EventTypeNormalizer` separa `CATEGORIA_NUMERO` |
| `status` / `outcome` / `severity` | String | outcome documentado: `SUCCESS\|FAILURE\|IN_PROGRESS\|CANCELED` |
| `actor` | `{id, type, username, fullName}` | `type` = USER \| SERVICE \| DEVICE. **No tiene `role`** |
| `location` | `{id, name, city, country}` | |
| `geo` | GeoJSON Point | Índice 2dsphere |
| `remoteConnection` | `{sourceIp, sourcePort, authMethod, authResult, user, sessionId, sessionDuration, clientType, sourceCountry, sourceCity, isLocalNetwork, riskScore, ...}` | Aquí viven la IP y la sesión |
| `correlation` | `{requestId, traceId, spanId}` | |
| `payload` / `meta` | Map | Esquema flexible |
| `caseId` | String | Requerido en ingest |

Índices existentes en `log_events` (anotaciones en `LogEvent.java:26-68`), todos con
prefijo `idx_tenant_system_*_time_v2`: `caseId`, `eventType`, `status`, `outcome`,
`severity`, `correlation.requestId`, **`actor.id`**, `location.id`, `eventCode`, más
`idx_tenant_time_isError_msgKey_v1`, `idx_system_time_v1`,
`idx_system_time_status_outcome_severity_v1` y `geo_2dsphere`.
**No existe índice sobre `actor.username` ni sobre `remoteConnection.sessionId`.**

### 1.2 Servicios y capacidades existentes reutilizables

- **Ingest y normalización**: `LogEventService.ingest()` (normaliza eventType, severity, `messageKey`, `isError`), `EventTypeNormalizer`, `SeverityNormalizer`.
- **Búsqueda de eventos**: `LogEventService.search/all/timeline`, filtros por system, eventType, actorId, status/outcome/severity.
- **Dashboard**: `LogDashboardService` (stats, series, http, geo, systemsHealth), `DashboardLogService` (el más optimizado: `$facet`, hint, `allowDiskUse`, `maxTime(3000)`, caché Caffeine `systemStats` TTL 10 s).
- **Analítica ejecutiva**: `ExecutiveAnalyticsService` (summary, timeline, userJourney por caseId, topFrictionalEvents; caché `TimedCache` 30 s).
- **Detección de anomalías actual (hardcoded)**: `AttendanceAnomalyService` — TRUSTVALUE con eventTypes fijos `INICIAR_ASISTENCIA`/`FINALIZAR_ASISTENCIA`/`REGISTRAR_ASISTENCIA`; detecta ORPHAN_CLOSURE, INCOMPLETE_SHIFT (>12 h), GHOST_USER (>48 h). **Es el patrón a generalizar, no a duplicar.**
- **Quick audit de usuarios**: `LogEventRepositoryImpl.quickAuditUsers()` — agrupa por `actor` con regex, extrae dispositivo/IP por "primer candidato no vacío" de ~6 rutas (`device`, `systemAndDevices.device`, `deviceInfo.model`, `requestInfo.userAgent`, `meta.device`, `payload.device` / `clientIp`, `requestInfo.clientIp`, `ip`, `meta.ip`, `remoteConnection.sourceIp`).
- **Registro de dispositivos**: `DeviceRegistryService.upsertFromLog()` → colección `devices_registry` (índice único `{tenantId, deviceId}`), eventos `SYSTEM_STARTUP/BOOT/SHUTDOWN`, status ONLINE/OFFLINE calculado por `lastSeen` < 10 min.
- **Resúmenes horarios**: `HourlySummaryService` (`$dateTrunc` por hora, top-5 por categoría) + `AlertSchedulerService` (cada 60 min: insights → `TrendAnomalyService` z-score → dedupe por `AlertSentRecord` → PDF/correo/WS/FCM).
- **Alertas**: infraestructura completa de alertas IA (`AiAlertRecord` en `ai_alerts`, fingerprint dedupe, estados ack/resolve) e `AlertIncident` en `alert_incidents` con su ciclo de vida — **reutilizable como plantilla del ciclo de vida de hallazgos** (ver decisión §8.5).
- **Funneles dinámicos**: `FunnelAnalyticsService` + `funnel_templates` (plantillas por sistema en Mongo). Demuestra que un motor configurable por tenant/sistema ya es viable en esta base de código.

### 1.3 Seguridad y multi-tenancy (a preservar)

- Tenant vía `TenantContext` (ThreadLocal), resuelto por `TenantResolutionFilter` (headers) o `ApiKeyTenantFilter` (ingest).
- JWT (`JwtAuthFilter`): claims `tenantId`, `roles` → `ROLE_*`, `perms` → `PERM_*`, `orgWide`, `systems`, filtros de visibilidad `lfOutcomes/lfStatuses/lfSeverities/lfEventTypes`.
- **Aislamiento obligatorio en toda consulta nueva**: `Criteria.where("tenant_id")` + `ScopeGuard.requireSystemAccess(user, system)` + `LogFilterCriteria.apply(criteria)`.
- Roles: `ORG_OWNER, ORG_ADMIN, SYSTEM_MANAGER, AUDITOR, SUPPORT_TI, SUPPORT, EXEC, VIEWER`.
- Permisos relevantes: `LOG_READ, LOG_EXPORT, LOG_REPORTS, ALERTS_MANAGE, VIEW_PII, SETTINGS_MANAGE`.
- Convenciones: respuesta envuelta en `ApiResponse<T>`, paginación `PageImpl` o `PageMeta(page,size,hasNext)` (limit+1 sin count), hint `idx_tenant_system_time_v2`, `maxTime(3000)`, `allowDiskUse`.

### 1.4 Otros mecanismos existentes

- **Schedulers**: `AlertSchedulerService` (60 min), `WeeklyReportSchedulerService` (dom 19:00), `LogRetentionScheduler` (diario 03:00 → `LogRetentionService`: borra `eventTime < now - retentionDays` en lotes de 1000; `Organization.settings.retentionDays`, default 90). Sin TTL index en `log_events`.
- **Caché**: Caffeine en memoria (`systemStats` 10 s, `TimedCache` 30 s, buckets rate-limit). Sin Redis.
- **Catálogo de sistemas**: `SystemApp` (`systems`: `{tenant_id, name, code, status}`). **No tiene campo de capacidades**; hoy se infieren escaneando `log_events` (distinct system, eventTypes presentes).

### 1.5 Inventario de colecciones existentes (base anti-duplicación)

Antes de proponer cualquier colección nueva, este es el inventario verificado (obs. 12):

`log_events`, `logs` (legacy `LogEntry`), `devices_registry`, `sessions` (legacy, sin escritor),
`users`, `user_roles`, `roles`, `organizations`, `password_reset_tokens`, `email_verifications`,
`qr_login_sessions`, `persons`, `fingerPrint`, `passport_events`, `api_keys`, `systems`,
`environments`, `offices`, `devices` (catálogo legacy), `event_types`, `error_codes`, `schemas`,
`flows_config`, `dashboards`, `funnel_templates`, `audits`, **`alert_rules`, `alert_incidents`,
`alert_channels`**, `webhooks`, `ai_metrics`, `ai_alerts`, `alert_sent_records`.

Decisiones de reutilización que de este inventario se derivan (detalle en §8.5):
- **No** se recrea una colección de sesiones nueva ignorando la legacy: `sessions` queda formalmente
  deprecada y el módulo no la usa (la sesión real vive en `remoteConnection.sessionId`); el nombre
  `session_summary` se mantiene para la materialización propuesta.
- **No** se asume que `ai_metrics` tiene datos: no se encontró su escritor en el código. Se propone
  `identity_period_stats` con writer propio en vez de reutilizar `ai_metrics` sin verificar.
- Las reglas/hallazgos de anomalías **revisan primero** extender `alert_rules`/`alert_incidents`
  existentes antes de crear colecciones paralelas (§8.5).

### 1.6 Problemas detectados relevantes al módulo

1. Relación usuario↔eventos débil: todo por strings (`actor.username/fullName`), sin FK a `users`,
   y sin mecanismo para saber si dos claves (actor.id, username) son la misma persona.
2. Resolver de dispositivo/IP **duplicado** en 2-3 servicios; no existe un resolver centralizado.
3. Caché inconsistente: `systemStats` invalida por WS; los `TimedCache` de analítica no.
4. `AttendanceAnomalyService` usa `actor.role` y `requestInfo.userAgent/clientIp`, campos **que no están modelados** en `Actor` ni en `LogEvent` (llegan solo si el emisor los manda, como campos extra del documento crudo). Dependencia frágil.
5. Colección legacy `sessions` **sin escritor activo**; la sesión real vive en `remoteConnection.sessionId/sessionDuration`.
6. No existe catálogo de capacidades por sistema ni de eventos semánticos (login/registro/logout).

---

## 2. Datos disponibles y faltantes

### 2.1 Disponibles hoy (verificados en código)

- **Identidad observada en logs**: `actor.{id, type, username, fullName}` + `remoteConnection.user`. Suficiente para métricas de actividad observada.
- **Sesión observada**: `remoteConnection.sessionId`, `sessionDuration`, `clientType`, `authMethod`, `authResult`.
- **Red/origen**: `remoteConnection.sourceIp/Port`, `sourceCountry/City`, `isLocalNetwork`.
- **Ubicación declarada**: `location.{id,name,city,country}` y `geo` (2dsphere).
- **Clasificación humana/técnica**: `actor.type` (USER/SERVICE/DEVICE) — base para excluir cuentas técnicas.
- **Usuarios registrados de la PLATAFORMA**: colección `users` (email, name, status, last_login_at) + `user_roles` (roles, `allowed_systems`, filtros). Son usuarios del dashboard Santoro, **no** de los sistemas cliente.
- **Dispositivos**: `devices_registry` (auto-registrado desde ingest).
- **Config por tenant**: `Organization.settings.timezone`, `retentionDays`; catálogos `systems`, `offices` (con geo), `environments`.
- **Tipos de evento sembrados** (funneles y asistencia): `INICIO_SESION`, `AUTH_LOGIN`, `INICIAR_ASISTENCIA`, `FINALIZAR_ASISTENCIA`, `REGISTRAR_ASISTENCIA`, `SYSTEM_STARTUP/BOOT/SHUTDOWN`, etc. Son strings libres; el catálogo `event_types` existe pero no tiene valores sembrados.

### 2.2 Faltantes (no asumir que existen)

- **Usuarios registrados de los sistemas cliente** (TrustValue, Pasaportes Guyana, etc.): NO hay integración con directorios externos ni API de usuarios de origen. La fuente disponible es la observación de actores en `log_events`.
- **Eventos semánticos de identidad**: no hay eventType de registro de usuario, logout ni cierre de sesión definidos en código. Deben definirse e integrarse en los emisores, o derivarse por heurísticas (documentadas como tales).
- **`actor.role`**: no modelado; los emisores no lo envían de forma confiable.
- **Oficina del actor**: solo `location` del evento (donde ocurrió), no necesariamente la oficina del usuario.
- **User-Agent**: no modelado; `requestInfo.userAgent` solo aparece en payloads crudos de algunos emisores.
- **Horarios autorizados / calendarios laborales**: no existen como datos. `Organization.settings.timezone` es el único dato de zona horaria confiable.
- **`actor.username` sin índice**; la estabilidad de `actor.id` depende de que los emisores lo manden consistente — **su cobertura real es desconocida y se medirá en la Fase M0 (§12)**.

### 2.3 Principio rector

> **No inventar resultados.** Cada métrica y hallazgo debe declarar su fuente y su estado de disponibilidad:
> `AVAILABLE` (se calcula con datos verificados) / `DEGRADED` (se calcula por heurística documentada) / `NOT_AVAILABLE` (la fuente no lo soporta).
> **En la API, `NOT_AVAILABLE` siempre se representa con `value: null` + `state` + `note` explicativo — nunca con 0 ni con otra cifra** (§9.1).

---

## 3. Resolución de identidad (diseño robusto)

Esta sección sustituye el mecanismo provisional de "userKey" usado hoy en
`LogEventRepositoryImpl.quickAuditUsers` y `AttendanceAnomalyService` (duplicado en ambos).

### 3.1 El problema

Un mismo usuario puede aparecer en distintos eventos con distintas claves:

- Evento A: `actor.id = "u-123"`, `actor.username = "juan.perez"`
- Evento B: `actor.username = "juan.perez"` (sin `actor.id`)
- Evento C: `actor.id = "u-123"` (sin username, o con username distinto por cambio de correo)
- Evento D: `remoteConnection.user = "juan.perez@empresa.com"` (autenticación de red)

Si cada clave crea una identidad, un solo humano se contará 3-4 veces en DAU/WAU/MAU.
Si se "deduplica" por heurísticas agresivas, usuarios distintos colapsan en uno.

### 3.2 Modelo: identidades canónicas + alias verificables

**`identity_directory`** — una fila por identidad canónica (§8.1):

```
identityKey        // estable, generado por el sistema (no es actor.id ni username)
aliases            // resumen denormalizado de las claves conocidas
```

**`identity_aliases`** — una fila por cada clave observada ligada a una identidad (§8.1):

```
{ tenant_id, system,
  identityKey,                    // identidad canónica a la que resuelve
  aliasType: ACTOR_ID|USERNAME|REMOTE_USER|EMAIL,
  aliasValue,                     // normalizado (trim/lower para USERNAME/EMAIL)
  firstSeenAt, lastSeenAt, eventCount,
  confidence: VERIFIED|HIGH|MEDIUM|LOW,
  verifiedBy: DIRECTORY|ADMIN|AUTO_CO_OCCURRENCE|AUTO_NORMALIZED,
  verifiedAt }
```

Clave única: `{tenant_id, system, aliasType, aliasValue}`.
**Toda equivalencia es explícita, auditable y tiene nivel de confianza** — no hay deduplicación
implícita por parecido de strings.

### 3.3 Algoritmo de resolución (por evento, en el pipeline §6)

1. Extraer claves candidatas del evento, en orden de fuerza:
   `actor.id` (ACTOR_ID) → `actor.username` (USERNAME) → `remoteConnection.user` (REMOTE_USER/EMAIL).
2. Si el evento trae **varias claves a la vez** (p. ej. `actor.id` + `actor.username`), se resuelve
   cada una por separado (paso 3) y luego se **enlazan**: se crean/actualizan ambos alias apuntando
   a la misma identidad con `confidence = HIGH (AUTO_CO_OCCURRENCE)`. La co-ocurrencia en el mismo
   evento es evidencia fuerte pero no definitiva (un dispositivo compartido podría mandar claves
   cruzadas; por eso no es VERIFIED).
3. Resolución de una clave:
   - ¿Existe alias exacto? → identidad canónica del alias.
   - ¿No existe? → se crea identidad nueva (`observedOnly = true`) + alias con
     `confidence = LOW (AUTO_NORMALIZED)` (solo se normalizó el string; aún no hay evidencia de
     equivalencia con otra clave).
4. Emparejamiento propuesto (no automático): si una clave nueva parece equivalente a una identidad
   existente por señal débil (mismo `fullName`, email local idéntico, etc.) **no se fusiona**; se
   registra en una cola de `merge_candidates` para verificación humana o de directorio.
5. Fusión (`merge`): solo con `confidence = VERIFIED` (directorio confirma ambas claves como la misma
   cuenta) o aprobación admin. La identidad absorbida queda con `mergedInto = identityKey` conservando
   su historial; las agregaciones pasadas no se reescriben (queda constancia en `mergeHistory`).

### 3.4 Niveles de confianza

| Nivel | Cuándo | Efecto |
|---|---|---|
| `VERIFIED` | Directorio del sistema confirma la equivalencia (sync futuro, §14) o admin la valida | Puede disparar merge; alias "de oro" |
| `HIGH` | Co-ocurrencia en el mismo evento (actor.id + username) | Une claves sin merge formal; suficiente para métricas |
| `MEDIUM` | Dos señales independientes de co-ocurrencia en ventana de tiempo razonable | Propone merge candidato |
| `LOW` | Solo normalización de string; equivalencia no comprobada | Identidad provisional aislada |

**Regla anti-duplicación**: una identidad canónica nace sin equivalencias; solo gana equivalencias
por evidencia registrada. La métrica de "usuarios únicos" siempre se calcula sobre identidades
canónicas, nunca sobre claves crudas.

### 3.5 Clasificación humana/técnica sin falsos positivos (obs. 2)

**No se excluye automáticamente a un usuario humano por patrones generales de username**
(`admin$`, `svc.*`, `system`, etc. pueden ser personas reales con cuentas privilegiadas).

Jerarquía de señales, de más fuerte a más débil:

1. **`actor.type`** (del propio evento): `SERVICE`/`DEVICE` → técnico. Es la señal primaria.
2. **Lista explícita del tenant**: patrones o cuentas exactas configuradas por el administrador en
   `system_capabilities.technicalAccounts` (allowlist propia del sistema, revisable).
3. **`actor.type` ausente** → se clasifica por la lista explícita; si no coincide, se trata como
   **humano** y la métrica lleva `state: DEGRADED` (señal ausente), nunca exclusión silenciosa.

Las cuentas administrativas/privilegiadas **no se excluyen de las métricas humanas**: se marcan
(`isPrivileged = true`, alimentado por directorio o por la lista explícita con flag `privileged`)
y se reportan como segmento propio (adopción de cuentas privilegiadas vs. operativas).
La exclusión (`status: EXCLUDED`) solo existe para cuentas técnicas verificadas, no para humanos.

---

## 4. Matriz de capacidades por sistema

### 4.1 Propuesta: catálogo declarativo de capacidades

Hoy las capacidades se infieren escaneando `log_events` (costoso y no declarativo). Se propone
ampliar el catálogo `systems` (o una colección derivada `system_capabilities`) con capacidades
declaradas por sistema y tenant, manteniendo la **verificación por datos** como fallback:

```
system_capabilities: {
  tenant_id, system,                          // clave única
  capabilities: {
    registeredUsers:  NONE | OBSERVED | DIRECTORY_SYNC,
    humanIdentities:  SUPPORTED | NOT_SUPPORTED,
    sessions:         EXPLICIT | INFERRED | NONE,
    adminAccounts:    KNOWN | UNKNOWN,
    devices:          SUPPORTED | NOT_SUPPORTED,
    technicalMetrics: SUPPORTED | NOT_SUPPORTED,
    anomalyDetection: ENABLED | DISABLED
  },
  identityHints: {                            // cómo leer la identidad de este sistema
    stableIdField: "actor.id",
    usernameField: "actor.username",
    humanTypes: ["USER"],                     // actor.type considerado humano
    technicalAccounts: [                      // allowlist EXPLÍCITA del tenant (no regex genéricas)
      { "pattern": "svc-trustvalue$", "kind": "SERVICE" },
      { "pattern": "jefe.turno", "kind": "USER", "privileged": true }
    ],
    registrationEventTypes: ["USER_REGISTERED","REGISTRO_USUARIO"],
    loginEventTypes:  ["AUTH_LOGIN","INICIO_SESION"],
    logoutEventTypes: ["AUTH_LOGOUT","CIERRE_SESION"],
    sessionIdPath: "remoteConnection.sessionId"
  },
  lastVerifiedAt                              // cuándo se validó contra datos reales (M0/M1)
}
```

- **Inicialización**: se rellena por el diagnóstico de datos de la Fase M0 (§12) y es editable por el
  administrador del tenant (`SETTINGS_MANAGE`).
- **Regla de oro**: ninguna regla exclusiva para TrustValue. TrustValue y PASSPORT_GY solo sirven
  para sembrar los dos primeros perfiles; cualquier sistema nuevo se configura con el mismo catálogo.

### 4.2 Matriz de referencia (escenarios de partida, a validar con datos reales en M0)

| Capacidad | TrustValue | CITA_GUYANA / PASSPORT_GY | CITA_QUINTANAROO | TICKETS | Servidores / switches / servicios |
|---|---|---|---|---|---|
| Usuarios registrados | OBSERVED (convenio: sincronizar directorio en fase posterior) | OBSERVED | OBSERVED | OBSERVED | N/A |
| Identidades humanas activas | Sí (asistencia con actor USER) | Sí (ciudadanos con caseId) | Sí | Sí (técnicos) | No (actores SERVICE/DEVICE) |
| Sesiones | INFERRED (INICIAR/FINALIZAR_ASISTENCIA) | INFERRED (flujo de cita) | INFERRED | INFERRED | EXPLICIT vía `remoteConnection.sessionId` |
| Cuentas administrativas | UNKNOWN (definir `technicalAccounts`) | UNKNOWN | UNKNOWN | UNKNOWN | KNOWN vía `actor.type` |
| Dispositivos | Sí (móviles del personal) | N/A | N/A | N/A | Sí (servidores, switches — `devices_registry`) |
| Métricas técnicas | No | No | No | No | Sí (arranque/paro, errores, latencias) |
| Detección de anomalías | ENABLED | ENABLED | ENABLED | ENABLED | ENABLED (anomalías técnicas) |

**Nota explícita**: la columna "ciudadanos" de CITA_GUYANA plantea una decisión de diseño:
¿son "usuarios" del sistema a efectos de adopción? Se propone tratarlos como identidades
observadas (`identityType = EXTERNAL_PERSON`), separadas de usuarios internos/operadores, para
que la métrica de "usuarios activos" no mezcle poblaciones distintas.

---

## 5. Definición exacta de métricas

### 5.0 Convenciones transversales (actualizadas)

- **Identidad canónica**: toda métrica por usuario se calcula sobre `identity_directory.identityKey`
  (§3), nunca sobre claves crudas de eventos.
- **Humano vs técnico**: sección §3.5 (jerarquía de señales; sin exclusión por patrones genéricos).
- **Fecha local**: agrupaciones por día/semana/mes en la zona de `Organization.settings.timezone`
  (default `America/Mexico_City`), siguiendo el patrón `$dateToString`/`$dateTrunc` con `tz`
  ya usado en `DashboardLogService`/`HourlySummaryService`.
- **Registrado vs observado**: solo los sistemas con `registeredUsers = DIRECTORY_SYNC` tienen
  "total oficial de registrados". Con `OBSERVED` se reporta "identidades observadas" con
  etiqueta explícita, no "registrados".
- **Estados de actividad vs estado administrativo** (obs. 6) — son conceptos distintos y se modelan
  en campos separados de `identity_directory` (§8.1):
  - `accountStatus` (ADMINISTRATIVO, externo al módulo): `ACTIVE | DISABLED | DELETED` — viene del
    directorio del sistema o de gestión del tenant. El módulo no lo infiere.
  - `activityStatus` (DERIVADO por el módulo): `ACTIVE | DORMANT | INACTIVE` — calculado desde
    `lastSeenAt` con umbrales configurables (§5.5).
  - `firstSeenAt` (primera OBSERVACIÓN en logs) y `registeredAt` (fecha OFICIAL de alta en el
    directorio) son fechas distintas: `registeredAt = null` cuando no hay directorio (`observedOnly`).
- **Contrato de disponibilidad** (obs. 7): toda métrica en API se devuelve como
  `{ value, state, note? }`; **`state = NOT_AVAILABLE` ⇒ `value = null`**; `state = DEGRADED` lleva
  `note` obligatorio. `0` solo es válido cuando la fuente existe y el conteo es realmente cero.

### 5.1 Total oficial de registrados

1. **Definición**: conteo de cuentas de usuario dadas de alta en el directorio oficial del sistema origen.
2. **Fuente**: directorio del sistema cliente (sincronización futura, §14) o `users` de la plataforma para el dashboard.
3. **Campos**: `identity_directory.registeredAt` (fecha oficial), `accountStatus`.
4. **Algoritmo**: `count(tenant, system, accountStatus ≠ DELETED)` sobre `identity_directory`. Nunca derivarse de logs.
5. **Limitaciones**: sin sincronización de directorio, estado `NOT_AVAILABLE` (no inventar).
6. **Índices**: `{tenant_id, system, accountStatus}`.
7. **Estrategia**: lectura directa O(1) sobre colección pequeña; se sincroniza por scheduler o webhook.

### 5.2 Nuevos registros

1. **Definición**: identidades cuyo alta oficial (`registeredAt`) o primera observación (`firstSeenAt`) ocurre en el periodo, reportados como dos métricas separadas: `newRegistered` (oficial) y `newObserved` (observado).
2. **Fuente**: `identity_directory.registeredAt` / `firstSeenAt`.
3. **Campos**: `registeredAt`, `firstSeenAt`, `observedOnly`.
4. **Algoritmo**: `count(registeredAt ∈ periodo)` y `count(firstSeenAt ∈ periodo)` contra `identity_directory`, **no** contra `log_events` en tiempo de consulta.
5. **Limitaciones**: `newObserved` con `OBSERVED` marca usuarios que ya existían pero nunca generaron logs; se etiqueta `DEGRADED` con note "observed_only". `newRegistered` es `NOT_AVAILABLE` sin directorio.
6. **Índices**: `{tenant_id, system, firstSeenAt}`, `{tenant_id, system, registeredAt}`.
7. **Estrategia**: el pipeline (§6) marca `firstSeenAt` al resolver identidades; `registeredAt` llega por sync de directorio.

### 5.3 DAU / WAU / MAU — calendario vs ventanas móviles (obs. 3)

Las siglas son ambiguas: "WAU" puede significar "semana ISO actual" o "últimos 7 días".
**La propuesta define ambas familias explícitamente y las reporta como métricas separadas:**

| Métrica | Definición | Tipo |
|---|---|---|
| `dau` | Identidades humanas únicas con ≥1 evento humano en el **día natural actual** (tz tenant) | Calendario |
| `wau_calendar` | Identidades únicas en la **semana ISO en curso** (lunes–domingo) | Calendario |
| `mau_calendar` | Identidades únicas en el **mes natural en curso** | Calendario |
| `mau_rolling_7` ("WAU móvil") | Identidades únicas en los **últimos 7 días completos** (hoy−7 → hoy−1) | Ventana móvil |
| `mau_rolling_30` ("MAU móvil") | Identidades únicas en los **últimos 30 días completos** | Ventana móvil |

- El dashboard de adopción usa la familia **móvil** para comparación entre días (comparables entre sí,
  sin el efecto de "lunes vs domingo" ni de "día 1 del mes vs día 30"), y la familia **calendario**
  para reportes ejecutivos alineados al periodo contable.
- Los nombres en API son inequívocos (`mau_rolling_30`, nunca solo `mau` sin sufijo).
- **Definición (aplicable a todas)**: identidades humanas únicas con ≥1 evento humano en el periodo,
  calculadas sobre identidades canónicas (§3) desde `identity_daily_stats` (§8.2).
- **Algoritmo**: móviles — `count(distinct identityKey)` sobre días del rango en `identity_daily_stats`;
  calendario — sobre los días del bucket calendario. En caliente (≤7 días, umbral configurable):
  `$group {_id: {$dateToString: {...tz}}, actors: {$addToSet: "$actor.id"}}` con hint, solo como fallback.
- **Limitaciones**: mide actividad **observada** (genera logs), no uso real de la app si hay funciones sin telemetría.
- **Índices**: `identity_daily_stats` — único `{tenant_id, system, day, identityKey}` y secundario
  `{tenant_id, system, day}` para barridos por día (§10). En `log_events` existe
  `idx_tenant_system_actor_time_v2` para el fallback en caliente.
- **Estrategia**: materialización diaria (§6); el endpoint solo lee `identity_period_stats`.

### 5.4 Usuarios recurrentes

1. **Definición**: identidades activas ≥ N días distintos en los últimos M días completos (ventana móvil; default N=3, M=7; configurable por tenant).
2. **Fuente**: `identity_daily_stats`.
3. **Campos**: `day, identityKey` (una fila por identidad-día activa).
4. **Algoritmo**: `aggregate({$match: day ∈ [hoy−M, hoy−1]}, {$group: identityKey, days: {$sum:1}}, {$match: days ≥ N})`.
5. **Limitaciones**: depende de la completitud de telemetría; N/M arbitrarios.
6. **Índices**: `{tenant_id, system, day, identityKey}` (cubre el match por rango de días + group).
7. **Estrategia**: pre-aggregar en la reconciliación nocturna hacia `identity_period_stats` con buckets móviles rolling 7/30 días.

### 5.5 Usuarios sin actividad (inactivos / dormidos)

1. **Definición**: identidades con `lastSeenAt` anterior a hoy − K días. Umbrales: `dormant_7_30`, `dormant_30_90`, `dormant_90_plus` (límites configurables por tenant).
2. **Fuente**: `identity_directory.lastSeenAt` (mantenido por el pipeline §6).
3. **Campos**: `lastSeenAt, activityStatus`.
4. **Algoritmo**: `count(lastSeenAt < hoy−K)`; `activityStatus` se deriva: `ACTIVE` (≤7 días sin actividad... umbral configurable), `DORMANT` (>7), `INACTIVE` (>90).
5. **Limitaciones**: solo sobre identidades ya observadas; no detecta usuarios que nunca se han conectado salvo con directorio.
6. **Índices**: `{tenant_id, system, lastSeenAt}`.
7. **Estrategia**: mantenimiento incremental en el pipeline (§6.2) — update con `$max` sobre `lastSeenAt`.

### 5.6 Número de sesiones

1. **Definición**: cantidad de sesiones distintas en el periodo. Una sesión = un `sessionId` distinto, o
   (si el sistema no manda sessionId) un segmento de actividad de una identidad con gap > G minutos entre eventos (default G=30).
2. **Fuente**: `log_events.remoteConnection.sessionId` / pares login-logout / heurística de gap.
3. **Campos**: `remoteConnection.sessionId`, `remoteConnection.sessionDuration`, `eventTime`, `actor.id`.
4. **Algoritmo**:
   - EXPLICIT: `$group: {_id: sessionId, identity: {$first: actor.id}, start: {$min: eventTime}, end: {$max: eventTime}, events: {$sum:1}}` — solo cuando `capabilities.sessions ∈ {EXPLICIT, INFERRED}`.
   - INFERRED por eventos: emparejar login→logout por identidad; sin par, cerrar a las 23:59 o por gap.
   - Por gap (fallback): ordenar eventos por identidad+tiempo y cortar cuando `Δt > G`.
5. **Limitaciones**: la heurística de gap subestima/sesga cuando G no representa el uso real; marcar `DEGRADED`.
6. **Índices**: nuevo `{tenant_id, system, "remoteConnection.sessionId", eventTime}`.
7. **Estrategia**: materializar sesiones resueltas hacia `session_summary` (una fila por sesión) en la
   reconciliación nocturna; el endpoint nunca agrupa en caliente sobre más de 1 día.

### 5.7 Primera y última actividad observada

1. **Definición**: `firstSeenAt` / `lastSeenAt` por identidad (a minuto). **No confundir con `registeredAt`** (fecha oficial, §5.0).
2. **Fuente**: `identity_directory` (mantenido por el pipeline §6 + backfill §6.4).
3. **Campos**: `firstSeenAt, lastSeenAt, firstSystem, lastSystem`.
4. **Algoritmo**: backfill: una sola agregación `$group identityKey {first: {$min: eventTime}, last: {$max: eventTime}}` por tenant/sistema con lotes por rango temporal; mantenimiento: update con `$max` diario.
5. **Limitaciones**: sujeto a retención (90 días default): `firstSeenAt` no es anterior a la retención salvo backfill previo a purga.
6. **Índices**: `{tenant_id, system, identityKey}` único.
7. **Estrategia**: ver §6.4 — el backfill corre por sistema con rangos acotados y `allowDiskUse`.

### 5.8 Tendencias de crecimiento

1. **Definición**: serie temporal de todas las métricas anteriores, por día, con granularidad diaria/semanal/mensual, ambas familias (calendario y móvil donde aplique, §5.3).
2. **Fuente**: `identity_period_stats` (buckets diarios + snapshots móviles).
3. **Campos**: `bucketStart, granularity, metric, windowType: CALENDAR|ROLLING, value, partial`.
4. **Algoritmo**: los endpoints solo leen series ya calculadas; el pipeline diario computa el bucket del día anterior completo y actualiza el día en curso marcado `partial: true`.
5. **Limitaciones**: los buckets del día en curso son parciales por definición.
6. **Índices**: `{tenant_id, system, granularity, windowType, metric, bucketStart}` único.
7. **Estrategia**: misma idea que `ai_metrics` (ya existe como concepto), pero con writer garantizado (§8.5).

### 5.9 Actividad por tipo de entidad

1. **Definición**: volumen de actividad segmentada por `actor.type` (USER/SERVICE/DEVICE), por sistema y oficina (`location.id`).
2. **Fuente**: `log_events` en caliente (agregaciones con índices existentes) + `identity_period_stats` para humanos.
3. **Campos**: `actor.type`, `system`, `location.id`, `eventType`.
4. **Algoritmo**: reutilizar el patrón `aggregate(base, field, 20, total)` de `LogDashboardService.stats`.
5. **Limitaciones**: `location` describe el evento, no la pertenencia del usuario.
6. **Índices**: existentes (`idx_tenant_system_location_time_v2`, `idx_tenant_system_actor_time_v2`).
7. **Estrategia**: caché `TimedCache` 30-60 s, igual que `ExecutiveAnalyticsService`.

### 5.10 Resumen: dónde se calcula cada cosa

| Métrica | En caliente sobre `log_events` | Materializada (pipeline §6) | Estado si no aplica |
|---|---|---|---|
| Registrados | No | `identity_directory` (sync) | `NOT_AVAILABLE` (value null) |
| Nuevos (oficial) | No | `identity_directory.registeredAt` | `NOT_AVAILABLE` (value null) |
| Nuevos (observados) | No | `identity_directory.firstSeenAt` | `DEGRADED` (con note) |
| DAU / WAU / MAU (cal.) | Solo ≤7 días (umbral) | `identity_period_stats` | `NOT_AVAILABLE` (value null) |
| MAU móvil 7/30 | Solo ≤7 días (umbral) | `identity_period_stats` | `NOT_AVAILABLE` (value null) |
| Recurrentes | No | `identity_period_stats` | `NOT_AVAILABLE` (value null) |
| Inactivos | No | `identity_directory.lastSeenAt` | `NOT_AVAILABLE` (value null) |
| Sesiones | Solo 1 día | `session_summary` | `NOT_AVAILABLE` si `capabilities.sessions = NONE` |
| Primera/última actividad | No | `identity_directory` | — |
| Tendencias | No | `identity_period_stats` | — |
| Actividad por entidad | Sí (≤7 días, cacheada) | — | — |

---

## 6. Pipeline de procesamiento (incremental + reconciliación + idempotencia)

Esta sección define cómo se mantienen los datos materializados correctos en todo momento (obs. 5).

### 6.1 Diagrama de flujo

```
ingest ──> [resolver identidad §3] ──> upsert incremental del día en curso (estimación)
              │
              └─ marca `day_dirty(day)` si el evento llega tarde (eventTime < día actual)

job cada N min (día en curso) ──> re-agrupa solo el día en curso desde log_events
                                   └── reemplaza las filas de hoy (idempotente por diseño)

reconciliación nocturna 00:30 ──> para cada day_dirty de los últimos R días:
                                   re-agrupa ese día completo y REEMPLAZA sus filas
                                   └── recalcula identity_directory (first/lastSeen, conteos)
                                   └── recalcula buckets de identity_period_stats afectados
                                   └── marca day limpio
```

### 6.2 Actualización incremental del día en curso

- Un job ligero (cada 5–15 min) re-agrupa **solo el día natural en curso** desde `log_events`
  (rango acotado, hint + `maxTime`) y **reemplaza** (no acumula) las filas de hoy en
  `identity_daily_stats` para los sistemas activos.
- `identity_directory` se actualiza en el mismo paso: `$max` sobre `lastSeenAt/lastSourceIp/...`,
  conteos incrementales de hoy.
- Las lecturas del dashboard durante el día usan estas filas parciales (etiquetadas `partial: true`
  en tendencias); son una **estimación consistente** porque se recomputean desde la fuente, no se acumulan deltas.

### 6.3 Idempotencia y eventos retrasados

- **Idempotencia por construcción**: ningún paso hace `$inc` permanente sobre conteos de días
  cerrados. Todo conteo de un día se obtiene recomputando ese día desde `log_events` y reemplazando
  el resultado materializado. Re-ejecutar cualquier paso N veces produce el mismo estado.
- **Eventos retrasados** (`createdAt` mucho mayor que `eventTime`, o llegadas posteriores a la
  reconciliación): el ingest marca `day_dirty(eventTime)` (colección ligera `dirty_days`:
  `{tenant_id, system, day, detectedAt}`). La reconciliación nocturna re-procesa los últimos R días
  marcados (default R=3, configurable según el desfase de llegada observado en M0).
- La reconciliación corre aunque no haya dirty days (recompute del día en curso al cierre del día),
  de modo que el día cerrado queda verificado contra la fuente al menos una vez.

### 6.4 Backfill histórico

- Ejecutable manual (admin, `SETTINGS_MANAGE`), por tenant + sistema + rango de meses, con
  **checkpoint reanudable** (guarda último mes completado).
- **Requisito previo obligatorio (obs. 11)**: el backfill de un sistema solo se habilita después de
  que el diagnóstico M0 (§12) haya validado la cobertura de identidad de ese sistema. Sin M0, no hay
  backfill ni colecciones productivas creadas.
- Cada mes se procesa con agregaciones acotadas + `allowDiskUse`; los resultados **reemplazan** lo
  materializado del rango (idempotente).

### 6.5 Retención de métricas y tratamiento de identidades eliminadas (obs. 8)

**Retención separada** (los agregados sobreviven a la purga de logs):

| Colección | Retención default | Purga |
|---|---|---|
| `identity_daily_stats` | 400 días | job propio integrado en `LogRetentionService` |
| `session_summary` | 400 días | idem |
| `identity_period_stats` | sin purga (volumen trivial: 1 fila/métrica/día/sistema) | — |
| `identity_directory` / `identity_aliases` | sin purga automática; ver eliminación abajo | política de datos personales |

**Identidades eliminadas** (`accountStatus = DELETED` por el directorio, o baja solicitada):

1. **Anonimización, no borrado histórico ciego**: `username`/`fullName` se sustituyen por hash
   irreversible (`sha256(tenant|identityKey)`), `lastSourceIp` se elimina; la fila queda como
   **tumba** (`anonymizedAt`) para no romper agregados históricos ni conteos de tendencias.
2. Los alias asociados se anonimizan igual; su `eventCount` histórico se conserva (es un conteo,
   no dato personal).
3. `identity_daily_stats` e `identity_period_stats` conservan las filas históricas (la dimensión
   personal ya no es resoluble — la fila referencia una tumba). Esto preserva DAU históricos.
4. El detalle PII completo (nombres, IPs por evento) sigue viviendo en `log_events` y desaparece
   con la retención de logs normal — la anonimización del directorio garantiza que el módulo no
   retenga PII más allá de esa fuente.
5. Reaparición: si la clave vuelve a observarse tras la baja, se crea una **identidad nueva**
   (la tumba no se reactiva), evitando re-enlazar historiales que el usuario pidió olvidar.

---

## 7. Arquitectura del motor de anomalías

### 7.1 Diseño general

Dos etapas sobre un mismo esqueleto:

- **Etapa 1 (reglas deterministas)**: evaluador de reglas configurables, declarativas y explicables.
  Cada hallazgo cita la regla, los datos que la dispararon y el umbral configurado.
- **Etapa 2 (líneas base históricas)**: mismas señales comparadas contra baseline por entidad/rol/sistema
  (media ± k·desviación, o percentiles), activables regla a regla y sistema a sistema.

El diseño ya está prefigurado en `docs/ARQUITECTURA_UNIVERSAL_DEFINITIVA.md` (plugin JSON con
`anomalyRules`) y `docs/ENTREGABLE_1.2_ANALYTICS_ENGINE.md` (modelo `DetectedAnomaly`,
colección `detected_anomalies`) — **ninguno está implementado**. Esta propuesta es compatible
con ambos documentos y revisa primero la reutilización de la infraestructura de alertas existente
(§8.5).

### 7.2 Configuración por contexto (declarativa, por tenant)

```json
{
  "ruleId": "ACTIVITY_OUTSIDE_SCHEDULE",
  "tenantId": "...",
  "scope": { "system": "TRUSTVALUE", "officeId": null, "role": null,
             "identityType": "INTERNAL_USER", "deviceId": null },
  "enabled": true,
  "severity": "WARNING",
  "params": { "allowedWindows": [{"dow":[1,2,3,4,5], "from":"07:00", "to":"20:00"}],
              "timezoneSource": "ORG_SETTING" },
  "baseline": { "mode": "NONE" },
  "suppression": { "cooldownMinutes": 240, "dedupeKey": ["identityKey", "ruleId", "day"] }
}
```

Resolución de configuración (de más específica a más general):
`tenant + system + office + role + identityType + device` → `tenant + system` → `tenant` (default).
La regla efectiva que se aplica a una identidad es la de mayor especificidad; se puede inspeccionar
("¿por qué se disparó / no se disparó?") — explicabilidad obligatoria.

### 7.3 Catálogo inicial de reglas (Etapa 1)

| Regla | Señal | Datos mínimos requeridos | Notas |
|---|---|---|---|
| `AUTH_FAILURE_BURST` | ≥N (`authResult`=FAIL en `remoteConnection`) fallos en T min por identidad/IP | `remoteConnection.authResult`, `sourceIp` | Sistema-agnóstica; la más fiable hoy |
| `ACTIVITY_OUTSIDE_SCHEDULE` | Actividad humana fuera de ventanas autorizadas | `actor.type=USER`, `eventTime`, tz org | Solo si el tenant configura ventanas; si no → regla deshabilitada, **nunca** asumir horario |
| `ACTIVITY_REPEATED_UNUSUAL_HOURS` | Actividad recurrente en franja horaria inusual **para esa entidad** | requiere Etapa 2 o histórico mínimo | "Inusual" = desviación del patrón propio, no "de noche = malo" |
| `VOLUME_SPIKE` | Volumen de operaciones por identidad/sistema > X% sobre baseline | Etapa 2 (o umbral absoluto en Etapa 1) | Etapa 1 con umbrales absolutos configurables |
| `UNEXPECTED_DEVICE` | Identidad humana activa desde dispositivo/IP no visto en D días para esa identidad | `remoteConnection.sourceIp`, resolver de dispositivo centralizado | Día/identidad, no global |
| `SENSITIVE_OP_OFF_PATTERN` | Operación marcada sensible fuera del patrón habitual | `eventType` en lista configurable `sensitiveEventTypes` | El tenant define qué es sensible |
| `TECH_ACTIVITY_ANOMALY` | Servidor/servicio: paro inesperado, arranques repetidos, errorRate alto | `SYSTEM_STARTUP/SHUTDOWN`, `isError` | Para `actor.type=DEVICE/SERVICE`; umbrales distintos a los humanos |

**Regla explícita de no-falso-positivo**: la actividad nocturna NO es sospechosa por sí misma.
`ACTIVITY_OUTSIDE_SCHEDULE` solo aplica donde el tenant declaró ventanas; los sistemas 24×7
(servidores, switches) usan reglas técnicas, no de horario humano. Se prevén:
- Turnos nocturnos → ventanas por `scope` (oficina/rol) con `dow`/`from`/`to` propios.
- Vacaciones/festivos → colección `holiday_calendars` por tenant (o supresión manual de hallazgos).
- Operaciones programadas → ventanas de mantenimiento configurables que suprimen reglas por sistema+franja.
- Zona horaria → siempre desde `Organization.settings.timezone` (única fuente confiable hoy).

### 7.4 Evaluación y doble ciclo de vida (obs. 9)

**Separación explícita entre "la condición anómala terminó" y "la investigación se resolvió":**

- **`conditionStatus` (automático, lo gestiona el evaluador)**:
  `ACTIVE` — la señal sigue observándose en las ventanas de evaluación.
  `ENDED` — la señal deja de cumplirse durante 2 ventanas consecutivas. El evaluador lo marca solo;
  **no cierra la investigación**.
- **`investigationState` (humano, lo gestiona el analista)**:
  `OPEN` → `ACKNOWLEDGED` (tomada) → `RESOLVED` (cierre formal con dictamen y nota obligatoria)
  o `FALSE_POSITIVE` (motivo obligatorio, alimenta el ajuste de reglas).

Un hallazgo puede ser `conditionStatus = ENDED` e `investigationState = OPEN` (la condición ya pasó,
pero nadie lo revisó — sigue en la cola de trabajo). La resolución formal es siempre humana;
el sistema nunca pasa a `RESOLVED` por sí solo. Esta distinción evita dos errores opuestos:
investigaciones que se auto-cierran sin revisión y condiciones que reaparecen sin reabrir el expediente.

**Scheduler**: job cada hora (patrón `AlertSchedulerService`), evaluando la última hora completa
sobre datos materializados (`identity_daily_stats`, `session_summary`, agregaciones acotadas con
hint + `maxTime`), nunca un full-scan de `log_events`.

**Modelo de hallazgo** (ver colección definitiva según decisión §8.5):

```
anomaly_findings: {
  tenant_id, system, ruleId, ruleVersion,
  severity: INFO|WARNING|CRITICAL,
  identityKey, identitySummary {username, fullName},   // enriquecido al crear
  officeId, deviceId,                                  // cuando aplica
  evidence: { metric: "...", observed: ..., threshold: ..., window: {...} },
  explanation: "...",                                  // texto generado, explicación obligatoria
  fingerprint: sha256(tenant|ruleId|identityKey|dedupeDim),  // dedupe
  conditionStatus: ACTIVE|ENDED,                       // AUTOMÁTICO
  investigationState: OPEN|ACKNOWLEDGED|RESOLVED|FALSE_POSITIVE,  // HUMANO
  stateHistory: [{state, at, by, note}],
  firstSeenAt, lastSeenAt, occurrenceCount,             // reincidencia en mismo fingerprint
  assignedTo, ticketRef
}
```

- **Dedupe**: mismo fingerprint dentro del cooldown → se incrementa `occurrenceCount` y
  `lastSeenAt` en vez de crear otro hallazgo (mismo patrón que `AlertSentRecord`/fingerprint de `AiAlertRepository`).
- **Escalación**: si `occurrenceCount` crece o sube la severidad observada, escala WARNING→CRITICAL.
- **Reapertura**: si un hallazgo `ENDED` vuelve a observarse dentro de su período de retención,
  se reactiva (`conditionStatus → ACTIVE`, `occurrenceCount++`) conservando el historial de investigación.

### 7.5 Etapa 2 — líneas base

- `baseline_stats`: agregados por `(tenant, system, identityKey | role | office, dow, hour)`:
  media/desviación/percentiles de volumen, franjas activas, IPs/dispositivos distintos.
- Construcción: job semanal sobre `identity_daily_stats` + muestreo; mínimo N días (default 28)
  antes de activar reglas basadas en baseline (`baseline.mode != NONE` exige `minDataDays`).
- Persistencia separada de los logs (sobrevive purga de `log_events` dentro de la retención de métricas).

### 7.6 Anti falsos positivos y anti duplicados (resumen)

1. Reglas deshabilitadas por defecto hasta que el tenant configure parámetros (`enabled=false` en default tenant).
2. `minDataDays` para cualquier regla estadística.
3. Cooldown + fingerprint por entidad (no global): un pico en una oficina no apaga la misma regla en otra.
4. Supresión por ventanas de mantenimiento y calendarios.
5. `FALSE_POSITIVE` con motivo obligatorio → reporte mensual de reglas ruidosas (tasa FP por regla).

---

## 8. Modelos y colecciones

Solo colecciones nuevas justificadas, tras revisar las existentes (§1.5, obs. 12). Todo se aísla por
`tenant_id`. Los resúmenes se guardan con **identificadores y conteos, no con datos personales completos** (§11).

### 8.1 `identity_directory` — directorio de identidades observadas

```
{ tenant_id, system, identityKey,              // único compuesto
  aliases: [                                   // denormalizado (fuente: identity_aliases)
    { type, value, confidence }
  ],
  username, fullName,                           // últimos valores observados (no histórico)
  identityType: INTERNAL_USER|EXTERNAL_PERSON|SERVICE|DEVICE|UNKNOWN,
  source: OBSERVED|DIRECTORY_SYNC,
  // --- estado administrativo (externo, no inferido) ---
  accountStatus: ACTIVE|DISABLED|DELETED,       // null si no hay directorio
  registeredAt,                                // fecha oficial de alta; null si observedOnly
  // --- estado de actividad (derivado por el módulo) ---
  activityStatus: ACTIVE|DORMANT|INACTIVE,      // umbrales §5.5
  excluded: bool,                               // SOLO cuentas técnicas verificadas (§3.5)
  isPrivileged: bool,                           // segmento, NO exclusión
  // --- observación ---
  firstSeenAt, lastSeenAt, firstSystem, lastSystem,
  lastLocationId, lastDeviceId,                 // enriquecido incremental (sin IP persistente)
  eventCountTotal, activeDays,
  observedOnly: bool,
  mergedInto, mergeHistory,                    // fusiones §3.3
  anonymizedAt                                 // tumba §6.5
}
```
Los campos `accountStatus`/`registeredAt` (administrativos/oficiales) están separados de
`activityStatus`/`firstSeenAt` (derivados/observados) — **no se mezclan en un solo "status"** (obs. 6).

### 8.2 `identity_daily_stats` — actividad diaria por identidad

```
{ tenant_id, system, day (yyyy-MM-dd local), identityKey,
  eventCount, errorCount, sessionCount, firstEventAt, lastEventAt,
  eventTypes: {EVENT: n}, locations: [locationId], devices: [deviceId], sourceIps: [ip] }
```
Índices: **único `{tenant_id, system, day, identityKey}`** (la clave completa; una fila por
identidad y día — versión corregida, obs. 4) y secundario `{tenant_id, system, day}` para barridos
del pipeline y consultas por día. El índice único además actúa como guarda de idempotencia del
reemplazo (upsert por clave completa).

### 8.3 `identity_period_stats` — tendencias

```
{ tenant_id, system, granularity: DAILY|WEEKLY|MONTHLY,
  bucketStart, windowType: CALENDAR|ROLLING,
  metric: DAU|WAU_CALENDAR|MAU_CALENDAR|MAU_ROLLING_7|MAU_ROLLING_30|NEW_REGISTERED|NEW_OBSERVED|
          DORMANT|RECURRENT|SESSIONS|REGISTERED,
  value, partial: bool }
```

### 8.4 `session_summary` — sesiones resueltas

```
{ tenant_id, system, sessionId (o sintético), identityKey,
  startedAt, endedAt, durationSec, eventCount, sourceIp, deviceId,
  origin: EXPLICIT|EVENT_PAIR|GAP_HEURISTIC }
```

### 8.5 Decisiones de reutilización vs. colecciones nuevas (obs. 12)

| Decisión | Razonamiento |
|---|---|
| **Extender `DeviceRegistryService`** con el resolver de dispositivo/IP (extraer la lógica duplicada de `LogEventRepositoryImpl.toQuickAudit` y `AttendanceAnomalyService.extractDevice`). No crear un servicio paralelo. | La duplicación actual es un bug de diseño; un solo punto de resolución beneficiará también a servicios existentes. |
| **No reutilizar `ai_metrics`** para tendencias hasta verificar que tiene escritor y datos reales. Se crea `identity_period_stats` con writer propio. | El escritor de `ai_metrics` no se encontró en el código; acoplar el módulo a una colección posiblemente vacía es riesgo. |
| **Deprecar formalmente `sessions`** (legacy, sin escritor); el módulo no la usa. | Evita confusión de dos modelos de sesión. |
| **Revisar antes de crear**: `anomaly_rules`/`anomaly_findings` vs. las colecciones existentes `alert_rules`, `alert_incidents`, `ai_alerts` (y sus servicios `AlertIncidentService`, estados ack/resolve, dedupe por fingerprint). | Primera opción de implementación: **extender** `alert_rules`/`alert_incidents` con los campos propios (evidence, explanation, identityKey, conditionStatus/investigationState) si el modelo lo permite sin fricción. Solo si la incompatibilidad es real se crean colecciones nuevas, diseñadas para convivir y no duplicar schedulers ni canales de notificación. Decisión final en la fase M5 con spike técnico de ≤2 días. |
| **Reutilizar catálogo `event_types`** (colección existente) para los eventos semánticos (login/registro/logout) en lugar de crear un catálogo nuevo. | Ya existe con índice único `{tenant_id, code}`; hoy solo falta poblarlo. |
| **Schedulers**: integrar los jobs del módulo en la infraestructura `@Scheduled` existente (patrones de `AlertSchedulerService`/`LogRetentionScheduler`), no crear un framework de jobs paralelo. | Un solo mecanismo de scheduling que ya opera en producción. |
| **Caché**: usar `TimedCache`/Caffeine existente; extender la invalidación por WS de `DashboardNotifier` a las claves del módulo. | No introducir Redis ni un segundo sistema de caché. |

### 8.6 Servicios nuevos realmente necesarios

| Servicio | Responsabilidad |
|---|---|
| `IdentityResolutionService` | resolución canónica §3, alias con confianza, merge, cola de candidatos; clasificación §3.5 |
| `IdentityStatsPipeline` (scheduler + service) | incremental del día en curso, reconciliación nocturna, dirty_days, backfill con checkpoint |
| `SessionReconstructionService` | resolver sesiones EXPLICIT/EVENT_PAIR/GAP → `session_summary` |
| `AnomalyRuleService` + `AnomalyEvaluationScheduler` | CRUD de reglas + evaluación horaria (o extensión de los servicios de alertas existentes, §8.5) |
| `BaselineService` (Etapa 2) | construcción/seguimiento de líneas base |
| `DataDiagnosticsService` | Fase M0: reporte de cobertura por sistema (§12.2), reutilizando MongoTemplate + hint |

---

## 9. Endpoints propuestos

Todos bajo `/api/intelligence`, envueltos en `ApiResponse`, con `@PreAuthorize` por permisos,
`ScopeGuard` + `LogFilterCriteria`, y parámetros `system` opcional (default: todos los permitidos).

**Permisos nuevos sugeridos** (siguiendo el patrón `PermissionCode`): `IDENTITY_READ`
(ver métricas e identidades, sin PII completa salvo `VIEW_PII`) y `ANOMALY_MANAGE`
(revisar hallazgos y configurar reglas; puede mapearse inicialmente a `LOG_READ`/`ALERTS_MANAGE` para no tocar JWTs).

### 9.1 Contrato de disponibilidad (obs. 7 — vinculante para todo endpoint)

Toda métrica escalar se devuelve así:

```json
{ "value": 143, "state": "AVAILABLE" }
{ "value": 12,  "state": "DEGRADED", "note": "observed_only_no_directory" }
{ "value": null, "state": "NOT_AVAILABLE", "note": "system_has_no_human_identities" }
```

- `NOT_AVAILABLE` ⇒ **`value` siempre `null`** (una cifra sería potencialmente engañosa).
- `0` solo aparece con `state: AVAILABLE` (fuente existente y conteo realmente cero).
- El frontend distingue visualmente los tres estados (nunca renderiza null como 0).

### 9.2 Resumen de usuarios y adopción

`GET /api/intelligence/users/summary?system=&from=&to=`

```json
{ "systems": ["TRUSTVALUE"],
  "registered":     { "value": null, "state": "NOT_AVAILABLE", "note": "no_directory_sync" },
  "observed":       { "value": 876,  "state": "AVAILABLE" },
  "dau":            { "value": 143,  "state": "AVAILABLE" },
  "wauCalendar":    { "value": 401,  "state": "AVAILABLE" },
  "mauCalendar":    { "value": 803,  "state": "AVAILABLE" },
  "mauRolling7":    { "value": 512,  "state": "AVAILABLE" },
  "mauRolling30":   { "value": 790,  "state": "AVAILABLE" },
  "newRegistered":  { "value": null, "state": "NOT_AVAILABLE", "note": "no_directory_sync" },
  "newObserved":    { "value": 12, "state": "DEGRADED", "note": "observed_only" },
  "recurrent":      { "value": 301, "state": "AVAILABLE" },
  "dormant30":      { "value": 98,  "state": "AVAILABLE" },
  "sessions":       { "value": 620, "state": "DEGRADED", "note": "gap_heuristic" } }
```

### 9.3 Tendencias

`GET /api/intelligence/users/trends?system=&metric=DAU|WAU_CALENDAR|MAU_CALENDAR|MAU_ROLLING_7|MAU_ROLLING_30|NEW_OBSERVED|DORMANT|SESSIONS&granularity=DAILY|WEEKLY|MONTHLY&from=&to=`

Respuesta: `{ system, metric, windowType, granularity, points: [{bucketStart, value, partial}] }`
(series ya materializadas; `partial: true` en el bucket en curso).

### 9.4 Identidades

`GET /api/intelligence/identities?system=&activityStatus=ACTIVE|DORMANT|INACTIVE&accountStatus=ACTIVE|DISABLED|DELETED&identityType=&q=&sort=lastSeenAt&dir=desc&page=&size=`

`GET /api/intelligence/identities/{identityKey}/detail?system=` →
perfil: `firstSeenAt`, `lastSeenAt` (observación), `registeredAt` (oficial, null si no existe),
`accountStatus` y `activityStatus` por separado, activeDays, systems, topEventTypes,
ubicaciones habituales, dispositivos históricos (limitados), resumen de hallazgos asociados.

Paginación: `PageMeta` con limit+1 (sin count) sobre `identity_directory`
(`{tenant_id, system, identityKey}` único evita counts costosos).

### 9.5 Logs de una identidad

Reutilizar el endpoint existente: `GET /api/logs/events?actorId=...` (`LogEventController.search` ya
filtra por actorId). El backend traduce `identityKey → alias (ACTOR_ID/USERNAME)` vía
`identity_aliases` y aplica el filtro como OR sobre las claves. **No duplicar endpoint de logs.**

### 9.6 Anomalías

`GET /api/intelligence/anomalies/summary?system=&from=&to=` →
`{ open: n, endedUnreviewed: n, bySeverity: {...}, byRule: [{ruleId, count}], trend: [...] }`
(`endedUnreviewed` = `conditionStatus=ENDED ∧ investigationState≠RESOLVED/FALSE_POSITIVE`).

`GET /api/intelligence/anomalies/findings?system=&conditionStatus=&investigationState=&severity=&ruleId=&identityKey=&from=&to=&page=&size=`

`GET /api/intelligence/anomalies/findings/{id}` → detalle con `evidence` + `explanation` + `stateHistory`.

`PUT /api/intelligence/anomalies/findings/{id}/state` — **solo transiciones humanas**:
`{investigationState: ACKNOWLEDGED|RESOLVED|FALSE_POSITIVE, note}` (note obligatoria para
RESOLVED/FALSE_POSITIVE). El `conditionStatus` **no** es modificable por la API: lo gestiona el evaluador.

### 9.7 Configuración de reglas

`GET /api/intelligence/anomalies/rules?system=` → reglas efectivas resueltas por scope + herencia mostrada.

`PUT /api/intelligence/anomalies/rules/{ruleId}` (upsert por scope) — valida params por tipo de regla.

`POST /api/intelligence/anomalies/rules/{ruleId}/test` — ejecuta la regla en modo dry-run sobre
las últimas 24 h y devuelve los hallazgos que habría generado, **sin persistir**. Esencial para
evitar ruido antes de activar una regla.

### 9.8 Diagnóstico de datos (Fase M0)

`GET /api/intelligence/diagnostics/coverage?system=` (admin) → reporte por sistema de la fase M0
(§12.2). `POST /api/intelligence/diagnostics/run` (admin) → re-ejecuta el diagnóstico y actualiza
`system_capabilities` + `lastVerifiedAt`.

---

## 10. Índices y estrategia de rendimiento

### 10.1 Índices nuevos en `log_events`

| Índice | Motivo |
|---|---|
| `{tenant_id, system, "remoteConnection.sessionId", eventTime}` | resolución de sesiones y regla `UNEXPECTED_DEVICE` |
| `{tenant_id, system, "actor.username", eventTime}` | fallback de identidad cuando no hay `actor.id`; quickAudit actual agrupa por username usando hint inadecuado |
| `{tenant_id, system, "remoteConnection.authResult", eventTime}` | `AUTH_FAILURE_BURST` en caliente |

Ninguno duplica prefijos existentes: los `idx_tenant_system_*_time_v2` sirven cuando el filtro
incluye `system`; se mantiene esa convención de nombres (`idx_tenant_system_<campo>_time_v3`).

### 10.2 Índices de colecciones nuevas

| Colección | Índices |
|---|---|
| `identity_directory` | único `{tenant_id, system, identityKey}`; `{tenant_id, system, lastSeenAt}`; `{tenant_id, system, accountStatus}`; `{tenant_id, system, activityStatus}` |
| `identity_aliases` | **único `{tenant_id, system, aliasType, aliasValue}`** (una clave resuelve a exactamente una identidad canónica por sistema); `{tenant_id, identityKey}` |
| `identity_daily_stats` | **único `{tenant_id, system, day, identityKey}`** (obs. 4: la clave incluye siempre `identityKey`; una fila = una identidad en un día) + secundario `{tenant_id, system, day}` |
| `identity_period_stats` | único `{tenant_id, system, granularity, windowType, metric, bucketStart}` |
| `session_summary` | `{tenant_id, system, startedAt}`; `{tenant_id, identityKey, startedAt}` |
| `anomaly_findings` (si se crea, §8.5) | único `{tenant_id, fingerprint}`; `{tenant_id, system, conditionStatus, investigationState}`; `{tenant_id, identityKey, lastSeenAt}` |
| `baseline_stats` (Etapa 2) | único `{tenant_id, system, dim, dimKey, dow, hour}` |
| `dirty_days` | único `{tenant_id, system, day}` |

### 10.3 Reglas de rendimiento (vinculantes)

1. Ninguna consulta de este módulo carga eventos a memoria: solo agregaciones con hint,
   `maxTime(3000)` y `allowDiskUse`, sobre rangos acotados; lecturas de detalle paginadas.
2. Todo cálculo repetible se materializa en el pipeline (§6); los endpoints de lectura no re-agrupan logs.
3. Endpoints de lectura con caché Caffeine 30-60 s (`TimedCache`), invalidación por WS en ingest.
4. Retención de métricas materializadas independiente de `log_events` (§6.5); purga propia integrada en `LogRetentionService`.
5. Recálculo histórico: backfill por sistema y mes con checkpoint (reanudable), en horario de baja
   demanda; nunca en línea del request. **Requiere diagnóstico M0 previo (obs. 11).**
6. Datos faltantes: `state: NOT_AVAILABLE|DEGRADED` con `value: null` según §9.1; nunca devolver 0
   como "éxito" cuando la fuente no existe — 0 es un valor válido solo cuando la fuente existe y está vacía.

---

## 11. Riesgos de privacidad, seguridad y falsos positivos

### 11.1 Privacidad

- **Minimización**: las colecciones materializadas guardan `identityKey`, conteos y **últimos valores**
  observados, no historiales completos de nombre/IP. El detalle PII vive en `log_events` (ya sujeta a
  retención) y se consulta bajo permiso existente `VIEW_PII`.
- **Perfiles de actividad**: el detalle individual (§9.4) es un perfil de comportamiento; se propone
  exigir `IDENTITY_READ` y registrar accesos vía `Audit` (la colección `audits` ya existe).
- **Poblaciones sensibles**: ciudadanos externos (CITA_GUYANA) nunca se mezclan en métricas de
  "usuarios internos"; `identityType` separa poblaciones (§4.2).
- **Bajas**: anonimización con tumba (§6.5) — el módulo no retiene PII resoluble tras una baja,
  preservando agregados históricos.
- **Retención**: `identity_daily_stats` y `session_summary` con retención propia (default 400 días,
  §6.5); `identity_period_stats` sin purga (volumen trivial).

### 11.2 Seguridad

- Toda consulta nueva: `tenant_id` + `ScopeGuard.requireSystemAccess` + `LogFilterCriteria.apply`.
- Configuración de reglas y cambios de estado de hallazgos: `ANOMALY_MANAGE` (o `ALERTS_MANAGE` inicial).
- `conditionStatus` no es modificable por API (solo el evaluador) — evita "limpieza" de alertas
  simulando que la condición terminó.
- El endpoint `POST .../rules/{ruleId}/test` (dry-run) ejecuta agregaciones con las mismas
  protecciones (rango acotado 24 h, maxTime) para no ser un vector de consultas pesadas.
- Ingest sigue prohibido con JWT humano; los emisores siguen autenticándose por API key.

### 11.3 Falsos positivos

- Reglas deshabilitadas por defecto hasta configuración del tenant (§7.6).
- La regla de horarios exige ventanas declaradas; "nocturno ≠ sospechoso" es regla de diseño, no umbral.
- Cooldown por entidad + fingerprint; supresión por calendarios y mantenimientos.
- **No exclusión humana por patrones genéricos** (§3.5): las cuentas privilegiadas se segmentan, no se ocultan.
- Métrica de salud del propio motor: tasa de `FALSE_POSITIVE` por regla y por sistema, visible para
  el admin — una regla con >X% FP se recomienda ajustar/deshabilitar.

---

## 12. Plan de implementación por fases (reordenado, obs. 10)

El desarrollo se reorganiza para entregar **primero identidades + métricas básicas + una primera
vista frontend**, dejando el motor avanzado de anomalías para después.

| Fase | Contenido | Criterio para pasar a la siguiente |
|---|---|---|
| **M0 — Diagnóstico de datos (OBLIGATORIO, obs. 11)** | `DataDiagnosticsService` + reporte de cobertura por sistema. **Sin M0 no se crean colecciones productivas ni se ejecutan backfills.** | Reporte ejecutado en datos reales; `system_capabilities` sembrado y validado por el equipo |
| **M1 — Identidades** | `identity_directory` + `identity_aliases` (resolución §3), pipeline incremental + reconciliación nocturna, `system_capabilities` | Identidades resueltas en prod; DAU manual cuadra con conteo directo; dedupe verificado |
| **M2 — Métricas básicas** | `identity_daily_stats`, `identity_period_stats`, endpoints §9.2–§9.5 | Summary/trends/identities <500 ms; contrato `value:null` para NOT_AVAILABLE verificado |
| **M3 — Primera vista frontend** | Pantallas Vue/Quasar: resumen de adopción, tendencias y listado de identidades (lectura) | Vista usable por un supervisor con datos reales; estados DEGRADED/NOT_AVAILABLE renderizados |
| **M4 — Sesiones** | `session_summary`, `SessionReconstructionService` | EXPLICIT vs heurística correctamente etiquetadas; sin agregaciones en caliente >1 día |
| **M5 — Motor de anomalías Etapa 1** | Reglas deterministas, evaluador horario, hallazgos (decisión de colección §8.5 con spike ≤2 días), endpoints §9.6–§9.7 | Reglas activadas en ≥1 tenant sin tormenta de FP (<5% en 2 semanas); doble ciclo de vida (§7.4) respetado |
| **M6 — Etapa 2 (baselines)** | `baseline_stats`, `holiday_calendars`, reglas estadísticas | Baselines ≥28 días; revisión humana razonable de precisión |

Cada fase es desplegable de forma independiente; M0–M3 entregan valor sin motor de anomalías.

### 12.1 Por qué este orden

- M0 elimina el riesgo mayor (diseñar sobre supuestos falsos sobre los datos) antes de crear nada.
- M1–M2 son la base técnica de todo lo demás y ya producen valor analítico.
- M3 da feedback de usuario real temprano y valida el contrato de API antes de construir el motor.
- El motor de anomalías (M5–M6) es lo más complejo y depende de identidades y métricas fiables;
  construirlo antes sería construir sobre arena.

### 12.2 Diagnóstico de datos M0 — especificación (obs. 11)

**Requisito obligatorio previo a cualquier colección nueva o backfill.** Ejecutable por admin
(`SETTINGS_MANAGE`), por tenant y por sistema, sobre datos reales (idealmente producción o réplica).

Reporte por sistema (últimos 30 días por defecto):

| Medida | Qué responde |
|---|---|
| **Cobertura de `actor.id`** | % de eventos con `actor.id` no vacío; cardinalidad (¿es estable por usuario? ¿cuántos ids distintos por username?) |
| **Cobertura de `actor.username`** | % de eventos con username; cardinalidad; normalización necesaria (case, espacios) |
| **Cobertura y valores de `actor.type`** | % con type; distribución USER/SERVICE/DEVICE/vacío; valida `identityHints.humanTypes` |
| **Cobertura de `remoteConnection.sessionId`** | % de eventos con sessionId; sesiones por identidad (¿sirve EXPLICIT?) |
| **Eventos de login/registro/logout** | ¿existen los eventTypes sembrados? ¿qué eventTypes reales actúan como login (frecuencia + presencia de authResult)? |
| **Tipos de identidad por sistema** | distribución preliminar INTERNAL_USER vs EXTERNAL_PERSON vs técnica (heurística §3.5) |
| **Volumen por sistema** | eventos/día; decide umbrales de caliente vs materializado y el desfase de llegada (fija R en §6.3) |
| **Cobertura de `remoteConnection.authResult`** | ¿es usable `AUTH_FAILURE_BURST` en caliente? |

Salida: actualiza `system_capabilities` (con `lastVerifiedAt`) y produce un dictamen por sistema:
`READY_FOR_BACKFILL | READY_WITH_CAVEATS (notas) | NOT_READY (bloquea backfill)`.
Los defaults del módulo (gap de sesión G=30 min, umbral caliente 7 días, R=3 días) se **ajustan con
los números de este reporte**, no al revés.

---

## 13. Pruebas de aceptación por fase

**M0**
- El reporte se ejecuta por tenant en <5 min por sistema y su salida cuadra con un muestreo manual Mongo (5 consultas de verificación, 100% match).
- Un sistema sin eventos humanos queda `NOT_READY`/capacidades `NONE`, no con valores inventados.

**M1**
- Dado un tenant con logs de 30 días, la resolución crea identidades canónicas con aliases:
  usuario con eventos mezclados (actor.id presente/ausente) → **una sola identidad** con 2 aliases,
  confianzas HIGH (co-ocurrencia) y LOW (username solo).
- Dos usuarios distintos con el mismo `fullName` y distinto username → dos identidades, sin merge.
- Un alias que reaparece ligado a otro `actor.id` no fusiona nada automáticamente: queda candidato
  de merge pendiente (no colapsa métricas).
- Cuenta `admin.turno` (humana privilegiada) **no** se excluye: aparece en métricas con `isPrivileged=true`.
- Cuenta técnica verificada (`actor.type=SERVICE`) queda `excluded=true` fuera de métricas humanas.
- Idempotencia: ejecutar el pipeline 2 veces sobre el mismo día produce `identity_daily_stats` idéntico.
- Evento retrasado (eventTime hace 2 días, llega hoy) marca `dirty_days`; tras la reconciliación, el
  día retrasado refleja el evento y los conteos del día actual no se duplican.

**M2**
- DAU del día cerrado (reconciliación) == `count(distinct identityKey)` directo sobre `log_events` (misma franja, mismos filtros).
- `wauCalendar` (semana ISO) y `mauRolling7` (últimos 7 días) son métricas distintas y sus valores difieren en un caso de prueba construido alrededor de un cambio de semana.
- Endpoint devuelve `value: null` con `state: NOT_AVAILABLE` para un sistema sin identidades humanas; nunca 0.
- Tendencias: 90 días diarios + 12 meses <300 ms.
- Listado de identidades paginado: página 50 con 50 items sin count full-collection.
- `activityStatus` cambia DORMANT→ACTIVE tras evento nuevo; `accountStatus` solo cambia por sync/admin, no por actividad.

**M3**
- El supervisor ve resumen, tendencias y listado con datos reales; los estados DEGRADED/NOT_AVAILABLE
  se distinguen visualmente de los valores reales (incluido null ≠ 0).

**M4**
- Sistema EXPLICIT: sesiones == `distinct sessionId` (match exacto).
- Sistema GAP: con G=30, secuencia con gaps 5/45/5 min → 2 sesiones etiquetadas `origin: GAP_HEURISTIC`.
- Sesiones sin cierre llevan `endedAt` estimado marcado.

**M5**
- `AUTH_FAILURE_BURST`: 5 fallos en 10 min → 1 hallazgo; 5 fallos más dentro del cooldown no duplican
  (occurrenceCount=2); tras cooldown, nuevo hallazgo si persiste.
- `ACTIVITY_OUTSIDE_SCHEDULE` con turno nocturno configurado (22:00–06:00) no genera hallazgos para ese
  turno, y sí para otro rol diurno en la misma oficina.
- Ventana de mantenimiento configurada suprime hallazgos técnicos en la franja.
- **Doble ciclo de vida**: la condición que deja de cumplirse pasa a `conditionStatus=ENDED` pero
  `investigationState` sigue `OPEN`; la API rechaza intentos de modificar `conditionStatus`; solo un
  humano con nota pasa a `RESOLVED`/`FALSE_POSITIVE`.
- Una condición ENDED que reaparece reactiva el mismo hallazgo (mismo fingerprint) con historial conservado.
- Dry-run de una regla nueva devuelve resultados sin persistir nada.

**M6**
- Regla con `minDataDays=28` y 20 días de datos no se evalúa y reporta `insufficient_data`.
- Con 30 días, una desviación de 3σ en volumen dispara y una variación dentro de 1σ no.

---

## 14. Dependencias y cambios en aplicaciones externas

1. **TrustValue (y cualquier app móvil/web)** — recomendados, no bloqueantes para M0–M4:
   - Enviar `actor.id` estable (UUID de usuario) en todos los eventos — mejora la confianza de las
     equivalencias (VERIFIED/HIGH vs inferencia).
   - Añadir eventos semánticos: `USER_REGISTERED` (registro), `AUTH_LOGIN`/`AUTH_LOGOUT` con `sessionId`.
   - Enviar `remoteConnection.sessionId` y `sourceIp` en eventos de autenticación.
   - Opcional (fase posterior a M3): endpoint o exportación de directorio de usuarios para
     `registeredUsers = DIRECTORY_SYNC`, que habilita métricas oficiales y `accountStatus` reales.
2. **Pasaportes Guyana / CITA_QUINTANAROO**: definir si la población de ciudadanos se reporta como
   `EXTERNAL_PERSON`; acordar eventTypes de inicio/fin de trámite como proxies de sesión.
3. **Emisores técnicos (servidores Windows, switches)**: continuar `SYSTEM_STARTUP/BOOT/SHUTDOWN`
   (ya soportados por `DeviceRegistryService`); añadir `actor.type=DEVICE/SERVICE` consistente.
4. **Plataforma Santoro**: los JWTs deben incluir los permisos nuevos si se adoptan
   (`IDENTITY_READ`, `ANOMALY_MANAGE`) — o mapearse a permisos existentes en M5.
5. **Sin cambios de esquema en `log_events`**: todo lo anterior cabe en el modelo actual; los campos
   nuevos son de emisores dentro de `actor`/`remoteConnection` ya existentes.

---

## Anexo A — Lo que NO se puede verificar con el código disponible

1. **Distribución real de los datos**: qué porcentaje de eventos lleva `actor.id` vs solo `username`,
   qué sistemas mandan `remoteConnection.sessionId`, cuántos eventos tienen `actor.type`. Es exactamente
   lo que la Fase M0 (§12.2) medirá antes de construir sobre esos supuestos.
2. **EventTypes reales enviados por cada sistema**: el código solo muestra seeds de funneles y
   asistencia; el catálogo real vive en producción. `event_types` existe pero está vacío en el repo.
3. **Existencia de `actor.role` / `requestInfo.*` en documentos reales**: el código los lee del
   documento crudo, no del modelo; su presencia depende de payloads de emisores concretos.
4. **Volumen real de `log_events`** por tenant/sistema: condiciona umbrales de "en caliente vs
   materializado" (§5.3, §12.2).
5. **Cobertura de la colección `ai_metrics`**: no se encontró su escritor en el código; no asumir que
   tiene datos hasta verificarlo en producción (§8.5).
6. **Compatibilidad real de `alert_rules`/`alert_incidents` con el modelo de hallazgos propuesto**:
   requiere el spike técnico de M5 (≤2 días) antes de decidir extender vs. crear colección (§8.5).
7. **Zonas horarias reales de los tenants**: solo `Organization.settings.timezone` es confiable;
   no se puede verificar cuántas orgs lo tienen configurado.
8. **Acuerdos con los equipos de TrustValue/Guyana** sobre eventos nuevos y directorio de usuarios:
   son dependencias organizativas, no verificables en código.
9. **Tests existentes del módulo de analytics**: no se auditó la cobertura actual de tests; las
   pruebas de aceptación de §13 se automatizan como tests de integración nuevos.

## Anexo B — Decisiones de diseño pendientes de validación

1. ¿Permisos nuevos (`IDENTITY_READ`, `ANOMALY_MANAGE`) o reutilizar `LOG_READ`/`ALERTS_MANAGE`?
2. ¿Extender `alert_rules`/`alert_incidents` o crear `anomaly_findings`? (spike M5, §8.5).
3. ¿Retención default de métricas materializadas: 400 días propuesto — validar con necesidad de negocio.
4. ¿Ciudadanos (EXTERNAL_PERSON) cuentan en "adopción" o van en métrica separada? (propuesta: separada).
5. ¿Umbrales operativos: gap de sesión (30 min), umbral caliente (7 días), retraso R (3 días),
   umbrales de actividad (7/30/90 días)? — **a fijar con los números del diagnóstico M0** (§12.2).
6. ¿Multi-sistema por identidad? (¿un mismo humano en TrustValue y TICKETS cuenta como una identidad
   o dos?). **Propuesta v2: una identidad por (tenant, system)** — el cruce cross-system requiere
   equivalencias VERIFIED cross-system (directorio común) que hoy no existen; se deja para fase posterior.

## Anexo C — Mapeo de las observaciones de revisión

| # | Observación | Sección(es) que la resuelven |
|---|---|---|
| 1 | Resolución de identidad robusta con equivalencias verificables y confianza | §3 (3.1–3.4), §8.1–8.2, §10.2 |
| 2 | No excluir humanos por patrones de username | §3.5, §4.1 (`technicalAccounts` explícitos), §11.3 |
| 3 | Separar DAU/WAU/MAU calendario de ventanas móviles | §5.3 (métricas nombradas), §9.2–9.3, §13 M2 |
| 4 | Índice único de `identity_daily_stats` con `identityKey` | §5.3, §8.2, §10.2 (corregido en las tres secciones) |
| 5 | Incremental del día, reconciliación nocturna, idempotencia, eventos retrasados | §6 (6.1–6.4), `dirty_days` §10.2, §13 M1 |
| 6 | Separar estado administrativo / actividad / primera observación / registro oficial | §5.0, §8.1 (`accountStatus`, `activityStatus`, `firstSeenAt`, `registeredAt`), §9.4, §13 M2 |
| 7 | `value: null` cuando `NOT_AVAILABLE` | §2.3, §5.0, §9.1 (contrato), §9.2 (ejemplo), §13 M2 |
| 8 | Retención de métricas e identidades eliminadas | §6.5 (tabla de retención + anonimización/tumba + reaparición) |
| 9 | Separar fin de condición de resolución formal | §7.4 (`conditionStatus` vs `investigationState`), §9.6, §11.2, §13 M5 |
| 10 | Primero identidades + métricas + vista frontend; motor avanzado después | §12 (M0–M6 reordenado, 12.1) |
| 11 | Diagnóstico de datos obligatorio antes de colecciones/backfills | §12.2 (M0 especificado), §6.4, §10.3, §9.8 |
| 12 | Revisar dependencias/colecciones/índices existentes para no duplicar | §1.5 (inventario), §8.5 (decisiones), §10 |
