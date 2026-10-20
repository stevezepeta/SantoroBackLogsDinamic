# M0 — Procedimiento de Ejecución Controlada sobre Datos Reales
## Módulo: Inteligencia de Actividad y Usuarios — Diagnóstico de Cobertura

**Referencia:** `M0_DIAGNOSTICO_COBERTURA.md` · **Fecha:** 2026-10-20
**Alcance:** ejecutar el diagnóstico M0 (`GET /api/intelligence/diagnostics/coverage`) sobre TrustValue, PASSPORT_GY (CITA_GUYANA), CITA_QUINTANAROO, TICKETS y servidores/servicios, de forma controlada y de solo lectura.

> **Restricciones vigentes:** no se modifican datos, no se crean colecciones de M1, no hay backfills, no se despliegan cambios como parte de la ejecución, no se toca la ingesta y no se introducen reglas de anomalías nuevas. El diagnóstico es 100 % de solo lectura.

---

## 1. Precondiciones

1. El build con las pruebas M0 en verde (`DataDiagnosticsServiceTest`, `IntelligenceDiagnosticsControllerSecurityTest`).
2. Usuario de ejecución con autoridad `PERM_LOG_READ` (rol de auditoría/lectura de logs del dashboard Santoro).
3. Acceso al backend desplegado (o ejecución local apuntando a la réplica/bd de solo lectura).
4. Ventana de ejecución acordada con operaciones (ver §5 límites de tiempo).

## 2. Índices: verificación de solo lectura (obligatoria antes de ejecutar)

El servicio **autorrechaza** con HTTP 503 (`required_index_missing` / `index_verification_failed`) si el índice hint no existe; la verificación manual previa evita llegar a ese punto:

```javascript
// Conectarse a la base de destino (producción o réplica de solo lectura)
use logs_system; // o el nombre real de la bd del entorno
db.log_events.getIndexes();
```

- **Requerido por el diagnóstico:** `idx_tenant_system_time_v2` = `{ tenant_id: 1, system: 1, eventTime: -1 }`.
  Sin él el servicio no ejecuta nada (rechazo seguro). Creación manual (solo si operaciones lo aprueba, fuera de este procedimiento):
  ```javascript
  db.log_events.createIndex(
    { tenant_id: 1, system: 1, eventTime: -1 },
    { name: "idx_tenant_system_time_v2", background: true })
  ```
- **Recomendados para el resto de la analítica (no bloquean M0):** `idx_tenant_system_actor_time_v2`, `idx_tenant_system_eventType_time_v2`.
- No crear índices automáticamente: cualquier creación se decide y ejecuta con operaciones, en horario de baja carga, con `background: true`.

Estimación de costo previa (opcional, acotada):

```javascript
db.log_events.aggregate(
  [ { $match: { tenant_id: ObjectId("<TENANT>"), system: "<SISTEMA>" } },
    { $count: "n" } ],
  { maxTimeMS: 3000, hint: "idx_tenant_system_time_v2" })
db.log_events.stats()   // tamaño y count aproximado de la colección
```

## 3. Parámetros de ejecución

| Parámetro | Valor | Notas |
|---|---|---|
| Endpoint | `GET /api/intelligence/diagnostics/coverage` | solo lectura |
| `system` | `TRUSTVALUE`, `PASSPORT_GY`, `CITA_QUINTANAROO`, `TICKETS`, y cada sistema de servidores/servicios | normaliza a UPPER |
| `days` | **30** por defecto; usar **90** si la retención lo permite (máximo aceptado) | el servicio acota a [1, 90] |
| Usuario | auditor con `PERM_LOG_READ` | sin permiso → 403 |
| Tenant | el del contexto de sesión (aislamiento por `tenant_id`) | un tenant por ejecución |

