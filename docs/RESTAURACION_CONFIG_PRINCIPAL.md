# 🔄 Restauración de Configuración - Backend Principal de Logs

**Fecha:** 2026-06-15  
**Objetivo:** Revertir configuración del ambiente de Quintana Roo al ambiente principal  
**Estado:** ✅ COMPLETADO

---

## 📋 Resumen de Cambios Aplicados

### **1. Configuración del Servidor**

#### ✅ Puerto Restaurado: **8040**

**Archivos modificados:**
- `application.yml` → `server.port: ${SERVER_PORT:8040}`
- `application-prod.properties` → `server.port=${SERVER_PORT:8040}`

**Antes:**
```yaml
server.port: 8080  # o 8057 (Quintana Roo)
```

**Después:**
```yaml
server.port: ${SERVER_PORT:8040}  # Puerto principal del backend de logs
```

---

### **2. Conexión a Base de Datos**

#### ✅ Base de Datos Restaurada: **logs_system**

**Archivos modificados:**
- `application.yml` → MongoDB URI y multitenant.base-database
- `application-dev.properties` → MongoDB URI
- `application-dev.yml` → multitenant.base-database

**Antes:**
```yaml
spring.data.mongodb.uri: mongodb://localhost:27017/logsQR
multitenant.base-database: logsQR
```

**Después:**
```yaml
spring.data.mongodb.uri: mongodb://localhost:27017/logs_system
multitenant.base-database: ${MULTITENANT_BASE_DATABASE:logs_system}
```

---

### **3. Configuración de WebSocket**

#### ✅ Orígenes Permitidos Actualizados

**Archivo modificado:**
- `application.properties`

**Antes:**
```properties
app.websocket.allowed-origins=${WEBSOCKET_ALLOWED_ORIGINS:*}
```

**Después:**
```properties
# ===== CONFIGURACION DE WEBSOCKET - AMBIENTE PRINCIPAL (Puerto 8040) =====
# Origenes permitidos para WebSocket del sistema principal
# Produccion: ws://dashboard-api.grupo-santoro.com.mx/ws
# Desarrollo: ws://localhost:8040/ws
app.websocket.allowed-origins=${WEBSOCKET_ALLOWED_ORIGINS:http://localhost:3000,https://dashboard-api.grupo-santoro.com.mx}
```

---

### **4. Limpieza de Referencias a Quintana Roo**

#### ✅ Referencias Eliminadas

**Archivo modificado:**
- `application.properties`

**Cambio aplicado:**
```properties
# Antes:
app.mail.from-name=${MAIL_FROM_NAME:Backlogs Santoro - Quintana Roo}

# Después:
app.mail.from-name=${MAIL_FROM_NAME:Backlogs Santoro - Sistema Principal}
```

---

## 📁 Archivos de Configuración Actualizados

### **Archivos Principales:**

1. ✅ `src/main/resources/application.yml`
   - Puerto: 8040
   - Base de datos: logs_system
   - Comentarios añadidos para claridad

2. ✅ `src/main/resources/application.properties`
   - WebSocket orígenes actualizados
   - Nombre de remitente de correo actualizado
   - Comentarios de ambiente principal

3. ✅ `src/main/resources/application-dev.properties`
   - MongoDB URI: logs_system
   - Comentarios actualizados

4. ✅ `src/main/resources/application-dev.yml`
   - Base de datos: logs_system
   - Comentario de ambiente principal

5. ✅ `src/main/resources/application-prod.properties`
   - Puerto: 8040
   - Comentarios actualizados

---

## 🚀 Cómo Usar la Configuración Restaurada

### **Variables de Entorno para Producción**

```bash
# MongoDB (Producción)
export MONGODB_URI="mongodb://usuario:password@ip_servidor:27017/logs_system"
export MULTITENANT_BASE_DATABASE="logs_system"

# Puerto del servidor
export SERVER_PORT=8040

# WebSocket
export WEBSOCKET_ALLOWED_ORIGINS="https://dashboard-api.grupo-santoro.com.mx"

# JWT Secret (cambiar en producción)
export JWT_SECRET="tu_secret_super_seguro_minimo_32_caracteres"

# OpenAI (si se usa)
export OPENAI_API_KEY="sk-..."
export EVA_OPENAI_API_KEY="sk-..."

# Correo
export MAIL_HOST="smtp.gmail.com"
export MAIL_PORT=587
export MAIL_USERNAME="soporte.tecnico@grupo-santoro.com.mx"
export MAIL_PASSWORD="tu_app_password"
```

### **Variables de Entorno para Windows (PowerShell)**

```powershell
# MongoDB
$env:MONGODB_URI="mongodb://localhost:27017/logs_system"
$env:MULTITENANT_BASE_DATABASE="logs_system"

# Puerto
$env:SERVER_PORT=8040

# WebSocket
$env:WEBSOCKET_ALLOWED_ORIGINS="http://localhost:3000,https://dashboard-api.grupo-santoro.com.mx"
```

---

## 🏗️ Compilar y Ejecutar

### **1. Compilación**

