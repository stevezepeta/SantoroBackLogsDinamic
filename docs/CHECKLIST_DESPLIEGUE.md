# ✅ CHECKLIST DE DESPLIEGUE - AMBIENTE QUINTANA ROO (Puerto 8057)

## Pre-requisitos

### 1. Base de Datos MongoDB
- [ ] MongoDB está ejecutándose en el servidor
- [ ] La base de datos `logs_quintanaroo` existe
- [ ] Los usuarios de Quintana Roo están migrados a `logs_quintanaroo`
- [ ] Se puede conectar a `mongodb://localhost:27017/logs_quintanaroo`

**Verificación:**
```bash
mongosh mongodb://localhost:27017/logs_quintanaroo
db.users.countDocuments()
# Debe mostrar el número de usuarios de Quintana Roo
```

---

### 2. Conectividad de Red
- [ ] Puerto 8057 disponible (no usado por otra aplicación)
- [ ] Puerto 27017 (MongoDB) accesible
- [ ] Puerto 587 (SMTP) tiene salida autorizada
- [ ] Firewall permite conexiones WebSocket

**Verificación:**
```powershell
# Verificar MongoDB
Test-NetConnection -ComputerName localhost -Port 27017

# Verificar SMTP
Test-NetConnection -ComputerName smtp.gmail.com -Port 587

# Verificar puerto 8057 disponible
Get-NetTCPConnection -LocalPort 8057 -ErrorAction SilentlyContinue
# No debe mostrar ninguna conexión activa
```

---

### 3. Archivo JAR Generado
- [ ] Compilación exitosa
- [ ] JAR existe en `target/dinamico-0.0.1-SNAPSHOT.jar`
- [ ] Tamaño del JAR > 50 MB (debe incluir todas las dependencias)

**Verificación:**
```powershell
Test-Path .\target\dinamico-0.0.1-SNAPSHOT.jar
(Get-Item .\target\dinamico-0.0.1-SNAPSHOT.jar).Length / 1MB
# Debe mostrar un número > 50
```

---

## Variables de Entorno Configuradas

### Variables Críticas
- [ ] `SERVER_PORT=8057`
- [ ] `MONGODB_URI=mongodb://localhost:27017/logs_quintanaroo`
- [ ] `MULTITENANT_BASE_DATABASE=logs_quintanaroo`
- [ ] `WEBSOCKET_ALLOWED_ORIGINS=*` (cambiar en producción)
- [ ] `SPRING_PROFILES_ACTIVE=prod`

### Variables de Correo
- [ ] `MAIL_HOST=smtp.gmail.com`
- [ ] `MAIL_PORT=587`
- [ ] `MAIL_USERNAME` configurado
- [ ] `MAIL_PASSWORD` configurado
- [ ] `MAIL_FROM` configurado

**Verificación:**
```powershell
$env:SERVER_PORT
$env:MONGODB_URI
$env:MULTITENANT_BASE_DATABASE
# Cada comando debe mostrar el valor configurado
```

---

## Despliegue

### 1. Detener Procesos Previos
- [ ] No hay otra instancia de Java escuchando en el puerto 8057
- [ ] No hay procesos Java con el JAR antiguo

**Verificación:**
```powershell
# Ver procesos Java activos
Get-Process -Name java -ErrorAction SilentlyContinue

# Ver qué está usando el puerto 8057
Get-NetTCPConnection -LocalPort 8057 -ErrorAction SilentlyContinue
```

---

### 2. Iniciar el Servidor
- [ ] Ejecutar `.\start-quintanaroo.ps1` O configurar variables manualmente
- [ ] El servidor inicia sin errores
- [ ] Se muestra el banner de Spring Boot
- [ ] Los logs muestran "Started BacklogsApplication"

**Logs esperados:**
```
Tomcat started on port 8057
Started BacklogsApplication in X.XXX seconds
```

---

### 3. Verificación de Conexión a Base de Datos
- [ ] Los logs muestran conexión exitosa a MongoDB
- [ ] No hay errores de "MongoTimeoutException"
- [ ] No hay errores de autenticación a MongoDB

**Buscar en logs:**
```
MongoDB initialization
```

---

## Pruebas Post-Despliegue

### 1. Health Check
- [ ] La aplicación responde en `http://localhost:8057`
- [ ] Swagger UI carga en `http://localhost:8057/swagger-ui.html`

**Verificación:**
```powershell
Invoke-WebRequest -Uri http://localhost:8057/swagger-ui.html -UseBasicParsing
# Debe retornar status 200 OK
```