Secuencia sugerida: un sistema por llamada, de menor a mayor volumen esperado (servidores/servicios primero si se incluyen, luego TrustValue, luego PASSPORT_GY, CITA_QUINTANAROO, TICKETS). Esperar el resultado de una llamada antes de lanzar la siguiente.

## 4. Volumen estimado y costo

- **Costo por llamada:** 4 agregaciones `$facet` con hint, `allowDiskUse`, `maxTimeMS: 3000`, batch 1000. Ninguna carga eventos a memoria: solo conteos y distribuciones acotadas.
- **Tiempo máximo por llamada:** ~12 s en el peor caso (4 × 3 s de maxTime); lo esperado es < 5 s por sistema.
- **Volumen:** acotado por el índice `tenant_id+system+eventTime`; aun con millones de documentos la agregación escanea solo el rango de 30–90 días del sistema. Los `$addToSet` de identidad/sesión están acotados por la cardinalidad real de ids en ventana (si un sistema tuviera cardinalidad extrema, `maxTime` lo corta y la faceta falla degradada sin tumbar el reporte).

## 5. Límites de tiempo y criterios de aceptación

**Límites de tiempo:**
- `maxTimeMS: 3000` por agregación (hard limit en el servicio).
- Ejecutar en horario de baja carga; si una llamada supera ~15 s total, detener y revisar índices antes de continuar.

**Criterios de aceptación por sistema:**
1. HTTP 200 con `readiness` distinto de `diagnostic_unavailable` (el fallback 200 con `diagnostic_unavailable` indica fallo: revisar logs `[DataDiagnostics]`).
2. Si HTTP 503 `required_index_missing`: detener la ejecución, verificar/crear índice (§2) y reintentar. No saltarse este rechazo.
3. `temporal.totalEventsInWindow` > 0 para sistemas que se sabe activos.
4. Validación cruzada (muestreo manual del 5 % o mínimo 5 verificaciones): los conteos del reporte deben cuadrar con consultas manuales equivalentes (match 100 % en los datos verificados).
5. Revisar `dataQuality`: `eventsWithoutEventTimeInWindow`, `futureEventsInWindow` y `potentialDuplicateGroups` deben tener notas explicativas si son > 0.
6. `readiness` final documentado por sistema: `READY_FOR_BACKFILL` / `READY_WITH_CAVEATS` / `NOT_READY`, con sus notas.

**Salida:** completar la matriz de capacidades (§4 del informe M0) con valores medidos y calibrar los parámetros de M1 (gap de sesión G, umbral caliente/materializado, retraso R) según §9 del informe.

## 6. Privacidad

- El reporte contiene conteos y distribuciones; los únicos identificadores textuales son `identityConflicts.conflicts` (top 10 usernames conflictivos con máx. 3 `sampleActorIds`). Tratar la respuesta como **dato sensible**: no publicarla, no versionarla en el repo, no incluir identificadores reales en informes.
- Los informes derivados deben usar conteos y porcentajes; si se citan ejemplos, anonimizar (p. ej. `usuario_ejemplo_1`).

## 7. Rollback / efectos colaterales

Ninguno sobre datos: el diagnóstico no escribe. Único efecto observable: carga de lectura acotada (≤ 4 agregaciones × 3 s por llamada) y entradas de log `[DataDiagnostics]` / `[IntelligenceDiagnostics]`.

## 8. Checklist de ejecución

- [ ] `db.log_events.getIndexes()` verificado en el entorno destino (índice hint presente).
- [ ] Build con pruebas M0 en verde.
- [ ] Usuario `PERM_LOG_READ` disponible.
- [ ] `?system=<S>&days=30` ejecutado por cada sistema, uno a la vez.
- [ ] 503 `required_index_missing` atendido (nunca ignorado).
- [ ] Validación cruzada con muestreo ≥ 5 consultas por sistema.
- [ ] Resultados volcados a la matriz M0 sin identificadores reales.
- [ ] Dictamen por sistema registrado y decisión de paso a M1 documentada.
