# PREPARACIÓN DE DESPLIEGUE — Versión con Fase M0 (Inteligencia de Actividad y Usuarios)
**Fecha:** 2026-10-20 · **Proyecto:** Backend de Logs (`dinamico-0.0.1-SNAPSHOT`) · **Rama:** `devEsau` @ `acd24c5` + cambios sin commitear del árbol de trabajo

> **Restricciones respetadas:** no se ejecutó despliegue, no se conectó a producción, no se modificó ninguna base de datos. Las pruebas de arranque se hicieron contra una **instancia MongoDB temporal y desechable** (dbpath aislado en `target/`, ya eliminado).

---

## 1. Veredicto

# ⚠️ APTO CON CONDICIONES

La nueva versión compila, pasa 36/36 pruebas, arranca limpia y **no modifica ingesta, autenticación, analítica ni multi-tenancy**. Las condiciones antes de desplegar son de confirmación operativa (no de código):

- **C1.** Confirmar el **método real de despliegue** en el servidor (existen dos variantes documentadas e incompatibles: servicio systemd en AWS EC2 vs script PowerShell en Windows — §8). **Pendiente de confirmación.**
- **C2.** Confirmar que los **orígenes WebSocket/CORS configurados** cubren todos los consumidores actuales: el cambio de `setAllowedOriginPatterns("*")` a lista explícita es el único cambio de comportamiento con impacto potencial en clientes existentes (§4). Los orígenes documentados cubren los frontends conocidos, pero cualquier consumidor no listado quedará fuera.
- **C3.** **Identificar la versión exacta desplegada hoy** (commit o hash del JAR). El análisis de delta asume que lo desplegado equivale a `HEAD` (`acd24c5`); si el JAR desplegado es más antiguo, el delta real incluiría además los commits intermedios y habría que rehacer la revisión (§2).
- **C4.** Ejecutar las **verificaciones post-arranque y regresión** del §9 y la **prueba del endpoint M0** del §10 en el servidor, y no dar por bueno el despliegue sin ellas.

**NO APTO no aplica** (no hay bloqueos técnicos); **APTO sin condiciones no aplica** (C1–C3 son confirmaciones pendientes reales).

---

## 2. Inventario del delta (qué cambia respecto a lo desplegado)

Asunción base: versión desplegada ≈ `HEAD` (`acd24c5`). **C3 pendiente.**

### 2.1 Archivos modificados (sin commitear, `git diff`)

| Archivo | Cambio | Líneas |
|---|---|---|
| `config/SecurityConfig.java` | Default de `app.cors.allowed-origins` amplía lista (añade `http://187.188.66.56:8032`, `http://localhost:9000`) | +5/-2 |
| `infra/ws/WebSocketConfig.java` | Orígenes WS/STOMP: de `"*"` hardcodeado a propiedad `app.websocket.allowed-origins` (lista explícita, soporta `*`) | +18/-6 |
| `controller/logs/LogEventController.java` | Nuevo query param **opcional** `deviceId` en endpoint de búsqueda de bitácora | +3 |
| `service/logs/LogEventService.java` | Filtro `deviceId`: igualdad exacta en `meta.deviceId`/`actor.id`/`actor.fullName` (OR) | +24 |
| `docs/DASHBOARD_LOGS.md`, `docs/README.md` | Solo documentación | — |

### 2.2 Archivos nuevos (sin trackear, módulo M0)

| Archivo | Naturaleza |
|---|---|
| `service/analytics/DataDiagnosticsService.java` | Nuevo servicio **solo lectura** (agregaciones acotadas hint + maxTime 3 s) |
| `controller/analytics/IntelligenceDiagnosticsController.java` | `GET /api/intelligence/diagnostics/coverage`, `@PreAuthorize("hasAuthority('PERM_LOG_READ')")` |
| `api/dto/intelligence/*` (9 DTOs) | DTOs del reporte; sin valores crudos de fullName/sessionId |
| `test/...DataDiagnosticsServiceTest.java`, `test/...IntelligenceDiagnosticsControllerSecurityTest.java` | 15 pruebas |
| `docs/M0_*.md`, `docs/PROPUESTA_*.md` | Documentación |

