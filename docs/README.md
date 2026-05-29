# SantoroBackLogsDinamic

Sistema de gestión de backlogs dinámico con Spring Boot y MongoDB.

## 🚀 Características

- ✅ API REST completa con CRUD
- ✅ MongoDB Atlas para almacenamiento en producción
- ✅ Spring Data MongoDB (Sync + Reactive)
- ✅ Spring AI con Vector Store support
- ✅ Health Check endpoint
- ✅ Filtros por estado y prioridad
- ✅ Configuración segura con variables de entorno

## 📋 Requisitos Previos

- Java 17 o superior
- Maven 3.6+
- MongoDB (local para desarrollo, remoto para producción)

## 🔧 Configuración

### Configuración de Secretos

Para configurar las variables de entorno necesarias, consulta el archivo [CONFIGURACION_SECRETS.md](CONFIGURACION_SECRETS.md) para instrucciones detalladas.

### Variables de Entorno Necesarias

#### Desarrollo (Local)
- `OPENAI_API_KEY` - API key de OpenAI
- `MAIL_PASSWORD` - Contraseña de aplicación de Gmail
- `MAIL_FROM` - Email de origen

#### Producción (Adicionales)
- `MONGODB_URI` - URI de conexión a MongoDB Atlas
- `MONGODB_DATABASE` - Nombre de la base de datos (default: backlogs_prod)

## 🏃 Ejecutar la Aplicación

### Modo Desarrollo
```bash
mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Modo Producción
```bash
mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## 📡 Endpoints API

### Health Check
```
GET /api/health
```
Verifica el estado de la aplicación y la conexión a MongoDB.

### Backlogs CRUD

**Listar todos los backlogs:**
```
GET /api/backlogs
```

**Obtener un backlog por ID:**
```
GET /api/backlogs/{id}
```

**Crear un nuevo backlog:**
```
POST /api/backlogs
Content-Type: application/json

{
  "titulo": "Título del backlog",
  "descripcion": "Descripción detallada",
  "prioridad": "ALTA|MEDIA|BAJA",
  "estado": "POR_HACER|EN_PROGRESO|COMPLETADO",
  "asignadoA": "Nombre del responsable"
}
```

**Actualizar un backlog:**
```
PUT /api/backlogs/{id}
Content-Type: application/json
```

**Eliminar un backlog:**
```
DELETE /api/backlogs/{id}
```

### Filtros

**Por estado:**
```
GET /api/backlogs/estado/{estado}
```

**Por prioridad:**
```
GET /api/backlogs/prioridad/{prioridad}
```

## 🧪 Pruebas Rápidas

Consulta la [GUIA_PRUEBAS.md](GUIA_PRUEBAS.md) para ejemplos detallados de uso con curl y Postman.

### Script de Datos de Prueba

**Linux/Mac:**
```bash
chmod +x crear-datos-prueba.sh
./crear-datos-prueba.sh http://tu-ip:8005
```

**Windows:**
```cmd
crear-datos-prueba.bat http://tu-ip:8005
```

## 📁 Estructura del Proyecto

```
src/
├── main/
│   ├── java/
│   │   └── backlogs/dinamico/
│   │       ├── BacklogsApplication.java
│   │       ├── config/
│   │       │   └── SecurityConfig.java
│   │       ├── controller/
│   │       │   ├── BacklogController.java
│   │       │   └── HealthController.java
│   │       ├── model/
│   │       │   └── Backlog.java
│   │       ├── repository/
│   │       │   └── BacklogRepository.java
│   │       └── service/
│   │           └── BacklogService.java
│   └── resources/
│       ├── application.properties         # Configuración base
│       ├── application-dev.properties     # Desarrollo
│       └── application-prod.properties    # Producción
└── test/
    └── java/
        └── backlogs/dinamico/
            └── BacklogsApplicationTests.java
```

## 🛠️ Tecnologías

- **Spring Boot** 3.5.6
- **Spring AI** 1.0.3
- **MongoDB** (Reactive & Vector Store)
- **Spring Security** (configurado sin autenticación por defecto)
- **Project Lombok** (anotaciones simplificadas)

## 📝 Modelo de Datos

### Backlog
```json
{
  "id": "string",
  "titulo": "string",
  "descripcion": "string",
  "prioridad": "ALTA|MEDIA|BAJA",
  "estado": "POR_HACER|EN_PROGRESO|COMPLETADO",
  "asignadoA": "string",
  "fechaCreacion": "datetime",
  "fechaActualizacion": "datetime"
}
```

## 🔐 Seguridad

La configuración actual tiene **CSRF deshabilitado** y todas las rutas accesibles sin autenticación. Esto es para desarrollo/pruebas. 

Para producción, se recomienda implementar:
- Autenticación JWT
- Validación de roles
- Rate limiting
- HTTPS

## 📚 Documentación Adicional

- [CONFIGURACION_SECRETS.md](CONFIGURACION_SECRETS.md) - Guía de configuración de variables de entorno
- [GUIA_PRUEBAS.md](GUIA_PRUEBAS.md) - Ejemplos de uso de la API
- [run-produccion.bat](run-produccion.bat) / [run-produccion.sh](run-produccion.sh) - Scripts para ejecutar en producción

## 🐛 Troubleshooting

Si la aplicación no inicia, verifica:
1. Variables de entorno configuradas correctamente
2. Conexión a MongoDB disponible
3. Puerto 8005/8007 disponible
4. Java 17+ instalado

Ver logs detallados:
```bash
java -jar dinamico-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod --debug
```


