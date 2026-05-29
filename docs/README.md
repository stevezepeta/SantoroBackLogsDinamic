# 📚 DataLogs - Backend Documentation

Sistema de gestión centralizada de logs para Grupo Santoro.

---

## 🗂️ Índice de Documentación

### 🔐 Autenticación y Seguridad

- **[Password Recovery](./PASSWORD_RECOVERY.md)** 🆕  
  Flujo de recuperación de contraseñas sin `X-Tenant` usando códigos de 6 dígitos por email
  - Endpoints públicos `/api/auth/forgot-password` y `/api/auth/reset-password`
  - Búsqueda global de usuarios por email
  - Códigos numéricos seguros con expiración automática
  - Templates HTML profesionales para emails

- **[External Validation Endpoint](./EXTERNAL_VALIDATION_ENDPOINT.md)**  
  Endpoint de validación de credenciales para integración con sistema de Tickets
  - `POST /api/auth/validate-external`
  - Estrategia A: Contraseña unificada

### 🌐 CORS y Deploy

- **[CORS AWS Fix](./CORS_AWS_FIX.md)**  
  Solución al problema de 403 Forbidden con headers personalizados en AWS
  - Configuración de `exposedHeaders` para `X-Tenant` y `X-Tenant-Id`
  - Diferencias entre desarrollo y producción

- **[Deploy CORS Fix](./DEPLOY_CORS_FIX.md)**  
  Guía paso a paso para deployment del fix de CORS en AWS

### 📊 Dashboard y Logs

- **[Dashboard Logs](./DASHBOARD_LOGS.md)**  
  Configuración y uso del dashboard de visualización de logs

### 🤖 Agentes y Automatización

- **[Agents](./AGENTS.md)**  
  Documentación de agentes especializados para tareas automáticas

---

## 🚀 Quick Start

### Desarrollo Local

```bash
# Compilar
.\mvnw.cmd clean package -DskipTests

# Ejecutar
.\mvnw.cmd spring-boot:run

# Swagger UI
http://localhost:8005/swagger-ui.html
```

### Variables de Entorno Requeridas

```properties
# MongoDB
MONGO_URI=mongodb://localhost:27017/backlogs

# JWT Secret (mínimo 32 caracteres)
JWT_SECRET=tu_secret_super_seguro_de_minimo_32_caracteres

# Email (Producción)
MAIL_APP_PASSWORD=tu_app_password_de_gmail
```

---

## 🧪 Testing

### Password Recovery

```powershell
# Test completo del flujo
.\test-password-reset.ps1

# Test en AWS
.\test-password-reset.ps1 -BaseUrl "https://api-logs.grupo-santoro.com.mx"
```

### CORS Local

```powershell
.\test-cors-local.ps1
```

---

## 📖 API Endpoints

### Públicos (sin autenticación)

| Method | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/api/auth/login` | Login con email/password |
| POST | `/api/auth/refresh` | Renovar token JWT |
| POST | `/api/auth/forgot-password` | Solicitar código de recuperación |
| POST | `/api/auth/reset-password` | Restablecer contraseña con código |
| POST | `/api/auth/validate-external` | Validar credenciales (para Tickets) |
| POST | `/api/auth/accept-invite` | Aceptar invitación de organización |

### Protegidos (requieren JWT)

| Method | Endpoint | Descripción | Permisos |
|--------|----------|-------------|----------|
| GET | `/api/logs` | Listar logs | `PERM_LOG_READ` |
| GET | `/api/logs/export` | Exportar logs | `PERM_LOG_EXPORT` |
| POST | `/api/catalogs/organizations` | Crear organización | `PERM_SETTINGS_MANAGE` |
| GET | `/api/core/users` | Listar usuarios | `PERM_USERS_MANAGE` |

---

## 🏗️ Arquitectura

```
Backend (Spring Boot 3.4.3)
├── controller/         → REST endpoints
├── service/           → Lógica de negocio
├── repository/        → MongoDB repositories
├── model/             → Entidades y DTOs
├── security/          → JWT, RBAC, Filters
├── infra/             → Email, Storage, WS
└── config/            → Spring Configuration
```

---

## 🔒 Seguridad

- ✅ JWT con roles/permisos granulares
- ✅ BCrypt para contraseñas (salt rounds: 10)
- ✅ Rate limiting (recomendado)
- ✅ CORS configurado para producción
- ✅ HTTPS obligatorio en AWS
- ✅ API Keys para ingest endpoints

---

## 📝 Convenciones

### Códigos de Error

| Código | Descripción |
|--------|-------------|
| `email_required` | Email es obligatorio |
| `password_min_8_chars` | Contraseña muy corta |
| `reset_code_expired` | Código de recuperación expirado |
| `invalid_credentials` | Email o contraseña incorrectos |
| `user_not_found` | Usuario no existe |

### Status HTTP

- `200 OK` - Operación exitosa
- `400 BAD_REQUEST` - Validación fallida
- `401 UNAUTHORIZED` - Sin autenticación
- `403 FORBIDDEN` - Sin permisos
- `404 NOT_FOUND` - Recurso no existe
- `500 INTERNAL_SERVER_ERROR` - Error del servidor

---

## 🛠️ Stack Tecnológico

- **Java 17**
- **Spring Boot 3.4.3**
- **MongoDB** (base de datos principal)
- **JWT** (autenticación)
- **BCrypt** (hashing de contraseñas)
- **JavaMail** (envío de emails)
- **Swagger/OpenAPI** (documentación)
- **WebSocket/STOMP** (notificaciones real-time)

---

## 📞 Soporte

**Email**: soporte.tecnico@grupo-santoro.com.mx  
**Documentación**: `/docs/`  
**Swagger UI**: `http://localhost:8005/swagger-ui.html`

---

**Última actualización**: 2026-05-29  
**Versión**: 2.0