**Correcciones aplicadas durante esta preparación (forman parte del delta a desplegar):** bug NPE en `DataDiagnosticsService` (`List.of(..., null)` impedía ejecutar el diagnóstico) y verificación previa de índice hint con rechazo seguro 503 si falta.

---

## 3. Comportamiento al arranque (punto 3 del encargo) — AUDITADO

Auditados **todos** los `ApplicationRunner`/`CommandLineRunner`/`@PostConstruct` del proyecto. Evidencia obtenida levantando la aplicación contra instancia MongoDB temporal y desechable: al arrancar solo se crearon las colecciones `log_events` (0 docs, 4 índices) y `roles` (6 docs).

| Componente | Acción al arrancar | ¿Modifica registros existentes? | ¿Nuevo en esta versión? |
|---|---|---|---|
| `MongoTenantConfig.logEventsIndexInitializer` | `ensureIndex` idempotente de 3 índices globales: `idx_system_time_v1`, `idx_system_time_status_outcome_severity_v1`, `idx_time_status_v1` (ya los crea la versión desplegada; en producción son no-op) | **No** (crea si no existen; conflictos ignorados) | No (código intacto) |
| `RoleSeedRunner` | Inserta 6 roles **solo si no existen** (`existsByCode`) | **No** (insert-if-missing) | No |
| `FlowConfigSeeder` | Semilla de flujo demo | N/A en producción: `@Profile("dev")` no activa con `prod,mail` | No |
| `MongoConnectionVerifier` | `ping` de solo lectura; aborta arranque si no hay conexión | No | No |
| `FirebaseConfig`, `JwtTokenService`, `SecurityConfig`, `ApiKeyTenantFilter`, `RateLimitPolicyService` | Inicialización en memoria (Firebase SDK, claves, filtros, buckets) | No | No |
| **Módulo M0 (nuevo)** | Sin runners, sin `@PostConstruct`, sin escrituras. Su única acción de metadata es `indexOps.getIndexInfo()` **durante la petición** (no al arrancar) | **No** | **Sí — inocuo** |

**Conclusión:** la nueva versión **no ejecuta migraciones, no crea índices inesperados** (solo los 3 globales ya conocidos, idempotentes) **y no modifica registros existentes** al arrancar. La única escritura potencial en frío son los 6 roles, que en producción ya existen ⇒ no-op verificable.

---

## 4. Análisis por área de riesgo (punto 2 del encargo)

| Área | ¿Cambiada? | Evaluación |
|---|---|---|
| **Ingesta de eventos y logs** | **No** | `LogEventIngestReq`, `ingest()` y validaciones intactos. El M0 es estrictamente de lectura. |
| **Autenticación, JWT y permisos** | **No** | `SecurityConfig` (filtros, JWT, rutas públicas) sin cambios funcionales; solo el default de orígenes CORS. El nuevo endpoint exige `PERM_LOG_READ` (probado: denega sin la autoridad). |
| **Dashboard y analítica ejecutiva** | **No** | `DashboardLogService`, `ExecutiveAnalyticsService`, `FunnelAnalyticsService` intactos. M0 solo **añade** un endpoint nuevo; no altera rutas ni contratos existentes. |
| **Bitácora y filtros por dispositivo** | **Sí, aditivo** | Nuevo parámetro opcional `deviceId` en búsqueda. Sin el parámetro, la consulta es idéntica a la versión desplegada. Igualdad exacta (sin regex) sobre `meta.deviceId`/`actor.id`/`actor.fullName`. Nota de costo: el OR solo aplica cuando el parámetro se envía (uso nuevo del dashboard); no afecta consultas existentes. Cubierto por `LogEventServiceDeviceFilterTest` (8 tests, en verde). |
| **WebSockets y STOMP** | **⚠️ Sí, comportamiento** | `WebSocketConfig`: `setAllowedOriginPatterns("*")` → lista explícita desde `app.websocket.allowed-origins`. **Riesgo de regresión:** cualquier consumidor cuyo origen no esté en la lista quedará fuera del handshake (antes entraba cualquiera). Mitigación documentada: en producción la variable `WEBSOCKET_ALLOWED_ORIGINS` (config-produccion.env) define `https://dashboard-api.grupo-santoro.com.mx, https://dashboard.grupo-santoro.com.mx, http://187.188.66.56:8032, http://localhost:9000`; Quintana Roo usa `*` (su script lo sobreescribe, y la nueva API soporta `*`). **Condición C2:** validar que no exista otro consumidor WS (móviles, integraciones) antes de desplegar. |
| **CORS** | **Sí, menor** | Default ampliado con `187.188.66.56:8032` y `localhost:9000`. En producción el valor efectivo viene de `CORS_ALLOWED_ORIGINS` (ya incluía ambos orígenes), por lo que el cambio de default no altera producción; solo afecta arranques sin variable de entorno. Headers expuestos sin cambios en este delta. |
| **Multi-tenancy** | **No** | `TenantContext`, `TenantRoutingMongoDbFactory`, `ScopeGuard`, resolución por `X-Tenant` intactos. M0 respeta el tenant de contexto (probado en pruebas: todo `$match` fija `tenant_id`). |
| **Conexión MongoDB e índices** | **No (arranque)** / **Sí (en petición M0)** | Conexión y creación de índices al arranque sin cambios (§3). Nuevo: el endpoint M0 verifica en runtime que existe `idx_tenant_system_time_v2` y **rechaza 503** si falta — ya confirmado en producción (existe, estructura correcta). |