---

### 2. WebSocket
- [ ] El endpoint WebSocket está activo
- [ ] Se puede conectar desde el frontend
- [ ] No hay errores CORS en la consola del navegador

**Verificación (Consola del navegador):**
```javascript
const ws = new WebSocket('ws://localhost:8057/ws');
ws.onopen = () => console.log('✅ WebSocket conectado');
ws.onerror = (e) => console.error('❌ Error WebSocket:', e);
ws.onclose = () => console.log('🔌 WebSocket cerrado');
```

---

### 3. Autenticación
- [ ] Login de usuarios funciona correctamente
- [ ] Los usuarios se buscan en `logs_quintanaroo`
- [ ] JWT se genera correctamente

**Verificación:**
```bash
# Desde el frontend o Postman
POST http://localhost:8057/api/auth/login
{
  "email": "usuario@ejemplo.com",
  "password": "contraseña"
}
```

---

### 4. Recuperación de Contraseña
- [ ] Se puede solicitar recuperación de contraseña
- [ ] El correo se envía correctamente
- [ ] El token es válido
- [ ] Los logs no muestran errores SMTP

**Verificación:**
```bash
# Desde el frontend o Postman
POST http://localhost:8057/api/auth/password-reset/request
{
  "email": "usuario@ejemplo.com"
}
```

**Verificar en logs:**
```
Sending password reset email to: usuario@ejemplo.com
Email sent successfully
```

---

### 5. Logs y Eventos
- [ ] Los logs se guardan en `logs_quintanaroo`
- [ ] Las notificaciones WebSocket funcionan
- [ ] El dashboard actualiza en tiempo real

---

## Verificación de Independencia

### Confirmar que NO afecta al ambiente 8040
- [ ] El puerto 8040 sigue funcionando normalmente
- [ ] Los usuarios del puerto 8040 NO aparecen en el puerto 8057
- [ ] Los logs del puerto 8040 NO se mezclan con los del puerto 8057
- [ ] Cada ambiente tiene su propia base de datos

**Verificación:**
```javascript
// Conectarse a ambas bases de datos y verificar separación
use logs_system
db.users.countDocuments()

use logs_quintanaroo
db.users.countDocuments()

// Los números deben ser diferentes
```

---

## Checklist de Producción (Antes de pasar a producción)

### Seguridad
- [ ] Cambiar `WEBSOCKET_ALLOWED_ORIGINS=*` por el dominio específico
- [ ] Verificar que las contraseñas no estén en texto plano en scripts
- [ ] Configurar HTTPS/WSS (certificados SSL)
- [ ] Revisar que JWT_SECRET sea único y seguro

### Monitoreo
- [ ] Configurar logs persistentes (archivo o servicio de logging)
- [ ] Configurar alertas para errores críticos
- [ ] Configurar monitoreo de recursos (CPU, memoria, disco)

### Backup
- [ ] Configurar backups automáticos de `logs_quintanaroo`
- [ ] Probar restauración de backup
- [ ] Documentar procedimiento de rollback

### Documentación
- [ ] El equipo conoce las nuevas variables de entorno
- [ ] Existe documentación del proceso de despliegue
- [ ] Existe plan de contingencia

---

## Troubleshooting

### Si el servidor no inicia:
1. Verificar que todas las variables de entorno están configuradas
2. Revisar los logs de error en la consola
3. Verificar que MongoDB está ejecutándose
4. Verificar que el puerto 8057 no está en uso

### Si WebSocket no conecta:
1. Verificar `WEBSOCKET_ALLOWED_ORIGINS`
2. Revisar logs del navegador (consola)
3. Verificar configuración de proxy/nginx si aplica
4. Probar con `WEBSOCKET_ALLOWED_ORIGINS=*` temporalmente

### Si los correos no se envían:
1. Verificar conectividad a `smtp.gmail.com:587`
2. Verificar credenciales de correo
3. Revisar logs del servidor para errores SMTP
4. Verificar firewall del servidor

### Si encuentra usuarios de otro ambiente:
1. Verificar `MONGODB_URI` apunta a `logs_quintanaroo`
2. Verificar `MULTITENANT_BASE_DATABASE=logs_quintanaroo`
3. Reiniciar el servidor después de cambiar variables

---

## ✅ APROBADO PARA DESPLIEGUE

Fecha: ___________  
Responsable: ___________  
Firma: ___________

**Notas adicionales:**
_______________________________________________
_______________________________________________
_______________________________________________

