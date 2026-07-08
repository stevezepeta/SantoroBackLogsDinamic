# Configuración del Ambiente de Quintana Roo (Puerto 8057)

## Resumen de Cambios

Este documento describe las modificaciones realizadas para independizar completamente el ambiente de logs de Quintana Roo del ambiente principal (puerto 8040).

## 1. Conexión a Base de Datos MongoDB

### Base de Datos Independiente
Se ha cambiado la base de datos por defecto de `logs_system` a `logs_quintanaroo` para evitar colisiones con el ambiente principal.

**Archivos modificados:**
- `application.yml`
- `application-dev.yml`
- `application-dev.properties`

### Variable de Entorno
```bash
# Para configurar la base de datos de Quintana Roo
MONGODB_URI=mongodb://localhost:27017/logs_quintanaroo
MULTITENANT_BASE_DATABASE=logs_quintanaroo
```

**Importante:** Asegúrate de que la base de datos `logs_quintanaroo` existe y contiene los usuarios activos de Quintana Roo.

## 2. Configuración de WebSocket

### Orígenes Permitidos Dinámicos
El WebSocket ahora es completamente configurable mediante variables de entorno.

### Variable de Entorno
```bash
# Permitir todos los orígenes (desarrollo/temporal)
WEBSOCKET_ALLOWED_ORIGINS=*

# O especificar orígenes específicos (producción - recomendado)
WEBSOCKET_ALLOWED_ORIGINS=https://dashboard-quintanaroo.grupo-santoro.com.mx,https://localhost:3000
```

**Archivo modificado:** `WebSocketConfig.java`

### Endpoint WebSocket
- URL: `ws://<servidor>:8057/ws` o `wss://<servidor>:8057/ws` (con SSL)
- Prefijo de aplicación: `/app`
- Prefijo de topic: `/topic`

## 3. Configuración de Correo Electrónico

### Credenciales Configurables
Todas las propiedades de correo ahora son configurables mediante variables de entorno.

### Variables de Entorno
```bash
# Host SMTP
MAIL_HOST=smtp.gmail.com

# Puerto SMTP
MAIL_PORT=587

# Usuario de correo
MAIL_USERNAME=soporte.tecnico@grupo-santoro.com.mx

# Contraseña de aplicación de Gmail
MAIL_PASSWORD=wnnkjroexgypcpss

# Correo remitente
MAIL_FROM=soporte.tecnico@grupo-santoro.com.mx

# Nombre del remitente
MAIL_FROM_NAME=Backlogs Santoro - Quintana Roo
```

### Verificación de Red
**IMPORTANTE:** Asegúrate de que el servidor físico permite salida de red por los puertos:
- Puerto 587 (STARTTLS) - Recomendado
- Puerto 465 (SSL)
- Puerto 25 (Sin cifrado - NO recomendado)

Puedes verificarlo con:
```powershell
# Verificar conectividad al servidor SMTP
Test-NetConnection -ComputerName smtp.gmail.com -Port 587
```

## 4. Variables de Entorno Completas para el Puerto 8057

### Script de Configuración (Windows - PowerShell)

Crea un archivo `start-quintanaroo.ps1`:

```powershell
# Configuración del Ambiente Quintana Roo - Puerto 8057

# Puerto del servidor
$env:SERVER_PORT="8057"

# Base de datos MongoDB
$env:MONGODB_URI="mongodb://localhost:27017/logs_quintanaroo"
$env:MULTITENANT_BASE_DATABASE="logs_quintanaroo"

# WebSocket - Permitir todos los orígenes (temporal)
# CAMBIAR en producción por el dominio específico
$env:WEBSOCKET_ALLOWED_ORIGINS="*"

# Configuración de Correo
$env:MAIL_HOST="smtp.gmail.com"
$env:MAIL_PORT="587"
$env:MAIL_USERNAME="soporte.tecnico@grupo-santoro.com.mx"
$env:MAIL_PASSWORD="wnnkjroexgypcpss"
$env:MAIL_FROM="soporte.tecnico@grupo-santoro.com.mx"
$env:MAIL_FROM_NAME="Backlogs Santoro - Quintana Roo"

# Perfil activo
$env:SPRING_PROFILES_ACTIVE="prod"

# Ejecutar la aplicación
java -jar target\dinamico-0.0.1-SNAPSHOT.jar
```

### Script de Configuración (Linux/Mac - Bash)

Crea un archivo `start-quintanaroo.sh`:

