# 🚀 Guía de Pruebas - API Backlogs

## 📋 Estructura Creada

### Archivos Nuevos:
- ✅ **Model:** `Backlog.java` - Entidad MongoDB
- ✅ **Repository:** `BacklogRepository.java` - Acceso a datos
- ✅ **Service:** `BacklogService.java` - Lógica de negocio
- ✅ **Controller:** `BacklogController.java` - API REST
- ✅ **Health:** `HealthController.java` - Verificación de conexión
- ✅ **Security:** `SecurityConfig.java` - Configuración sin autenticación

---

## 🔍 1. Verificar Conexión a MongoDB

### Endpoint Health Check
```bash
curl http://tu-ec2-ip:8005/api/health
```

**Respuesta esperada:**
```json
{
  "status": "UP",
  "timestamp": 1743109234567,
  "mongodb": {
    "status": "CONNECTED",
    "database": "backlogs",
    "collections": ["backlogs", "apiKeys", ...]
  }
}
```

---

## 📝 2. Crear tu Primer Backlog

```bash
curl -X POST http://tu-ec2-ip:8005/api/backlogs \
  -H "Content-Type: application/json" \
  -d '{
    "titulo": "Implementar autenticación JWT",
    "descripcion": "Agregar sistema de autenticación con tokens JWT para securizar la API",
    "prioridad": "ALTA",
    "estado": "POR_HACER",
    "asignadoA": "Alan Dev"
  }'
```

**Respuesta esperada:**
```json
{
  "id": "65f8a1b2c3d4e5f6g7h8i9j0",
  "titulo": "Implementar autenticación JWT",
  "descripcion": "Agregar sistema de autenticación con tokens JWT para securizar la API",
  "prioridad": "ALTA",
  "estado": "POR_HACER",
  "asignadoA": "Alan Dev",
  "fechaCreacion": "2026-03-27T10:15:30",
  "fechaActualizacion": "2026-03-27T10:15:30"
}
```

---

## 📖 3. Listar Todos los Backlogs

```bash
curl http://tu-ec2-ip:8005/api/backlogs
```

---

## 🔎 4. Buscar Backlog por ID

```bash
curl http://tu-ec2-ip:8005/api/backlogs/{id}
```

---

## 🔄 5. Actualizar un Backlog

```bash
curl -X PUT http://tu-ec2-ip:8005/api/backlogs/{id} \
  -H "Content-Type: application/json" \
  -d '{
    "titulo": "Implementar autenticación JWT",
    "descripcion": "Agregar sistema de autenticación con tokens JWT",
    "prioridad": "ALTA",
    "estado": "EN_PROGRESO",
    "asignadoA": "Alan Dev"
  }'
```

---

## 🗑️ 6. Eliminar un Backlog

```bash
curl -X DELETE http://tu-ec2-ip:8005/api/backlogs/{id}
```

---

## 🔍 7. Filtrar Backlogs

### Por Estado:
```bash
curl http://tu-ec2-ip:8005/api/backlogs/estado/POR_HACER
curl http://tu-ec2-ip:8005/api/backlogs/estado/EN_PROGRESO
curl http://tu-ec2-ip:8005/api/backlogs/estado/COMPLETADO
```

### Por Prioridad:
```bash
curl http://tu-ec2-ip:8005/api/backlogs/prioridad/ALTA
curl http://tu-ec2-ip:8005/api/backlogs/prioridad/MEDIA
curl http://tu-ec2-ip:8005/api/backlogs/prioridad/BAJA
```

---

## 🧪 Ejemplos con Postman

### 1. Health Check
- **Método:** GET
- **URL:** `http://tu-ec2-ip:8005/api/health`

### 2. Crear Backlog
- **Método:** POST
- **URL:** `http://tu-ec2-ip:8005/api/backlogs`
- **Headers:** `Content-Type: application/json`
- **Body (raw JSON):**
```json
{
  "titulo": "Migrar a MongoDB Atlas",
  "descripcion": "Migrar base de datos local a MongoDB Atlas en producción",
  "prioridad": "ALTA",
  "estado": "COMPLETADO",
  "asignadoA": "Equipo DevOps"
}
```

### 3. Listar Todos
- **Método:** GET
- **URL:** `http://tu-ec2-ip:8005/api/backlogs`

---

## 🔥 Datos de Prueba Rápida

Crea varios backlogs de prueba:

```bash
# Backlog 1
curl -X POST http://tu-ec2-ip:8005/api/backlogs \
  -H "Content-Type: application/json" \
  -d '{"titulo":"Diseñar UI principal","descripcion":"Crear mockups de pantallas principales","prioridad":"MEDIA","estado":"POR_HACER","asignadoA":"Diseñador"}'

# Backlog 2
curl -X POST http://tu-ec2-ip:8005/api/backlogs \
  -H "Content-Type: application/json" \
  -d '{"titulo":"Configurar CI/CD","descripcion":"Implementar pipeline de despliegue automático","prioridad":"ALTA","estado":"EN_PROGRESO","asignadoA":"DevOps"}'

# Backlog 3
curl -X POST http://tu-ec2-ip:8005/api/backlogs \
  -H "Content-Type: application/json" \
  -d '{"titulo":"Escribir tests unitarios","descripcion":"Crear suite de tests para servicios","prioridad":"MEDIA","estado":"POR_HACER","asignadoA":"QA Team"}'
```

---

## 🗄️ Verificar en MongoDB Atlas

Ve a tu MongoDB Atlas Console:
1. Selecciona tu cluster `cluster0`
2. Click en **Browse Collections**
3. Busca la base de datos: `backlogs`
4. Verás la colección: `backlogs`
5. Los documentos aparecerán con estructura:

```javascript
{
  "_id": ObjectId("..."),
  "titulo": "...",
  "descripcion": "...",
  "prioridad": "ALTA",
  "estado": "POR_HACER",
  "asignadoA": "...",
  "fechaCreacion": ISODate("2026-03-27T..."),
  "fechaActualizacion": ISODate("2026-03-27T..."),
  "_class": "backlogs.dinamico.model.Backlog"
}
```

---

## 📊 Valores Válidos

### Estados:
- `POR_HACER`
- `EN_PROGRESO`
- `COMPLETADO`

### Prioridades:
- `ALTA`
- `MEDIA`
- `BAJA`

---

## ⚠️ Troubleshooting

### Si no funciona el Health Check:
```bash
# Verifica que la app esté corriendo
ps aux | grep dinamico

# Verifica el puerto
netstat -tuln | grep 8005

# Ve los logs
tail -f logs/backlogs.log
```

### Si no aparecen las colecciones:
- La colección `backlogs` se crea automáticamente al insertar el primer documento
- Usa el POST para crear un backlog y la colección aparecerá

---

## 🎯 URLs Completas

Reemplaza `tu-ec2-ip` con tu IP pública de EC2:

- Health Check: `http://tu-ec2-ip:8005/api/health`
- Backlogs CRUD: `http://tu-ec2-ip:8005/api/backlogs`
- Por Estado: `http://tu-ec2-ip:8005/api/backlogs/estado/{estado}`
- Por Prioridad: `http://tu-ec2-ip:8005/api/backlogs/prioridad/{prioridad}`
