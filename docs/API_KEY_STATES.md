# Estados de API Keys

## Descripción General

Este documento describe cómo funcionan los estados de las API Keys en el sistema Backlogs, incluyendo los diferentes estados posibles, transiciones entre estados, y las validaciones que se realizan durante el ciclo de vida de una API Key.

### Estados del Sistema

El sistema maneja **4 estados persistidos** en la base de datos:
- `active` - API Key activa y utilizable
- `disabled` - Deshabilitada por el usuario
- `rotated` - Reemplazada por una nueva
- `revoked` - Revocada por administrador de Santoro

Y **5 estados computados** para monitoreo:
- `OK` - Funcionando correctamente
- `RENEW_SOON` - Próxima a renovación
- `RENEW_REQUIRED` - Requiere renovación (warning)
- `EXPIRED` - Expirada (bloqueada)
- `DISABLED` - Deshabilitada o revocada

### ⚠️ Importante sobre el estado `revoked`

Si ves API Keys en estado `revoked`, significa que fueron **revocadas administrativamente** por Grupo Santoro. Esto típicamente ocurre cuando:
- Tu organización fue deshabilitada (todas las API Keys se revocan automáticamente)
- Un administrador revocó manualmente la API Key

**No podrás eliminar o modificar API Keys revocadas** - solo los administradores de Santoro pueden gestionarlas. Ver la sección "Panel de Santoro" para más detalles.

## Estados Persistidos

Las API Keys tienen un campo `status` en la base de datos que puede tener los siguientes valores:

### 1. `active`
- **Descripción**: La API Key está activa y puede ser utilizada para autenticar peticiones.
- **Validaciones adicionales**: Aunque el estado sea `active`, la API Key puede ser inutilizable si:
  - `expiresAt` ha pasado (la API Key está expirada)
  - No tiene los scopes necesarios para la operación
- **Creación**: Cuando se crea una nueva API Key, el estado por defecto es `active`.

### 2. `disabled`
- **Descripción**: La API Key ha sido deshabilitada manualmente y no puede ser utilizada.
- **Cómo llegar a este estado**: 
  - Llamando al endpoint `DELETE /api/catalogs/api-keys/{id}`
  - El servicio ejecuta `ApiKeyService.disable()`
- **Reversible**: Sí, mediante el endpoint de renovación `POST /api/catalogs/api-keys/{id}/renew`, que cambia el estado a `active`.

### 3. `rotated`
- **Descripción**: La API Key ha sido rotada (reemplazada por una nueva).
- **Cómo llegar a este estado**:
  - Llamando al endpoint `POST /api/catalogs/api-keys/{id}/rotate`
  - El sistema marca la API Key anterior como `rotated` y crea una nueva con el mismo nombre + " (rotated)"
- **Reversible**: No. Una API Key `rotated` no puede ser renovada.

### 4. `revoked`
- **Descripción**: La API Key ha sido revocada desde el Panel Administrativo de Santoro.
- **Cómo llegar a este estado**:
  - **Automáticamente**: Cuando una organización es deshabilitada desde el Panel de Santoro, **todas sus API Keys activas se revocan automáticamente**.
  - **Manualmente**: Llamando al endpoint `PUT /api/santoro/panel/organizations/{orgId}/api-keys/{keyId}/status` con body `{"status": "revoked"}`.
- **Propósito**: Cortar acceso inmediato a una organización (por ejemplo, cuando no paga la cuota o incumple términos).
- **Reversible**: Sí, desde el Panel de Santoro se puede cambiar el estado de vuelta a `active` usando el mismo endpoint.
- **Diferencia con `disabled`**: 
  - `disabled` es una acción del usuario de la organización sobre su propia API Key.
  - `revoked` es una acción administrativa desde Grupo Santoro sobre las API Keys de sus clientes.
- **Acceso al endpoint**: Solo usuarios con email `@grupo-santoro.com.mx` y permiso `PERM_SETTINGS_MANAGE` o rol `ORG_ADMIN`.