---

## 5. Compilación y pruebas (punto 4 del encargo)

- **Compilación:** `./mvnw clean package` → BUILD SUCCESS (JAR reproducible en `target/dinamico-0.0.1-SNAPSHOT.jar`).
- **Suite completa con MongoDB temporal:** **36/36 verde** (`BacklogsApplicationTests` 1, `SecurityConfigCorsTest` 6, M0: `DataDiagnosticsServiceTest` 12 + `IntelligenceDiagnosticsControllerSecurityTest` 3, `OperationalErrorExplainerServiceTest` 3, `OperationalTranslationServiceTest` 3, `LogEventServiceDeviceFilterTest` 8).
- **Investigación de `BacklogsApplicationTests.contextLoads` — resuelta con evidencia, no asumida:**
  1. Sin MongoDB local, la prueba falla con `Failed to load ApplicationContext`; la traza apunta a `MongoTenantConfig.ensureIndex` → `ClientSessionBinding.getConnectionSource` (driver esperando conexión a `localhost:27017`).
  2. Se levantó un **mongod temporal desechable** (puerto 27017, dbpath aislado, sin datos) y se repitió la prueba: **pasa en ~13 s** (y la suite completa quedó 36/36).
  3. Conclusión: el fallo es **exclusivamente ambiental** (la app aborta el arranque sin MongoDB, comportamiento por diseño documentado en `MongoConnectionVerifier`), no un defecto de la nueva versión.

---

## 6. Entorno de pruebas (punto 5 del encargo)

- **No existe un entorno de staging documentado** para el backend principal (8040). Lo único cercano en los docs es el ambiente Quintana Roo (8057), que es **producción independiente** con su propia base (`logs_quintanaroo`) — no debe usarse como sandbox sin autorización expresa.
- **Sí se realizó prueba de arranque e integración local:** boot completo de la aplicación (`contextLoads`, 13 s) contra MongoDB temporal desechable, incluyendo la creación de índices y seeds de arranque (evidencia en §3). Esto valida el contexto Spring completo de la nueva versión, pero **no** sustituye una prueba contra datos/carga reales.
- Marcado según instrucciones: **prueba de integración en entorno de pruebas adecuado = NO DISPONIBLE.**

---

## 7. Identificación de versiones

- Versión nueva: `dinamico-0.0.1-SNAPSHOT`, rama `devEsau`, commit `acd24c5` + delta sin commitear (§2). Registrar al construir: `git rev-parse HEAD` y hash del JAR.
- Versión desplegada actual: **pendiente de confirmar en servidor** (C3). El JAR no lleva metadato de commit; identificar por fecha/hash de archivo. Acciones en el servidor (a confirmar según método real, §8):
  - Linux: `sha256sum /opt/backlogs/dinamico-0.0.1-SNAPSHOT.jar` (ruta documentada en `docs/DEPLOY_CORS_FIX.md`, **a confirmar**).
  - Windows: `Get-FileHash .\<ruta>\dinamico-0.0.1-SNAPSHOT.jar -Algorithm SHA256` (ruta **pendiente de confirmación**).