```bash
#!/bin/bash
# Configuración del Ambiente Quintana Roo - Puerto 8057

# Puerto del servidor
export SERVER_PORT=8057

# Base de datos MongoDB
export MONGODB_URI="mongodb://localhost:27017/logs_quintanaroo"
export MULTITENANT_BASE_DATABASE="logs_quintanaroo"

# WebSocket - Permitir todos los orígenes (temporal)
# CAMBIAR en producción por el dominio específico
export WEBSOCKET_ALLOWED_ORIGINS="*"

# Configuración de Correo
export MAIL_HOST="smtp.gmail.com"
export MAIL_PORT="587"
export MAIL_USERNAME="soporte.tecnico@grupo-santoro.com.mx"
export MAIL_PASSWORD="wnnkjroexgypcpss"
export MAIL_FROM="soporte.tecnico@grupo-santoro.com.mx"
export MAIL_FROM_NAME="Backlogs Santoro - Quintana Roo"

# Perfil activo
export SPRING_PROFILES_ACTIVE="prod"

# Ejecutar la aplicación
java -jar target/dinamico-0.0.1-SNAPSHOT.jar
```

## 5. Verificación Post-Despliegue

### 5.1 Verificar Conexión a MongoDB
```bash
# Conectarse a la base de datos y verificar usuarios
mongosh mongodb://localhost:27017/logs_quintanaroo

# En el shell de MongoDB:
db.users.countDocuments()
db.users.find().limit(5)
```

### 5.2 Verificar WebSocket
Desde la consola del navegador (Frontend):
```javascript
const socket = new WebSocket('ws://servidor:8057/ws');
socket.onopen = () => console.log('WebSocket conectado');
socket.onerror = (err) => console.error('Error WebSocket:', err);
```

### 5.3 Verificar Envío de Correos
- Intentar recuperar contraseña desde el frontend
- Revisar los logs del servidor para ver si hay errores de conexión SMTP
- Verificar que el correo llegue a la bandeja de entrada

### 5.4 Verificar Logs del Servidor
```bash
# Ver los logs para detectar cualquier error
tail -f logs/application.log

# O si estás usando la salida estándar:
# Los logs aparecerán en la consola donde ejecutaste java -jar
```

## 6. Troubleshooting

### Problema: WebSocket no conecta
**Solución:**
1. Verificar que `WEBSOCKET_ALLOWED_ORIGINS=*`
2. Revisar logs del servidor para errores CORS
3. Verificar que el frontend esté apuntando al puerto correcto (8057)
4. Si usas proxy/nginx, verificar configuración de WebSocket

### Problema: Correos no se envían
**Solución:**
1. Verificar conectividad: `Test-NetConnection -ComputerName smtp.gmail.com -Port 587`
2. Verificar que la contraseña de aplicación de Gmail sea correcta
3. Revisar los logs del servidor para errores SMTP
4. Verificar firewall del servidor

### Problema: Usuario no encontrado
**Solución:**
1. Verificar que estás apuntando a la base de datos correcta
2. Verificar que los usuarios existen en `logs_quintanaroo`:
   ```bash
   mongosh mongodb://localhost:27017/logs_quintanaroo
   db.users.find({email: "usuario@ejemplo.com"})
   ```
3. Si es necesario, migrar usuarios de `logs_system` a `logs_quintanaroo`

## 7. Migración de Usuarios (Si es necesario)

Si necesitas copiar usuarios de la base de datos principal a Quintana Roo:

```javascript
// En mongosh, conectado a logs_system
use logs_system
const usuarios = db.users.find({estado: "Quintana Roo"}).toArray()

// Cambiar a la base de datos de Quintana Roo
use logs_quintanaroo
db.users.insertMany(usuarios)
```

## 8. Recomendaciones de Producción

1. **WebSocket Origins:** En producción, cambiar `WEBSOCKET_ALLOWED_ORIGINS=*` por el dominio específico
2. **Contraseñas:** Usar secretos seguros para `MAIL_PASSWORD` y `JWT_SECRET`
3. **Monitoreo:** Configurar logs y alertas para detectar problemas de conexión
4. **Backup:** Realizar backups regulares de la base de datos `logs_quintanaroo`
5. **SSL/TLS:** Configurar certificados SSL para WebSocket seguro (wss://)

## Contacto
Para soporte técnico, contactar a: soporte.tecnico@grupo-santoro.com.mx