## Estados Computados (Runtime)

Cuando se consulta el estado de las API Keys mediante `GET /api/catalogs/api-keys/status`, el sistema calcula estados adicionales basados en las fechas `expiresAt` y `rotatesAt`:

### 1. `OK`
- La API Key está activa y funcionando correctamente.
- No ha alcanzado la fecha `rotatesAt`.
- No está en la ventana de renovación pronta.

### 2. `RENEW_SOON`
- La API Key todavía funciona, pero se acerca la fecha `rotatesAt`.
- Se activa cuando: `(ahora < rotatesAt) && (tiempo hasta rotatesAt <= renewWindowDays)`
- Por defecto, `renewWindowDays = 7` días.

### 3. `RENEW_REQUIRED`
- La fecha `rotatesAt` ya pasó, pero la API Key aún no ha expirado.
- **Importante**: La API Key **todavía funciona**, pero se emite un warning.
- Se activa cuando: `ahora >= rotatesAt && ahora < expiresAt`

### 4. `EXPIRED`
- La fecha `expiresAt` ha pasado.
- La API Key **no puede ser utilizada**.
- Se activa cuando: `ahora >= expiresAt`

### 5. `DISABLED`
- El campo `status` en la base de datos es `"disabled"`.

**Nota sobre `revoked`**: Las API Keys con estado persistido `revoked` también se mostrarán con estado computado `DISABLED` en el endpoint `/status`, ya que ambos representan API Keys no utilizables. La diferencia está en quién/cómo se llegó a ese estado (ver sección "Panel de Santoro").

## Fechas Importantes

### `expiresAt` (Expiración)
- **Propósito**: Fecha límite de uso de la API Key.
- **Comportamiento**: Si la fecha actual es posterior a `expiresAt`, la API Key se rechaza con código 401.
- **Configuración**: Se establece al crear la API Key mediante el parámetro `ttlDays`:
  ```
  expiresAt = fechaCreación + ttlDays
  ```
- **Valor por defecto**: `null` (sin expiración) o 90 días al renovar.

### `rotatesAt` (Rotación)
- **Propósito**: Fecha sugerida para rotar la API Key por seguridad.
- **Comportamiento**: Si la fecha actual es posterior a `rotatesAt`:
  - La API Key **sigue funcionando** (no se rechaza)
  - Se registra un warning en los logs
  - El flag `requiresRotation` se establece en el contexto del tenant
- **Configuración**: Se establece al crear la API Key mediante el parámetro `rotateDays`:
  ```
  rotatesAt = fechaCreación + rotateDays
  ```
- **Restricción**: `rotateDays` no puede ser mayor que `ttlDays`.

## Validaciones en la Autenticación

Cuando una petición llega con una API Key (header `X-Api-Key`), el filtro `ApiKeyTenantFilter` realiza las siguientes validaciones en orden:

### 1. Verificación de Existencia
```java
apiKeyRepo.findByKeyHashAndStatus(hash, "active")
```
- Busca la API Key por su hash **y** que el estado sea `"active"`.
- Si no existe o el estado no es `active` → `401 Unauthorized` (código: `invalid_api_key`)
- **Importante**: Si la API Key está en estado `disabled`, `rotated`, o `revoked`, NO podrá autenticar peticiones.

### 2. Verificación de Expiración (Bloqueo Duro)
```java
if (key.getExpiresAt() != null && now.isAfter(key.getExpiresAt()))
```
- Si `expiresAt` ha pasado → `401 Unauthorized` (código: `api_key_expired`)
- **La petición se rechaza completamente**.

### 3. Verificación de Rotación (Warning, No Bloquea)
```java
boolean requiresRotation = key.getRotatesAt() != null && now.isAfter(key.getRotatesAt());
```
- Si `rotatesAt` ha pasado:
  - Se registra un warning en logs
  - Se establece `TenantContext.setRequiresRotation(true)`
  - **La petición continúa** (no se rechaza)

