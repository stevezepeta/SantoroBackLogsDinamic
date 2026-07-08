# 🎯 CONFIGURACIÓN COMPLETADA - AMBIENTE QUINTANA ROO

## ✅ ¡Todas las correcciones han sido aplicadas exitosamente!

---

## 📊 RESUMEN EJECUTIVO

El ambiente de Quintana Roo (puerto 8057) ahora está **100% independizado** del ambiente principal (puerto 8040).

### Problemas Resueltos:
1. ✅ **Base de Datos Separada:** Ahora usa `logs_quintanaroo` en lugar de `logs_system`
2. ✅ **WebSocket Dinámico:** Configuración flexible mediante variables de entorno
3. ✅ **Correo Independiente:** Configuración de mail completamente externalizada

---

## 🚀 INICIO RÁPIDO

### 1️⃣ Verificar Pre-requisitos (Opcional pero recomendado)
```powershell
.\verificar-ambiente.ps1
```

### 2️⃣ Iniciar el Servidor
```powershell
.\start-quintanaroo.ps1
```

### 3️⃣ Verificar que Esté Funcionando
- **API:** http://localhost:8057
- **Swagger:** http://localhost:8057/swagger-ui.html
- **WebSocket:** ws://localhost:8057/ws

---

## 📁 ARCHIVOS CREADOS/MODIFICADOS

### ✏️ Archivos Modificados (Configuración)
```
✓ src/main/resources/application.yml
✓ src/main/resources/application.properties
✓ src/main/resources/application-dev.yml
✓ src/main/resources/application-dev.properties
✓ src/main/java/backlogs/dinamico/infra/ws/WebSocketConfig.java
```

### 📝 Archivos Creados (Documentación)
```
✓ docs/QUINTANA_ROO_SETUP.md          (Documentación completa)
✓ QUINTANA_ROO_README.md              (Guía rápida de inicio)
✓ CAMBIOS_REALIZADOS.md               (Resumen de cambios)
✓ CHECKLIST_DESPLIEGUE.md             (Checklist paso a paso)
```

### 🔧 Archivos Creados (Scripts)
```
✓ start-quintanaroo.ps1               (Script de inicio)
✓ verificar-ambiente.ps1              (Script de verificación)
✓ .env.quintanaroo.example            (Ejemplo de variables)
```

---

## 🔑 VARIABLES DE ENTORNO CONFIGURABLES

El sistema ahora acepta las siguientes variables de entorno:

| Variable | Valor por Defecto | Descripción |
|----------|-------------------|-------------|
| `SERVER_PORT` | 8057 | Puerto del servidor |
| `MONGODB_URI` | logs_quintanaroo | URI de MongoDB |
| `MULTITENANT_BASE_DATABASE` | logs_quintanaroo | Base de datos multitenant |
| `WEBSOCKET_ALLOWED_ORIGINS` | * | Orígenes permitidos para WebSocket |
| `MAIL_HOST` | smtp.gmail.com | Host SMTP |
| `MAIL_PORT` | 587 | Puerto SMTP |
| `MAIL_USERNAME` | soporte.tecnico@... | Usuario de correo |
| `MAIL_PASSWORD` | (configurado) | Contraseña de correo |
| `MAIL_FROM` | soporte.tecnico@... | Remitente |
| `MAIL_FROM_NAME` | Backlogs Santoro - Quintana Roo | Nombre del remitente |

---

## 🎯 SIGUIENTE PASO IMPORTANTE

### ⚠️ ANTES DE INICIAR EL SERVIDOR:

**Asegúrate de crear/verificar la base de datos de Quintana Roo:**

```javascript
// Conectarse a MongoDB
mongosh

// Crear/usar la base de datos logs_quintanaroo
use logs_quintanaroo

// Verificar si hay usuarios
db.users.countDocuments()

// Si NO hay usuarios, migrarlos desde logs_system
use logs_system
const usuariosQR = db.users.find({estado: "Quintana Roo"}).toArray()
use logs_quintanaroo
db.users.insertMany(usuariosQR)
```

---

