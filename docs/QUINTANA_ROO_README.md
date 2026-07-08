# Inicio Rápido - Ambiente Quintana Roo (Puerto 8057)

## Para iniciar el servidor en Windows

### Opción 1: Usando el script automatizado (Recomendado)
```powershell
.\start-quintanaroo.ps1
```

### Opción 2: Manual
```powershell
# Configurar variables de entorno
$env:SERVER_PORT = "8057"
$env:MONGODB_URI = "mongodb://localhost:27017/logs_quintanaroo"
$env:MULTITENANT_BASE_DATABASE = "logs_quintanaroo"
$env:WEBSOCKET_ALLOWED_ORIGINS = "*"
$env:SPRING_PROFILES_ACTIVE = "prod"

# Ejecutar
java -jar target\dinamico-0.0.1-SNAPSHOT.jar
```

## URLs importantes

- **API REST:** http://localhost:8057
- **Swagger UI:** http://localhost:8057/swagger-ui.html
- **WebSocket:** ws://localhost:8057/ws

## Verificación rápida

### 1. Base de datos correcta
```javascript
// En mongosh
use logs_quintanaroo
db.users.countDocuments()
```

### 2. WebSocket funcionando
Desde la consola del navegador:
```javascript
const ws = new WebSocket('ws://localhost:8057/ws');
ws.onopen = () => console.log('✅ Conectado');
ws.onerror = (e) => console.error('❌ Error:', e);
```

### 3. Correo electrónico
```powershell
# Verificar conectividad SMTP
Test-NetConnection -ComputerName smtp.gmail.com -Port 587
```

## Solución de problemas

| Problema | Solución |
|----------|----------|
| WebSocket no conecta | Verificar que `WEBSOCKET_ALLOWED_ORIGINS=*` |
| Correos no se envían | Verificar conectividad al puerto 587 |
| Usuario no encontrado | Verificar base de datos `logs_quintanaroo` |
| Puerto en uso | Cambiar `SERVER_PORT` a otro puerto |

## Documentación completa

Ver: `docs/QUINTANA_ROO_SETUP.md`

## Cambios realizados

✅ Base de datos cambiada a `logs_quintanaroo`  
✅ WebSocket configurado dinámicamente  
✅ Configuración de correo independiente  
✅ Todas las variables externalizadas  

---
**Última actualización:** 2026-06-05