### 4. Verificación de Scopes
```java
key.getScopes().stream().anyMatch(s -> s.equals("LOGS_INGEST") || s.equals("INGEST") || s.equals("ALL"))
```
- Verifica que la API Key tenga al menos uno de los scopes requeridos.
- Si no tiene el scope → `401 Unauthorized` (código: `missing_scope_logs_ingest`)

## Operaciones y Transiciones de Estado

### Crear API Key
**Endpoint**: `POST /api/catalogs/api-keys`

**Parámetros**:
```json
{
  "name": "string",
  "systemId": "string",
  "environmentId": "string",
  "scopes": ["LOGS_INGEST"],
  "ttlDays": 90,
  "rotateDays": 30
}
```

**Estado inicial**: `active`

**Retorna**: La API Key en texto plano (solo una vez).

**Opciones de exportación**:
- `?export=txt` → Descarga en formato texto
- `?export=json` → Descarga en formato JSON

---

### Deshabilitar API Key
**Endpoint**: `DELETE /api/catalogs/api-keys/{id}`

**Transición**: `active` → `disabled`

**Efecto**: 
- Cambia el estado a `disabled`.
- La API Key ya no puede autenticar peticiones.

**Importante**: 
- Este endpoint **solo funciona correctamente** si la API Key pertenece a tu organización y está en estado `active`.
- Si la API Key está en estado `revoked`, técnicamente el endpoint podría cambiarla a `disabled`, pero **esto no es recomendado** ya que perdería la traza administrativa de que fue revocada por Santoro.
- Si intentas deshabilitar una API Key `revoked` y no funciona, es porque **solo los administradores de Santoro pueden gestionar API Keys revocadas**.

---

### Renovar API Key
**Endpoint**: `POST /api/catalogs/api-keys/{id}/renew`

**Parámetros**:
```json
{
  "ttlDays": 90,
  "rotateDays": 30
}
```

**Transición posible**: 
- `disabled` → `active`
- `active` (con fechas vencidas) → `active` (con fechas renovadas)

**Restricción**: No se puede renovar una API Key con estado `rotated`.

**Efecto**:
- Cambia el estado a `active`.
- Recalcula `expiresAt = ahora + ttlDays` (por defecto 90 días).
- Recalcula `rotatesAt = ahora + rotateDays` (opcional).

**Nota**: El plaintext de la API Key **no se retorna** (se mantiene el mismo hash).

---

### Rotar API Key
**Endpoint**: `POST /api/catalogs/api-keys/{id}/rotate`

**Transición**: `active` → `rotated` (API Key vieja) + crea nueva con estado `active`

**Efecto**:
1. Marca la API Key anterior con estado `rotated`.
2. Crea una nueva API Key con:
   - Nombre: `{nombre anterior} (rotated)`
   - Mismo `systemId`, `environmentId`, y `scopes`
   - Nuevo hash (nueva clave secreta)
   - Estado: `active`
3. Retorna la nueva API Key en texto plano.

---

### Listar API Keys
**Endpoint**: `GET /api/catalogs/api-keys`

**Parámetros**:
- `status` (opcional): Filtrar por estado (`active`, `disabled`, `rotated`)
- `q` (opcional): Búsqueda por nombre
- `page` (default: 0)
- `size` (default: 25)

**Retorna**: Lista paginada con metadatos de cada API Key (sin el plaintext).

---

### Consultar Estado de API Keys
**Endpoint**: `GET /api/catalogs/api-keys/status`

**Parámetros**:
- `page` (default: 0)
- `size` (default: 25)
- `renewWindowDays` (default: 7): Ventana en días para marcar como `RENEW_SOON`
- `includeDisabled` (default: true): Incluir API Keys deshabilitadas