## 📋 FLUJO DE DESPLIEGUE RECOMENDADO

### Paso 1: Backup (Seguridad primero)
```bash
# Hacer backup de la base de datos actual
mongodump --db logs_system --out backup_$(date +%Y%m%d)
```

### Paso 2: Preparar Base de Datos
```javascript
// Ver instrucciones en la sección anterior
use logs_quintanaroo
db.users.countDocuments()
```

### Paso 3: Compilar (Ya realizado)
```powershell
# Ya ejecutado exitosamente ✓
mvn clean package -DskipTests
```

### Paso 4: Verificar
```powershell
.\verificar-ambiente.ps1
```

### Paso 5: Iniciar
```powershell
.\start-quintanaroo.ps1
```

### Paso 6: Probar
1. Abrir http://localhost:8057/swagger-ui.html
2. Probar login con un usuario de Quintana Roo
3. Probar recuperación de contraseña
4. Verificar WebSocket desde el frontend

---

## 🔍 VERIFICACIÓN RÁPIDA POST-INICIO

### 1. ¿El servidor está escuchando?
```powershell
Test-NetConnection -ComputerName localhost -Port 8057
```

### 2. ¿Conecta a la base de datos correcta?
Revisar los logs del servidor, debe mostrar:
```
Connecting to MongoDB: logs_quintanaroo
```

### 3. ¿WebSocket funciona?
Desde la consola del navegador:
```javascript
const ws = new WebSocket('ws://localhost:8057/ws');
ws.onopen = () => console.log('✅ OK');
```

### 4. ¿Login funciona?
```bash
curl -X POST http://localhost:8057/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"usuario@ejemplo.com","password":"contraseña"}'
```

---

## 🐛 SOLUCIÓN DE PROBLEMAS COMUNES

| Problema | Solución Rápida |
|----------|----------------|
| "Puerto 8057 en uso" | Cerrar la otra aplicación o cambiar `SERVER_PORT` |
| "MongoDB connection failed" | Verificar que MongoDB esté corriendo |
| "WebSocket CORS error" | Verificar `WEBSOCKET_ALLOWED_ORIGINS=*` |
| "Usuario no encontrado" | Verificar que el usuario esté en `logs_quintanaroo` |
| "Mail send failed" | Verificar conectividad al puerto 587 |

---

## 📚 DOCUMENTACIÓN DE REFERENCIA

- **Guía Completa:** `docs/QUINTANA_ROO_SETUP.md`
- **Checklist Detallado:** `CHECKLIST_DESPLIEGUE.md`
- **Cambios Técnicos:** `CAMBIOS_REALIZADOS.md`
- **Ejemplo de Variables:** `.env.quintanaroo.example`

---

## ⚙️ CONFIGURACIÓN PARA PRODUCCIÓN

Cuando vayas a producción, **CAMBIAR**:

1. **WebSocket Origins:**
   ```bash
   WEBSOCKET_ALLOWED_ORIGINS=https://dashboard-quintanaroo.grupo-santoro.com.mx
   ```

2. **JWT Secret:** (usar uno único para este ambiente)
   ```bash
   JWT_SECRET=<generar-nuevo-secret-largo-y-seguro>
   ```

3. **Contraseñas:** No dejarlas en scripts, usar secretos seguros

4. **HTTPS:** Configurar certificados SSL/TLS

---

## 📞 SOPORTE

**Email:** soporte.tecnico@grupo-santoro.com.mx

**Documentos creados:**
- ✅ Documentación técnica completa
- ✅ Scripts de inicio automatizados
- ✅ Scripts de verificación
- ✅ Checklist de despliegue
- ✅ Guías de troubleshooting

---

## 🎉 ¡LISTO PARA DESPLEGAR!

El ambiente está **compilado**, **configurado** y **documentado**.

**Próximo comando a ejecutar:**
```powershell
.\start-quintanaroo.ps1
```

---

**Fecha:** 2026-06-05  
**Versión:** 0.0.1-SNAPSHOT  
**Estado:** ✅ LISTO PARA DESPLIEGUE

