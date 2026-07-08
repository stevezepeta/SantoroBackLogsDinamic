# Guía Rápida de Verificación - Fix Marcadores Fantasma

**Fecha:** 2026-07-08  
**Estado:** ✅ Implementado

## ⚡ Cambios Aplicados

Se aplicaron filtros de integridad estrictos en:

### 1. LogDashboardService.geo()
```java
// Líneas 271-288
base = base.and("caseId")
        .exists(true)
        .ne(null)
        .ne("")
        .ne("-")
        .regex("^[A-Z]+[-_][A-Za-z0-9]")  // Prefijo válido
        .regex("^.{5,}$");  // Longitud >= 5
```

### 2. DeviceRegistryService
- **upsertFromLog()**: Rechaza deviceId inválidos al registrar
- **getAllDevices()**: Filtra deviceId inválidos en queries

## 🧪 Verificación Inmediata

### En el Backend
```powershell
# 1. Compilar (opcional si no hay cambios pendientes)
cd C:\WorkSpace\Santoro\BackLogs\SantoroBackLogsDinamic
./mvnw clean compile -DskipTests

# 2. Reiniciar servidor
Stop-Process -Name java -Force -ErrorAction SilentlyContinue
./iniciar-backend-principal.ps1
```

### En el Frontend (Browser)

1. **Abrir el dashboard** → Sección "DISPOSITIVOS"

2. **Verificar el mapa:**
   - ✅ NO deben aparecer marcadores con nombre "-" o vacío
   - ✅ Todos los marcadores deben tener formato válido (ej: TV-12345)
   - ✅ No deben haber marcadores duplicados en la misma ubicación

3. **Probar filtrado:**
   - Hacer clic en un marcador válido (ej: "Juan Pérez - TV-12345")
   - ✅ La bitácora debe mostrar SOLO logs de ese dispositivo específico
   - ❌ NO debe mostrar logs de otros usuarios/dispositivos

4. **Consola del navegador (F12):**
   - ✅ NO debe haber errores 403 Forbidden
   - ✅ NO debe haber errores de WebSocket

## 📊 Endpoint Afectado

```http
GET /api/logs/dashboard/geo?system=TRUSTVALUE
Authorization: Bearer <token>
```

**Respuesta esperada:**
```json
{
  "total": 150,  // Número de dispositivos únicos (NO suma de logs)
  "points": [
    {
      "caseId": "TV-12345",     // Siempre presente y válido
      "lon": -99.1332,
      "lat": 19.4326,
      "count": 1,
      "usuario": "Juan Pérez",
      "ip": "192.168.100.8"
    }
  ]
}
```

## 🔍 Casos de Prueba

| caseId | ¿Aparece en mapa? | Motivo |
|--------|-------------------|--------|
| `null` | ❌ NO | Es null |
| `""` | ❌ NO | String vacío |
| `"-"` | ❌ NO | Solo guion |
| `"1"` | ❌ NO | Longitud < 5 |
| `"ab"` | ❌ NO | Longitud < 5 |
| `"test"` | ❌ NO | Sin prefijo mayúsculas |
| `"tv-001"` | ❌ NO | Prefijo en minúsculas |
| `"TV-12345"` | ✅ SÍ | Formato válido |
| `"TKT-001"` | ✅ SÍ | Formato válido |
| `"ACC_999"` | ✅ SÍ | Formato válido |

## ⚠️ Si el Problema Persiste

1. **Verificar que el Backend se reinició:**
   ```powershell
   Get-Process java | Where-Object { $_.Path -like "*SantoroBackLogsDinamic*" }
   ```

2. **Verificar logs del servidor:**
   Buscar líneas como:
   ```
   [Dashboard Geo] Mapa generado con 150 dispositivos únicos
   ```

3. **Limpiar caché del navegador:**
   - Ctrl+Shift+Delete
   - Borrar caché y cookies
   - Recargar (F5)

4. **Verificar endpoint directamente:**
   ```bash
   curl -X GET "http://localhost:8040/api/logs/dashboard/geo?system=TRUSTVALUE" \
        -H "Authorization: Bearer <token>"
   ```
   - Verificar que TODOS los puntos tengan `caseId` válido
   - Verificar que ningún `caseId` sea `"-"`, `""`, o menor a 5 caracteres

## 📚 Documentación Completa

Ver: [DEVICE_MAP_INTEGRITY_FIX.md](./DEVICE_MAP_INTEGRITY_FIX.md)

---

**¿Dudas o problemas?** Revisar logs del servidor con nivel DEBUG activado.