**Retorna**: Para cada API Key:
```json
{
  "id": "string",
  "name": "string",
  "status": "active|disabled|rotated",
  "createdAt": "timestamp",
  "updateAt": "timestamp",
  "lastUsedAt": "timestamp",
  "rotateAt": "timestamp",
  "expiresAt": "timestamp",
  "state": "OK|RENEW_SOON|RENEW_REQUIRED|EXPIRED|DISABLED",
  "renewRequired": boolean,
  "expired": boolean,
  "secondsToRotate": long,
  "secondsToExpire": long
}
```

## Método Helper: `isActiveNow()`

En el modelo `ApiKey` existe un método helper que determina si la API Key está activa en el momento actual:

```java
public boolean isActiveNow() {
    if (!"active".equalsIgnoreCase(status)) return false;
    if (expiresAt != null && Instant.now().isAfter(expiresAt)) return false;
    if (rotatesAt != null && Instant.now().isAfter(rotatesAt)) return false;
    return true;
}
```

**Nota**: Este método es más estricto que la validación en `ApiKeyTenantFilter`. Considera que la API Key no está activa si `rotatesAt` ha pasado, mientras que el filtro solo emite un warning.

## Diagrama de Estados

```
                              ┌──────────┐
                              │  CREATE  │
                              └────┬─────┘
                                   │
                                   ▼
    ┌──────────────────────┬──────────┬───────────────────────┐
    │                      │          │                       │
    │ DELETE (usuario)     │          │ Panel Santoro         │
    │                      │          │ (admin revoca)        │
    ▼                      │          ▼                       │
┌───────────┐              │     ┌──────────┐                │
│ disabled  │              │     │ revoked  │                │
└─────┬─────┘              │     └────┬─────┘                │
      │                    │          │                      │
      │ RENEW              │          │ Panel Santoro        │
      │                    │          │ (admin reactiva)     │
      │                    │          │                      │
      │                    ▼          ▼                      ▼
      └──────────────────▶┌───────────────┐◀────────────────┘
                          │    active     │
                          └───────┬───────┘
                                  │
                                  │ ROTATE
                                  │
                                  ▼
                            ┌───────────┐
                            │  rotated  │
                            └───────────┘
                                  │
                                  └──────────▶ [No reversible]

Leyenda:
- Estado "disabled": acción del usuario de la organización
- Estado "revoked": acción administrativa de Grupo Santoro
- Estado "rotated": creación de nueva API Key (la anterior queda archivada)
```

## Diagrama de Estados Computados

```
        ACTIVE (status)
             │
    ┌────────┼────────┐
    │        │        │
    ▼        ▼        ▼
┌──────┐ ┌────────┐ ┌────────────┐ ┌─────────┐
│  OK  │→│ RENEW_ │→│   RENEW_   │→│ EXPIRED │
│      │ │  SOON  │ │  REQUIRED  │ │         │
└──────┘ └────────┘ └────────────┘ └─────────┘
             │            │
             └────────────┘
                  │
          (solo advertencia)


        DISABLED o REVOKED (status)
                  │
                  ▼
            ┌──────────┐
            │ DISABLED │
            │ (state)  │
            └──────────┘
            
Nota: En el endpoint /status, tanto "disabled" como "revoked" 
se muestran con estado computado "DISABLED".
```

## Scopes

Las API Keys tienen un campo `scopes` que define qué operaciones pueden realizar. Los scopes reconocidos actualmente son:

- **`LOGS_INGEST`**: Permite enviar logs al sistema.
- **`INGEST`**: Equivalente a `LOGS_INGEST`.
- **`ALL`**: Acceso completo.

**Normalización**: Los scopes se convierten a mayúsculas y se eliminan duplicados.

**Default**: Si no se especifican scopes, se asigna `["LOGS_INGEST"]` por defecto.

## Rate Limiting

Las API Keys están sujetas a rate limiting en dos niveles:

### 1. Rate Limit por API Key
- Limita el número de peticiones por API Key específica.
- Configurable mediante políticas en la base de datos o valores por defecto en el YAML.

