# Corrección del Mapa de Dispositivos - Dashboard

## Problema Identificado

En el mapa del Dashboard, bajo la pestaña de "DISPOSITIVOS", se generaban marcadores duplicados para un mismo usuario:
- Aparecía un marcador con caseId vacío o con guion (-)
- Aparecía otro marcador idéntico con el caseId correcto
- El marcador sin caseId rompía el filtro y mostraba registros globales de todos los usuarios

## Causa Raíz

El método `geo()` en `LogDashboardService` estaba:
1. **Agrupando por coordenadas** en lugar de por `caseId` (dispositivo único)
2. **No filtraba logs sin caseId válido**, permitiendo que logs sin dispositivo real crearan puntos en el mapa
3. **No proporcionaba información del dispositivo** en la respuesta (usuario, IP, caseId)

## Solución Implementada

### 1. Filtrado Estricto de Logs Sin Dispositivo Válido

Se agregaron criterios de filtrado en la consulta de agregación:

```java
base = base.and("caseId").exists(true)
        .ne(null)
        .ne("")
        .ne("-")
        .regex("^[A-Z]+[-_]");  // Debe empezar con prefijo del sistema (ej: TV-, TKT-)
```

**Reglas de negocio aplicadas:**
- ✅ El `caseId` debe existir
- ✅ No puede ser `null` ni vacío
- ✅ No puede ser solo un guion "-"
- ✅ Debe empezar con un prefijo del sistema (letras mayúsculas + guion/underscore)

### 2. Corrección del Criterio de Agrupación

**Antes:** Se agrupaba por coordenadas `group("coords")`
```java
Aggregation.group("coords").count().as("count")
```

**Después:** Se agrupa por `caseId` (cada dispositivo es único)
```java
Aggregation.group("caseId")
    .first("usuario").as("usuario")
    .first("coords").as("coordenadas")
    .max("ultimaConexion").as("ultimaConexion")
    .first("ip").as("ip")
```

**Beneficios:**
- 🎯 Un dispositivo = un marcador único en el mapa
- 🎯 Se toma la última posición GPS del dispositivo (último log)
- 🎯 Evita duplicados por nombre de usuario

### 3. Enriquecimiento del DTO de Respuesta

Se agregaron campos adicionales a `DashboardGeoDto.GeoPoint`:

```java
public static class GeoPoint {
    private double lon;
    private double lat;
    private long count;
    private String caseId;      // Identificador único del dispositivo
    private String usuario;     // Nombre del usuario/actor del último log
    private String ip;          // IP del dispositivo
}
```

**Ventajas:**
- ℹ️ El frontend puede mostrar información del dispositivo en el tooltip del marcador
- ℹ️ Se puede hacer clic en el marcador y filtrar correctamente por `caseId`
- ℹ️ Mejor trazabilidad y debugging

## Pipeline de Agregación MongoDB

```javascript
[
  // 1. Filtrar solo logs con coordenadas y caseId válido
  { $match: { 
      "geo.coordinates": { $exists: true },
      "caseId": { 
        $exists: true, 
        $ne: null, 
        $ne: "", 
        $ne: "-",
        $regex: /^[A-Z]+[-_]/
      }
    }
  },
  
  // 2. Ordenar por tiempo descendente (más reciente primero)
  { $sort: { "eventTime": -1 } },
  
  // 3. Proyectar campos relevantes
  { $project: {
      "caseId": 1,
      "coords": "$geo.coordinates",
      "usuario": "$actor.fullName",
      "ultimaConexion": "$eventTime",
      "ip": "$meta.ip"
    }
  },
  
  // 4. Agrupar por caseId (dispositivo único)
  { $group: {
      "_id": "$caseId",
      "usuario": { $first: "$usuario" },
      "coordenadas": { $first: "$coords" },
      "ultimaConexion": { $max: "$ultimaConexion" },
      "ip": { $first: "$ip" }
    }
  },
  
  // 5. Ordenar por última conexión
  { $sort: { "ultimaConexion": -1 } },
  
  // 6. Limitar a 2000 puntos
  { $limit: 2000 }
]
```

## Archivos Modificados

### 1. `LogDashboardService.java`
**Ruta:** `src/main/java/backlogs/dinamico/service/logs/LogDashboardService.java`

**Método modificado:** `geo(Authentication auth, String system, Instant from, Instant to)`

**Cambios:**
- Agregado filtro estricto de `caseId` válido
- Cambiado agrupación de `coords` a `caseId`
- Agregado ordenamiento por `eventTime DESC`
- Agregada proyección de campos del dispositivo
- Agregado uso de `$first` y `$max` para obtener el último estado
- Modificado mapeo de resultados para incluir `caseId`, `usuario` e `ip`

### 2. `DashboardGeoDto.java`
**Ruta:** `src/main/java/backlogs/dinamico/api/dto/logs/DashboardGeoDto.java`

**Clase modificada:** `GeoPoint` (nested class)

**Campos agregados:**
```java
private String caseId;      // Identificador único del dispositivo
private String usuario;     // Nombre del usuario/actor del último log
private String ip;          // IP del dispositivo
```

## Testing

### Casos de prueba recomendados:

1. **Logs sin caseId válido:**
   ```json
   { "caseId": null }          // ❌ No debe aparecer en el mapa
   { "caseId": "" }            // ❌ No debe aparecer en el mapa
   { "caseId": "-" }           // ❌ No debe aparecer en el mapa
   { "caseId": "123" }         // ❌ No tiene prefijo válido
   { "caseId": "TV-001" }      // ✅ Válido
   ```

2. **Múltiples logs del mismo dispositivo:**
   ```json
   // Dispositivo TV-001 reporta desde 3 ubicaciones diferentes
   { "caseId": "TV-001", "geo": [-99.1, 19.4], "eventTime": "2026-06-01T10:00:00Z" }
   { "caseId": "TV-001", "geo": [-99.2, 19.5], "eventTime": "2026-06-02T12:00:00Z" }
   { "caseId": "TV-001", "geo": [-99.3, 19.6], "eventTime": "2026-06-03T14:00:00Z" }
   
   // ✅ Solo debe aparecer 1 marcador con las coordenadas del log más reciente
   // Resultado: lon: -99.3, lat: 19.6
   ```

3. **Dispositivos válidos con información completa:**
   ```json
   {
     "caseId": "TV-001",
     "actor": { "fullName": "Juan Pérez" },
     "meta": { "ip": "192.168.1.100" },
     "geo": { "coordinates": [-99.1332, 19.4326] }
   }
   
   // ✅ El marcador debe incluir: caseId, usuario, ip, coordenadas
   ```

## Impacto en el Frontend

El frontend ahora recibirá objetos `GeoPoint` con la siguiente estructura:

```json
{
  "total": 150,
  "points": [
    {
      "lon": -99.1332,
      "lat": 19.4326,
      "count": 1,
      "caseId": "TV-12345",
      "usuario": "Juan Pérez Gómez",
      "ip": "192.168.100.8"
    },
    ...
  ]
}
```

**Cambios requeridos en el frontend:**
- ✅ **NO es breaking change** - Los campos `lon`, `lat`, `count` se mantienen
- ✅ Campos nuevos (`caseId`, `usuario`, `ip`) son opcionales
- ✅ Se puede mostrar información del dispositivo en tooltips/popups del marcador
- ✅ Al hacer clic en un marcador, usar `caseId` para filtrar (evita el bug de mostrar datos globales)

## Validación del Fix

### Antes:
```
GET /api/logs/dashboard/geo?system=TRUSTVALUE
Respuesta:
{
  "total": 300,
  "points": [
    { "lon": -99.1, "lat": 19.4, "count": 45 },    // ¿Cuál dispositivo?
    { "lon": -99.1, "lat": 19.4, "count": 12 },    // ¿Duplicado?
    ...
  ]
}
```

### Después:
```
GET /api/logs/dashboard/geo?system=TRUSTVALUE
Respuesta:
{
  "total": 150,
  "points": [
    { 
      "lon": -99.1332, 
      "lat": 19.4326, 
      "count": 1,
      "caseId": "TV-12345",
      "usuario": "Juan Pérez",
      "ip": "192.168.100.8"
    },
    ...
  ]
}
```

**Verificación:**
- ✅ `total` debe ser igual al número de dispositivos únicos (no suma de logs)
- ✅ No debe haber marcadores duplicados en las mismas coordenadas
- ✅ Cada `caseId` debe aparecer solo una vez
- ✅ Las coordenadas corresponden al último log de cada dispositivo

## Endpoint Afectado

```http
GET /api/logs/dashboard/geo
Authorization: Bearer <jwt>
```

**Parámetros (opcionales):**
- `system`: Filtrar por sistema
- `from`: Fecha inicio (ISO 8601)
- `to`: Fecha fin (ISO 8601)

**Scope de seguridad:**
- Aplica RBAC del usuario autenticado
- Solo muestra dispositivos de los sistemas permitidos
- Respeta `LogFilterCriteria` del usuario

## Fecha de Implementación

**Fecha:** 2026-06-04  
**Versión:** 0.0.1-SNAPSHOT  
**Autor:** GitHub Copilot  
**Compilación:** ✅ Exitosa

---

## Notas Adicionales

### Prefijos de Sistema Válidos

El regex `^[A-Z]+[-_]` acepta cualquier prefijo que:
- Empiece con letras mayúsculas
- Seguido de guion `-` o underscore `_`

**Ejemplos válidos:**
- `TV-12345` (TrustValue)
- `TKT-001` (Tickets)
- `ACC_999` (Access Control)
- `HID-001` (Biometric)

**Ejemplos inválidos:**
- `123` (sin prefijo)
- `-001` (empieza con guion)
- `tv-001` (minúsculas)
- `null`, `""`, `"-"` (valores especiales)

### Rendimiento

- La agregación usa índices existentes de MongoDB
- Límite de 2000 puntos evita sobrecarga del navegador
- El ordenamiento por `eventTime DESC` aprovecha el índice temporal
- `$first` y `$max` son operadores eficientes en pipelines

### Compatibilidad

- ✅ Compatible con versión anterior (campos nuevos son opcionales)
- ✅ No requiere migración de datos
- ✅ No requiere actualización del frontend (pero se recomienda para aprovechar los nuevos campos)

