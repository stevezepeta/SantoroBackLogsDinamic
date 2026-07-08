# ✅ RESUMEN DE CORRECCIONES APLICADAS - AMBIENTE QUINTANA ROO (Puerto 8057)

## 📋 Cambios Implementados

### 1. ✅ Corrección de Conexión a Base de Datos

**Problema:** El ambiente 8057 apuntaba a la base de datos `logs_system` del ambiente 8040, causando que los correos de recuperación de contraseña buscaran usuarios en la BD incorrecta.

**Solución Aplicada:**
- ✅ Cambiada la base de datos por defecto de `logs_system` a `logs_quintanaroo`
- ✅ Configurada mediante variable de entorno `MONGODB_URI`
- ✅ Actualizado en todos los perfiles (application.yml, application-dev.yml, application-dev.properties)

**Archivos modificados:**
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-dev.properties`

**Variables de entorno:**
```bash
MONGODB_URI=mongodb://localhost:27017/logs_quintanaroo
MULTITENANT_BASE_DATABASE=logs_quintanaroo
```

---

### 2. ✅ Dinamización de WebSocket

**Problema:** URLs fijas de WebSocket causaban fallas de autenticación por WebSockets en el nuevo ambiente.

**Solución Aplicada:**
- ✅ WebSocket ahora configurable mediante variable de entorno
- ✅ Soporta múltiples orígenes separados por comas
- ✅ Por defecto permite todos los orígenes (*) para facilitar desarrollo

**Archivos modificados:**
- `src/main/java/backlogs/dinamico/infra/ws/WebSocketConfig.java`
- `src/main/resources/application.properties`

**Variable de entorno:**
```bash
# Desarrollo/Testing - Permitir todos los orígenes
WEBSOCKET_ALLOWED_ORIGINS=*

# Producción - Especificar orígenes exactos (RECOMENDADO)
WEBSOCKET_ALLOWED_ORIGINS=https://dashboard-quintanaroo.grupo-santoro.com.mx,https://localhost:3000
```

---

### 3. ✅ Verificación de Credenciales de Correo

**Problema:** Configuración estática de correo no permitía independizar el ambiente.

**Solución Aplicada:**
- ✅ Todas las propiedades de correo ahora configurables por variables de entorno
- ✅ Host, puerto, credenciales y remitente totalmente dinámicos
- ✅ Nombre del remitente cambiado a "Backlogs Santoro - Quintana Roo"

**Archivos modificados:**
- `src/main/resources/application.properties`

**Variables de entorno:**
```bash
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=soporte.tecnico@grupo-santoro.com.mx
MAIL_PASSWORD=wnnkjroexgypcpss
MAIL_FROM=soporte.tecnico@grupo-santoro.com.mx
MAIL_FROM_NAME=Backlogs Santoro - Quintana Roo
```

---

## 📦 Archivos Creados

### Scripts de Inicio
1. **start-quintanaroo.ps1** - Script PowerShell para iniciar el servidor con todas las variables configuradas
2. **.env.quintanaroo.example** - Archivo de ejemplo con todas las variables de entorno

### Documentación
1. **docs/QUINTANA_ROO_SETUP.md** - Documentación completa del setup
2. **QUINTANA_ROO_README.md** - Guía rápida de inicio

---

## 🚀 Cómo Usar

### Opción 1: Script Automatizado (Recomendado)
```powershell
.\start-quintanaroo.ps1
```

### Opción 2: Manual
```powershell
# Configurar variables
$env:SERVER_PORT = "8057"
$env:MONGODB_URI = "mongodb://localhost:27017/logs_quintanaroo"
$env:MULTITENANT_BASE_DATABASE = "logs_quintanaroo"
$env:WEBSOCKET_ALLOWED_ORIGINS = "*"
$env:SPRING_PROFILES_ACTIVE = "prod"

# Ejecutar
java -jar target\dinamico-0.0.1-SNAPSHOT.jar
```

---

## ✔️ Verificaciones Post-Despliegue

### 1. Verificar Base de Datos Correcta
```javascript
// En mongosh
use logs_quintanaroo
db.users.countDocuments()
```

### 2. Verificar WebSocket
```javascript
// En la consola del navegador
const ws = new WebSocket('ws://localhost:8057/ws');
ws.onopen = () => console.log('✅ Conectado');
```

### 3. Verificar Correo
```powershell
# Verificar conectividad SMTP
Test-NetConnection -ComputerName smtp.gmail.com -Port 587
```

### 4. Probar Recuperación de Contraseña
- Intentar recuperar contraseña desde el frontend
- Verificar que el correo llegue correctamente
- Verificar en logs que no hay errores de SMTP

---

## 🎯 Estado del Ambiente

| Componente | Estado | Detalles |
|------------|--------|----------|
| Base de Datos | ✅ Independizada | logs_quintanaroo |
| WebSocket | ✅ Configurado | Origins dinámicos |
| Correo | ✅ Configurado | Credenciales externalizadas |
| Compilación | ✅ Exitosa | JAR generado |
| Puerto | ✅ Configurable | 8057 (por defecto) |

---

## ⚠️ IMPORTANTE - Antes de Desplegar

1. **Crear la base de datos logs_quintanaroo si no existe:**
   ```javascript
   use logs_quintanaroo
   ```

2. **Migrar usuarios de Quintana Roo (si es necesario):**
   ```javascript
   use logs_system
   const usuarios = db.users.find({estado: "Quintana Roo"}).toArray()
   use logs_quintanaroo
   db.users.insertMany(usuarios)
   ```

3. **Verificar conectividad de red:**
   - MongoDB: puerto 27017
   - SMTP: puerto 587
   - WebSocket: asegurarse de que el firewall permite conexiones

4. **En producción, cambiar WEBSOCKET_ALLOWED_ORIGINS:**
   ```bash
   WEBSOCKET_ALLOWED_ORIGINS=https://dashboard-quintanaroo.grupo-santoro.com.mx
   ```

---

## 🐛 Troubleshooting Rápido

| Problema | Solución |
|----------|----------|
| WebSocket no conecta | Verificar `WEBSOCKET_ALLOWED_ORIGINS=*` |
| Correos no se envían | Verificar puerto 587 abierto |
| Usuario no encontrado | Verificar que apunta a `logs_quintanaroo` |
| Puerto en uso | Cambiar `SERVER_PORT` |

---

## 📞 Soporte

Para más detalles, consultar:
- **Documentación completa:** `docs/QUINTANA_ROO_SETUP.md`
- **Guía rápida:** `QUINTANA_ROO_README.md`
- **Soporte técnico:** soporte.tecnico@grupo-santoro.com.mx

---

**Fecha de implementación:** 2026-06-05  
**Ambiente:** Quintana Roo - Puerto 8057  
**Estado:** ✅ Listo para despliegue