### 2. Rate Limit por Tenant
- Limita el número de peticiones por tenant (organización).
- Es un límite agregado para todas las API Keys del tenant.

**Configuración por defecto** (ver `application.yml`):
```yaml
rate-limit:
  enabled: true
  api-key:
    capacity: 100
    refill-tokens: 100
    refill-seconds: 60
  tenant:
    capacity: 500
    refill-tokens: 500
    refill-seconds: 60
```

**Respuesta cuando se excede**:
- Status: `429 Too Many Requests`
- Header: `Retry-After: {segundos}`
- Código: `rate_limited`

## Seguridad

### Almacenamiento de API Keys
- **En Base de Datos**: Se almacena el hash SHA-256 en Base64 del plaintext.
- **Generación**: 32 bytes aleatorios con `SecureRandom`, codificados en Base64 URL-safe.
- **Formato**: `bk_<base64url>` (ej: `bk_K7vN2xQ9...`)

### Transmisión
- Se envía en el header: `X-Api-Key`, `X-API-Key`, o `x-api-key`.
- El plaintext solo se muestra **una vez** al crear o rotar la API Key.
- Después de la creación, no hay forma de recuperar el plaintext.

### Índices en MongoDB
```javascript
// Índice único por hash
{ "key_hash": 1 }  // unique

// Índice compuesto por tenant y estado
{ "tenant_id": 1, "status": 1 }

// Índice único por tenant y nombre
{ "tenant_id": 1, "name": 1 }  // unique
```

## Panel de Santoro: Administración de Organizaciones

El Panel de Santoro es un módulo administrativo especial que permite a Grupo Santoro gestionar todas las organizaciones del sistema.

### Revocación Automática de API Keys

Cuando una organización es **deshabilitada** desde el Panel de Santoro:

1. El estado de la organización cambia a `"disabled"`.
2. **Todas las API Keys activas** de esa organización se marcan automáticamente como `"revoked"`.
3. Las API Keys revocadas **dejan de funcionar inmediatamente** (no pueden autenticar).

**Código relevante** (SantoroPanelService.java:223-229):
```java
if ("disabled".equals(status)) {
    List<ApiKey> keys = apiKeyRep.findByTenantIdOrderByCreatedAtDesc(id);
    keys.stream()
        .filter(k -> "active".equalsIgnoreCase(k.getStatus()))
        .forEach(k -> { k.setStatus("revoked"); apiKeyRep.save(k); });
}
```

### Revocación Manual de API Keys

Un administrador de Santoro puede revocar o reactivar API Keys individuales:

**Endpoint**: `PUT /api/santoro/panel/organizations/{orgId}/api-keys/{keyId}/status`

**Body para revocar**:
```json
{
  "status": "revoked"
}
```

**Body para reactivar**:
```json
{
  "status": "active"
}
```

**Casos de uso**:
- Suspensión temporal por falta de pago
- Incumplimiento de términos de servicio
- Investigación de seguridad
- Límite de uso excedido

### ¿Qué hacer si tienes API Keys en estado `revoked`?

Si eres usuario de una organización y ves API Keys en estado `revoked`:

1. **No puedes cambiar el estado tú mismo** - Solo los administradores de Grupo Santoro pueden revocar/reactivar.
2. **El endpoint `DELETE /api/catalogs/api-keys/{id}` no funciona** con API Keys revocadas (solo cambia `active` a `disabled`).
3. **Contacta con Grupo Santoro** para:
   - Reactivar la API Key (`revoked` → `active`)
   - Entender por qué fue revocada
   - Resolver problemas de facturación o incumplimiento

4. **Alternativa temporal**: Si necesitas urgentemente una API Key:
   - Crea una **nueva** API Key (si tu organización está activa)
   - La nueva API Key tendrá estado `active` y funcionará normalmente
   - Pero si tu organización está deshabilitada, no podrás crear nuevas API Keys

### Diferencias entre estados de bloqueo