- **Nota de riesgo de versionado:** el nombre del artefacto es fijo (`0.0.1-SNAPSHOT`); el respaldo del JAR anterior (§9.1) es la única vía de rollback fiable.

---

## 8. Procedimiento de despliegue

> ⚠️ **Los docs del repo documentan DOS métodos distintos y no queda claro cuál es el real hoy:** (a) AWS EC2 con systemd `backlogs-api` y JAR en `/opt/backlogs/` (`docs/DEPLOY_CORS_FIX.md`, 2026-05-29); (b) Windows con `iniciar-backend-principal.ps1` (`mvnw spring-boot:run`, puerto 8040) + `config-produccion.env`. **C1: confirmar cuál se usa antes de ejecutar nada.** Las variantes se marcan abajo.

### 8.1 Construcción (mismo proceso en ambos métodos)

```powershell
# En la máquina de build (Windows, repo limpio en acd24c5 + delta M0)
.\mvnw.cmd clean package
# Verificar: target\dinamico-0.0.1-SNAPSHOT.jar existe y > 50 MB
Get-FileHash .\target\dinamico-0.0.1-SNAPSHOT.jar -Algorithm SHA256   # registrar hash nuevo
git rev-parse HEAD                                                     # registrar commit
```

### 8.2 Respaldo del JAR desplegado (obligatorio, antes de tocar nada)

- **Variante systemd (Linux):** `sudo cp /opt/backlogs/dinamico-0.0.1-SNAPSHOT.jar /opt/backlogs/dinamico-0.0.1-SNAPSHOT.jar.pre-m0-$(date +%Y%m%d)` — rutas según `DEPLOY_CORS_FIX.md`, **a confirmar**.
- **Variante Windows:** detener el proceso Java actual y copiar el JAR/script de inicio a `<ruta>.pre-m0-20261020` — **ruta pendiente de confirmación**.

### 8.3 Despliegue

- **Variante systemd (documentada):**
  ```bash
  sudo systemctl stop backlogs-api
  # copiar nuevo JAR a /opt/backlogs/ (scp según método de acceso del equipo)
  sudo systemctl start backlogs-api
  sudo journalctl -u backlogs-api -f     # observar arranque
  ```
  *(Tomado de `docs/DEPLOY_CORS_FIX.md`; usuario/host exactos y método de transferencia: **pendiente de confirmación** — no documentados.)*
- **Variante Windows:** cargar variables de `config-produccion.env` ajustadas y arrancar con `.\iniciar-backend-principal.ps1 -Production` **o** `java -jar target\dinamico-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod,mail` (comando exacto de producción: **pendiente de confirmación** — el script documentado usa `mvnw spring-boot:run`, que no es procedimiento de producción recomendado).

### 8.4 Verificaciones posteriores al arranque (§9) y rollback (§11) aplican a ambas variantes.

---

## 9. Verificaciones post-arranque y regresión de endpoints existentes

En orden, todas contra el servidor desplegado (ajustar host/puerto según ambiente real):

1. **Proceso y puerto:** servicio activo / `Tomcat started on port 8040`, log `Started BacklogsApplication`.
2. **MongoDB:** log `MongoDB ping OK. DB actual: logs_system` y los 3 `[MongoIndex] Índice verificado` (sin `[MongoIndex] Índice omitido` inesperados).
3. **Health:** `GET http://<servidor>:8040/actuator/health` → 200 `UP`.
4. **Swagger:** `GET http://<servidor>:8040/swagger-ui.html` → 200.
5. **CORS preflight:** `curl -X OPTIONS .../api/auth/login -H "Origin: https://dashboard.grupo-santoro.com.mx" -H "Access-Control-Request-Method: POST" -v` → 200/204 sin 403 (y lo mismo con origen `http://187.188.66.56:8032`).
6. **Login (autenticación/JWT):** `POST /api/auth/login` con usuario real → 200 + token; llamada autenticada a cualquier endpoint de lectura → 200 (valida JWT y cadena de filtros).
7. **Ingesta (regresión crítica):** enviar 1 evento de prueba identificable por el agente/emisor habitual → confirmar en bitácora que se persiste con `eventTime` y `severity` normalizados → **no debe haber cambios de comportamiento**.
8. **WebSocket/STOMP (C2):** conectar desde el dashboard desplegado (`187.188.66.56:8032`) a `/ws` → handshake exitoso; verificar en consola del navegador que no hay error CORS; confirmar recepción de mensajes en `/topic/...`.
9. **Bitácora:** búsqueda paginada sin filtros (idéntica a antes) + búsqueda de texto; respuestas y tiempos comparables a la versión anterior.
10. **Filtro dispositivo (nuevo):** `GET /api/logs?deviceId=<equipo conocido>` → solo eventos de ese equipo (igualdad exacta, sin mezclar equipos de nombre similar); sin `deviceId` → comportamiento previo.
11. **Analítica existente:** `GET /api/analytics/funnel/...` y endpoints ejecutivos con usuario autorizado → mismos números que antes del despliegue (mismas ventanas).