```powershell
# Limpiar y compilar
.\mvnw.cmd clean package -DskipTests

# El JAR se genera en:
# target/dinamico-0.0.1-SNAPSHOT.jar
```

### **2. Ejecución Local (Desarrollo)**

```powershell
# Con Maven
.\mvnw.cmd spring-boot:run

# O con el JAR
java -jar target/dinamico-0.0.1-SNAPSHOT.jar

# Con perfil de desarrollo
java -jar target/dinamico-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

### **3. Ejecución en Producción**

```bash
# Linux
java -jar dinamico-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --server.port=8040

# Windows
java -jar dinamico-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod --server.port=8040
```

---

## ✅ Verificación Post-Restauración

### **Checklist de Validación**

#### **1. Verificar Puerto**
```bash
# El servidor debe arrancar en el puerto 8040
curl http://localhost:8040/swagger-ui.html
```

#### **2. Verificar Conexión a Base de Datos**
```bash
# Debe conectarse a logs_system
# Revisar los logs del servidor al iniciar:
# "Connected to MongoDB database: logs_system"
```

#### **3. Verificar WebSocket**
```bash
# Intentar conectar desde el frontend al WebSocket
ws://localhost:8040/ws
```

#### **4. Verificar API**
```bash
# Endpoints principales deben responder
curl http://localhost:8040/v3/api-docs
curl http://localhost:8040/api/analytics/funnel/available-systems
```

---

## 🔧 Troubleshooting

### **Problema: El servidor arranca en puerto incorrecto**

**Solución:**
```bash
# Verificar que no haya variable de entorno SERVER_PORT configurada
echo $SERVER_PORT  # Linux
echo $env:SERVER_PORT  # Windows PowerShell

# Si existe, resetearla:
export SERVER_PORT=8040  # Linux
$env:SERVER_PORT=8040  # Windows
```

### **Problema: No se conecta a la base de datos correcta**

**Solución:**
```bash
# Verificar variable MONGODB_URI
echo $MONGODB_URI

# Verificar MULTITENANT_BASE_DATABASE
echo $MULTITENANT_BASE_DATABASE

# Si no existen, el sistema usará los valores por defecto:
# - logs_system (correcto para ambiente principal)
```

### **Problema: WebSocket no acepta conexiones del frontend**

**Solución:**
```bash
# Configurar los orígenes permitidos
export WEBSOCKET_ALLOWED_ORIGINS="http://localhost:3000,https://tu-dominio.com"

# O en Windows:
$env:WEBSOCKET_ALLOWED_ORIGINS="http://localhost:3000,https://tu-dominio.com"
```

---

## 📊 Diferencias Entre Ambientes

| Característica | **Principal (8040)** | Quintana Roo (8057) |
|----------------|---------------------|---------------------|
| **Puerto** | 8040 | 8057 |
| **Base de Datos** | logs_system | logsQR |
| **WebSocket** | dashboard-api.grupo-santoro.com.mx | dashboard-quintanaroo.grupo-santoro.com.mx |
| **Propósito** | Sistema global de auditoría | Ambiente específico QR |
| **Historial** | Completo y centralizado | Aislado para QR |

---

## 📝 Notas Importantes

### **⚠️ Atención**

1. **Base de datos logs_system** contiene el historial completo de auditoría
2. **No mezclar** configuraciones entre ambientes
3. **Variables de entorno** tienen prioridad sobre valores por defecto
4. **Backup** de la base de datos antes de desplegar en producción

### **🔐 Seguridad**

- Cambiar `security.jwt.secret` en producción
- Usar credenciales seguras para MongoDB
- Configurar CORS correctamente en producción
- Usar HTTPS para WebSocket en producción

---

## 🎯 Próximos Pasos

### **1. Validación Completa**
```bash
# Ejecutar suite completa de tests
.\mvnw.cmd test

# Verificar funcionalidad de Analytics (Funnel)
.\test-funnel-analytics.ps1 -Token "..." -Tenant "..."
```

### **2. Despliegue en Servidor**
- Copiar JAR al servidor
- Configurar variables de entorno
- Iniciar servicio
- Verificar logs

### **3. Monitoreo**
- Verificar que los logs se registren en `logs_system`
- Monitorear uso de memoria y CPU
- Revisar logs de errores

---

## 📞 Soporte

**Email:** soporte.tecnico@grupo-santoro.com.mx  
**Documentación:** docs/  
**Swagger UI:** http://localhost:8040/swagger-ui.html

---

## ✅ Estado Final

- [x] Puerto restaurado a 8040
- [x] Base de datos apuntando a logs_system
- [x] WebSocket configurado para ambiente principal
- [x] Referencias a Quintana Roo eliminadas
- [x] Comentarios añadidos para claridad
- [x] Documentación completa generada

**🎉 Configuración restaurada exitosamente al ambiente principal**

---

**Última actualización:** 2026-06-15  
**Versión del sistema:** 0.0.1-SNAPSHOT  
**Estado:** ✅ PRODUCCIÓN (Configuración Principal Restaurada)

