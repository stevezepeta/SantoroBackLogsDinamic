# Contrato GET /api/logs/events — Filtro por identidad de usuario

Fecha: 2026-10-23
Estado: implementado y probado (ver `LogEventServiceUsernameFilterTest`)

## Problema reportado

El módulo Analítica de Usuarios (frontend) consultaba:

```
GET /api/logs/events?system=TRUSTVALUE&username=esfe&page=0&size=5&fromDate=...&toDate=...&sortDir=DESC&sortBy=created_at
```

y recibía HTTP 200 con eventos de otros usuarios (`yael`), no solo de `esfe`.

## Causa raíz

El endpoint `GET /api/logs/events` (`LogEventController.search`) **no declaraba el
parámetro `username`**. Spring ignora silenciosamente los query params no
declarados, así que el filtro se descartaba y la consulta devolvía todos los
eventos del sistema dentro del periodo. El único filtro de identidad disponible
era `actorId` (igualdad exacta sobre `actor.id`).

Identidades en MongoDB (colección `log_events`):

| Campo            | Contenido                        |
|------------------|----------------------------------|
| `actor.id`       | Identificador del actor          |
| `actor.username` | Nombre de usuario (login)        |
| `actor.fullName` | Nombre completo                  |
| `actor.type`     | USER \| SERVICE \| DEVICE \| etc |

## Contrato del endpoint

```
GET /api/logs/events
```

Parámetros:

| Parámetro    | Obligatorio | Descripción |
|--------------|-------------|-------------|
| `system`     | sí          | Sistema (ej. `TRUSTVALUE`). Se normaliza a mayúsculas. |
| `username`   | no          | **Nuevo.** Igualdad exacta sobre `actor.username`. Si se envía, solo se devuelven eventos de ese usuario. |
| `actorId`    | no          | Igualdad exacta sobre `actor.id`. |
| `caseId`     | no          | Igualdad exacta sobre `caseId`. |
| `eventType`  | no          | Categoría de evento. |
| `eventCode`  | no          | Código de evento. |
| `status`     | no          | Normalizado a mayúsculas. |
| `outcome`    | no          | Normalizado a mayúsculas. |
| `severity`   | no          | Normalizado a mayúsculas. |
| `locationId` | no          | Igualdad exacta sobre `location.id`. |
| `requestId`  | no          | Igualdad exacta sobre `correlation.requestId`. |
| `deviceId`   | no          | Coincidencia exacta sobre `meta.deviceId` / `actor.id` / `actor.fullName`. |
| `text`       | no          | Búsqueda case-insensitive en `message` y `reason.description`. |
| `fromDate`   | no          | `LocalDate` (ISO). Inicio del día en UTC, inclusivo (`eventTime >= from`). |
| `toDate`     | no          | `LocalDate` (ISO). Fin del día siguiente en UTC, exclusivo (`eventTime < to+1d`). |
| `page`       | no (0)      | Base 0. |
| `size`       | no (10)     | Tamaño de página. |
| `sortBy`     | no          | Campo MongoDB. El valor del esquema para la fecha es **`eventTime`** (no `created_at`; ver nota para el frontend). |
| `sortDir`    | no (DESC)   | ASC \| DESC. |

Respuesta (`ApiResponse`):

```json
{
  "ok": true,
  "message": "Logs",
  "code": "log_events_search",
  "data": {
    "items": [ ... ],
    "page": 0,
    "size": 5,
    "totalElements": 12,
    "totalPages": 3
  }
}
```

Garantías:

- `tenant_id` del tenant autenticado siempre se aplica (aislamiento entre tenants).
- `system` siempre se aplica (o el alcance de sistemas permitidos del usuario).
- Los filtros se aplican **antes** de paginar; `totalElements`/`totalPages`
  cuentan solo los registros coincidentes.
- Sin coincidencias se devuelve página vacía con `totalElements = 0`.

## Contrato alternativo (auditoría rápida)

Existe además una agregación de búsqueda de usuarios por texto
(`LogEventRepositoryCustom.quickAuditUsers`), usada por el endpoint de
diagnóstico de usuarios (quick audit): busca coincidencias parciales
(case-insensitive) en `actor.username` / `actor.fullName` y resume la actividad
reciente por usuario. Sirve para localizar usuarios, no para listar eventos de
uno en específico.

## Ajustes que debe hacer el frontend

1. El parámetro `username` ahora sí se respeta: no es necesario cambiar la
   llamada; con `username=esfe` el backend devuelve solo eventos de `esfe`.
2. `start_date` / `end_date` **no existen** en este endpoint. Usar `fromDate` /
   `toDate` (las fechas extra enviadas hoy se ignoran silenciosamente).
3. `sortBy` debe ser un campo del esquema: `eventTime` (o `severity`,
   `status`, etc.). El valor actual `created_at` no existe en el documento y el
   orden queda indefinido.

## Archivos involucrados

- `src/main/java/backlogs/dinamico/controller/logs/LogEventController.java` — parámetro `username`.
- `src/main/java/backlogs/dinamico/service/logs/LogEventService.java` — criterio `actor.username`.
- `src/test/java/backlogs/dinamico/service/logs/LogEventServiceUsernameFilterTest.java` — pruebas nuevas.
- `src/test/java/backlogs/dinamico/service/logs/LogEventServiceDeviceFilterTest.java` — ajuste por cambio de firma.
- `src/main/java/backlogs/dinamico/repository/log/LogEventRepositoryCustom.java` y
  `LogEventRepositoryImpl.java` — restaurados (estaban referenciados por
  `LogEventRepository` pero ignorados por `.gitignore` por el patrón no
  anclado `log/`; con ello el proyecto no compilaba en un clone limpio).
- `.gitignore` — patrón `log/` reemplazado por `/logs/` y `/log/` anclados a raíz.