## 10. Prueba del nuevo endpoint M0

Precondición: ya verificado que en la BD de producción existe `idx_tenant_system_time_v2` (confirmado previo a este informe).

```bash
# 1. Sin autenticar → 401/403
curl -i "http://<servidor>:8040/api/intelligence/diagnostics/coverage?system=TRUSTVALUE"

# 2. Con usuario SIN PERM_LOG_READ → 403
# 3. Con usuario con PERM_LOG_READ (rol Auditor/Org Admin)
curl -i -H "Authorization: Bearer <JWT>" \
  "http://<servidor>:8040/api/intelligence/diagnostics/coverage?system=TRUSTVALUE&days=30"
```

**Resultados esperados:** (1)/(2) denegados; (3) HTTP 200 con reporte `{ tenantId, system, windowDays, actorCoverage, identityConflicts, sessionAuth, temporal, dataQuality, readiness, readinessNotes }` y `readiness` en `READY_FOR_BACKFILL | READY_WITH_CAVEATS | NOT_READY`. **Si responde 503 `required_index_missing`:** detener y verificar índices (no ignorar). Repetir (3) para `PASSPORT_GY`, `CITA_QUINTANAROO`, `TICKETS` y sistemas de servidores, un sistema por llamada, siguiendo `docs/M0_PROCEDIMIENTO_EJECUCION_CONTROLADA.md`. Cada respuesta < ~15 s.

## 11. Plan de rollback inmediato

1. **Detener** la nueva versión (`sudo systemctl stop backlogs-api` / cerrar proceso Java).
2. **Restaurar** el JAR respaldado en §8.2 a su ruta original.
3. **Arrancar** la versión anterior y verificar §9.1–§9.6 (health, login, WS, bitácora).
4. **Confirmar ingesta** con 1 evento de prueba (punto crítico: los agentes/emisores siguen escribiendo durante la ventana).
- Ventana estimada de rollback: minutos (solo intercambio de JAR). No hay migraciones que revertir (§3): el rollback no requiere tocar la base de datos.
- Nota: los índices globales creados por el arranque (`idx_system_*_v1`, `idx_time_status_v1`) ya existían en la versión desplegada; el rollback no los elimina ni los necesita eliminar.

---

## 12. Resumen de pendientes de confirmación (explícitos)

| # | Pendiente | Por qué |
|---|---|---|
| C1 | Método real de despliegue (systemd/AWS vs Windows/script) y rutas exactas | Dos variantes documentadas incompatibles |
| C2 | Consumidores WebSocket/CORS actuales vs orígenes configurados | Cambio `*` → lista explícita |
| C3 | Commit/hash del JAR desplegado | El delta asume HEAD = desplegado |
| — | Usuario/host/método de transferencia al servidor | `DEPLOY_CORS_FIX.md` dice "ajusta según tu método" |
| — | Comando exacto de arranque en producción | El script documentado usa `spring-boot:run` (no es procedimiento de prod) |
| — | Existencia y resultado de pruebas en entorno de pruebas | No hay staging; solo boot local con Mongo temporal |

---

*Restricciones respetadas en la elaboración de este informe: sin despliegue, sin conexión a producción, sin modificación de bases de datos. Única infraestructura usada: instancia MongoDB 8.2 temporal y desechable en localhost para evidenciar el arranque y las pruebas.*