| Estado | Quién lo hace | Reversible | Propósito |
|--------|--------------|------------|-----------|
| `disabled` | Usuario de la org | Sí (RENEW) | Desactivación voluntaria |
| `revoked` | Admin de Santoro | Sí (Panel Santoro) | Suspensión administrativa |
| `rotated` | Usuario de la org | No | Reemplazo por seguridad |

## Buenas Prácticas

1. **Establecer fechas de expiración**: Siempre configurar `ttlDays` para limitar la vida de las API Keys.

2. **Configurar rotación**: Usar `rotateDays` (menor que `ttlDays`) para recibir advertencias antes de la expiración.

3. **Monitorear el estado**: Consultar regularmente el endpoint `/api/catalogs/api-keys/status` para detectar API Keys que requieren renovación.

4. **Rotar proactivamente**: No esperar a que la API Key expire. Rotar cuando el estado sea `RENEW_REQUIRED`.

5. **Almacenar el plaintext de forma segura**: Después de crear una API Key, guárdala en un gestor de secretos (ej: AWS Secrets Manager, HashiCorp Vault).

6. **No compartir API Keys**: Cada sistema o servicio debe tener su propia API Key con scopes específicos.

7. **Deshabilitar en lugar de eliminar**: Usar `DELETE` (que deshabilita) en lugar de eliminar de la base de datos, para mantener el historial.

8. **Monitorear `lastUsedAt`**: Identificar API Keys que no se usan y deshabilitarlas.

9. **Revisar API Keys revocadas**: Si tienes API Keys en estado `revoked`, contacta con Grupo Santoro para entender el motivo y resolver el problema.

10. **Mantener la facturación al día**: Para evitar que tu organización sea deshabilitada y tus API Keys revocadas automáticamente.

## Endpoints Relacionados

### Endpoints de Usuario (Gestión Normal)

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/api/catalogs/api-keys` | Crear nueva API Key |
| GET | `/api/catalogs/api-keys` | Listar API Keys |
| GET | `/api/catalogs/api-keys/status` | Consultar estado detallado |
| POST | `/api/catalogs/api-keys/{id}/rotate` | Rotar API Key |
| POST | `/api/catalogs/api-keys/{id}/renew` | Renovar API Key |
| DELETE | `/api/catalogs/api-keys/{id}` | Deshabilitar API Key (cambia a `disabled`) |

### Endpoints del Panel de Santoro (Administración)

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/santoro/panel/stats` | Estadísticas globales del sistema |
| GET | `/api/santoro/panel/organizations` | Listar todas las organizaciones |
| GET | `/api/santoro/panel/organizations/{id}` | Detalle de una organización |
| POST | `/api/santoro/panel/organizations` | Crear nueva organización |
| PUT | `/api/santoro/panel/organizations/{id}/status` | Activar/Deshabilitar organización (revoca API Keys automáticamente) |
| GET | `/api/santoro/panel/organizations/{orgId}/api-keys` | Listar API Keys de una organización |
| PUT | `/api/santoro/panel/organizations/{orgId}/api-keys/{keyId}/status` | Activar/Revocar API Key manualmente |

**Restricción de acceso**: Los endpoints del Panel de Santoro solo pueden ser usados por usuarios con email `@grupo-santoro.com.mx` y permisos adecuados.

## Permisos Requeridos

### Endpoints de Usuario
Todos los endpoints de gestión de API Keys requieren el permiso:
```
PERM_SETTINGS_MANAGE
```

### Endpoints del Panel de Santoro
Requieren **ambas condiciones**:
1. Email del usuario debe terminar en `@grupo-santoro.com.mx`
2. Tener uno de estos permisos:
   - `PERM_SETTINGS_MANAGE`
   - Rol `ORG_ADMIN`

## Preguntas Frecuentes

### ¿Por qué tengo API Keys en estado `revoked`?

Tu organización fue **deshabilitada desde el Panel de Santoro**, lo que automáticamente revocó todas las API Keys activas. Razones comunes:

- Factura impaga o problemas de pago
- Incumplimiento de términos de servicio
- Suspensión temporal por mantenimiento
- Exceso del límite de uso acordado

**Solución**: Contacta con Grupo Santoro para resolver el problema y reactivar tu organización.

---

### ¿Por qué no puedo eliminar (DELETE) una API Key en estado `revoked`?

Las API Keys `revoked` están bajo **control administrativo** de Grupo Santoro. El endpoint `DELETE /api/catalogs/api-keys/{id}`:

- Está diseñado para que **los usuarios gestionen sus propias API Keys**.
- Solo debería usarse con API Keys en estado `active`.
- Si la API Key está `revoked`, solo un administrador de Santoro puede cambiar su estado.

**Solución**: 
- Si quieres "limpiar" la lista, no es necesario eliminarlas - simplemente fíltralas por `status=active` al consultar.
- Si necesitas reactivarlas, contacta con Grupo Santoro.

---

### ¿Puedo crear una nueva API Key si tengo otras en estado `revoked`?

**Depende del estado de tu organización**:

- Si tu organización está `active`: **Sí**, puedes crear nuevas API Keys normalmente.
- Si tu organización está `disabled`: **No**, primero debe ser reactivada desde el Panel de Santoro.

Para verificar el estado de tu organización, contacta con Grupo Santoro.

---

### ¿Cuál es la diferencia entre `revoked` y `disabled`?

| Aspecto | `revoked` | `disabled` |
|---------|-----------|------------|
| **Quién lo hace** | Administrador de Santoro | Usuario de la organización |
| **Endpoint** | Panel de Santoro | `DELETE /api/catalogs/api-keys/{id}` |
| **Motivo** | Decisión administrativa (pago, términos, etc.) | Decisión del usuario (ya no la necesita) |
| **Reversible por usuario** | ❌ No | ✅ Sí (con RENEW) |
| **Reversible por admin** | ✅ Sí | ✅ Sí |
| **Indica problema** | Probablemente sí | No necesariamente |

---

### ¿Qué hago si mi organización fue deshabilitada?

1. **Verifica el estado**: Todas tus API Keys activas aparecerán como `revoked`.
2. **Contacta con Grupo Santoro**: Solicita información sobre el motivo de la deshabilitación.
3. **Resuelve el problema**: Paga facturas pendientes, acepta nuevos términos, etc.
4. **Espera la reactivación**: Un administrador de Santoro:
   - Cambiará el estado de tu organización a `active`.
   - Opcionalmente reactivará tus API Keys (`revoked` → `active`), o puedes crear nuevas.

---

## Referencias

- **Código Fuente (Gestión Normal)**:
  - Modelo: `backlogs.dinamico.model.ingest.ApiKey`
  - Servicio: `backlogs.dinamico.service.catalog.ApiKeyService`
  - Controlador: `backlogs.dinamico.controller.catalog.ApiKeyController`
  - Filtro: `backlogs.dinamico.infra.security.ApiKeyTenantFilter`
  - Repositorio: `backlogs.dinamico.repository.security.ApiKeyRepository`

- **Código Fuente (Panel de Santoro)**:
  - Servicio: `backlogs.dinamico.service.santoro.SantoroPanelService`
  - Controlador: `backlogs.dinamico.controller.santoro.SantoroPanelController`

- **DTOs**:
  - `backlogs.dinamico.api.dto.catalog.ApiKeyView`
  - `backlogs.dinamico.api.dto.catalog.ApiKeyStatusResponse`
  - `backlogs.dinamico.api.dto.santoro.OrgSummaryDto`
  - `backlogs.dinamico.api.dto.santoro.SantoroPanelStatsDto`

- **Documentación Relacionada**:
  - `AGENTS.md` - Información sobre agentes del sistema
  - `PASSWORD_RECOVERY.md` - Recuperación de contraseñas
  - `CORS_AWS_FIX.md` - Configuración CORS en AWS

