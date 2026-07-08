# 📚 DataLogs - Backend Documentation

**Plataforma Universal de Observabilidad y Analytics** para Grupo Santoro.

Sistema modular basado en plugins que soporta múltiples aplicaciones (TRUSTVALUE, TICKETS, y futuros sistemas).

---

## 🧭 INICIO RÁPIDO

### 🌟 Lee ESTO primero:

**[📄 RESUMEN_EJECUTIVO.md](./RESUMEN_EJECUTIVO.md)** (10 minutos) ⚡  
Entendimiento completo del sistema, arquitectura de plugins, y plan de implementación.

### ✅ Empieza a implementar:

**[📋 CHECKLIST_IMPLEMENTACION.md](./CHECKLIST_IMPLEMENTACION.md)** 🚀  
Guía paso a paso (día por día) para implementar Fase 1 completa en 3 semanas.

### 📚 Documentación Técnica Detallada:

1. **[🏗️ ARQUITECTURA_UNIVERSAL_DEFINITIVA.md](./ARQUITECTURA_UNIVERSAL_DEFINITIVA.md)** (30 min)  
   Arquitectura de plugins, ejemplos de TRUSTVALUE y TICKETS, componentes core

2. **[📋 PLAN_IMPLEMENTACION_MODULAR.md](./PLAN_IMPLEMENTACION_MODULAR.md)** (20 min)  
   Plan completo Fase 1-3, código del Plugin Engine (Entregable 1.1)

3. **[📊 ENTREGABLE_1.2_ANALYTICS_ENGINE.md](./ENTREGABLE_1.2_ANALYTICS_ENGINE.md)** (15 min)  
   Motor de análisis configurable, detección de anomalías

4. **[📈 ENTREGABLE_1.3_KPIS_DINAMICOS.md](./ENTREGABLE_1.3_KPIS_DINAMICOS.md)** (15 min)  
   Calculador de KPIs dinámicos, dashboard personalizado

---

## 🗂️ Índice de Documentación

### 🆕 Ambiente Quintana Roo (Puerto 8057) - NUEVO

**IMPORTANTE:** Documentación específica para la configuración independiente del ambiente de Quintana Roo:

- **[🔄 RESTAURACION DE CONFIGURACION PRINCIPAL](./RESTAURACION_CONFIG_PRINCIPAL.md)** 🔥 **NUEVO**  
  Guía completa para restaurar la configuración del backend principal (Puerto 8040)
  - Revertir cambios del ambiente de Quintana Roo
  - Restaurar base de datos logs_system
  - Scripts de verificación y despliegue
  - Troubleshooting completo

- **[🚀 INICIO RÁPIDO](INICIO_RAPIDO.md)** 🔥  
  Guía de inicio rápido para desplegar el ambiente en 3 pasos
  - Scripts automatizados de inicio
  - Verificación de pre-requisitos
  - URLs de acceso y testing

- **[📋 CHECKLIST DE DESPLIEGUE](CHECKLIST_DESPLIEGUE.md)**  
  Checklist completo paso a paso para despliegue seguro
  - Pre-requisitos
  - Variables de entorno
  - Pruebas post-despliegue
  - Verificación de independencia

- **[📖 SETUP COMPLETO](./QUINTANA_ROO_SETUP.md)**  
  Documentación técnica completa del ambiente Quintana Roo
  - Configuración de base de datos independiente
  - WebSocket dinámico
  - Configuración de correo
  - Troubleshooting detallado

- **[🏗️ ARQUITECTURA](./ARQUITECTURA.md)**  
  Diagramas de arquitectura antes/después de los cambios
  - Comparación visual de ambientes
  - Flujo de datos
  - Beneficios de la nueva arquitectura

- **[✅ CAMBIOS REALIZADOS](CAMBIOS_REALIZADOS.md)**  
  Resumen ejecutivo de todas las modificaciones aplicadas
  - Base de datos independizada
  - WebSocket configurable
  - Correo externalizado

**Scripts Disponibles:**
- `start-quintanaroo.ps1` - Inicia el servidor con configuración correcta
- `verificar-ambiente.ps1` - Verifica que todo esté listo

---

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

- **[📊 Funnel Analytics - Análisis de Embudos de Conversión](./FUNNEL_ANALYTICS.md)** ⭐ **NUEVO**  
  Módulo de analítica estratégica de procesos
  - Métricas de conversión y abandono por pasos secuenciales
  - Identificación de cuellos de botella en flujos de usuario
  - KPIs de rendimiento operativo
  - Soporte multi-sistema (CITA_GUYANA, TICKETS, TRUSTVALUE, PASSPORT, CITA_QUINTANAROO)

### 🤖 Agentes y Automatización

- **[Agents](./AGENTS.md)**  
  Documentación de agentes especializados para tareas automáticas

---

## 🎯 Arquitectura Modular de Plugins

### 🚀 Sistema Universal para Múltiples Aplicaciones

> **Enfoque:** Plataforma escalable que se adapta a CUALQUIER sistema mediante plugins JSON

**Sistemas Actuales:**
- ✅ TRUSTVALUE (asistencias, geolocalización)
- ✅ TICKETS (soporte web)
- ✅ Futuros sistemas (sin límite)

**Documentación Principal:**

- **[⚡ RESUMEN_EJECUTIVO.md](./RESUMEN_EJECUTIVO.md)** 🔥  
  **LEER PRIMERO** - Entendimiento completo en 10 minutos
  - Arquitectura de plugins explicada
  - Plan de implementación (Fase 1-3)
  - Ejemplos de TRUSTVALUE y TICKETS
  - FAQ completo

- **[🏗️ ARQUITECTURA_UNIVERSAL_DEFINITIVA.md](./ARQUITECTURA_UNIVERSAL_DEFINITIVA.md)** ⭐  
  Arquitectura técnica completa
  - Concepto de plugins
  - Componentes core (Plugin Engine, Analytics Engine, KPI Calculator)
  - Ejemplos completos de plugins JSON
  - Escalabilidad y extensibilidad

### 📦 Implementación Técnica

- **[📋 PLAN_IMPLEMENTACION_MODULAR.md](./PLAN_IMPLEMENTACION_MODULAR.md)**  
  Plan completo con código
  - **Fase 1:** Plugin Engine + Analytics + KPIs (2-3 semanas)
  - **Fase 2:** Eva Context-Aware (2 semanas)
  - **Fase 3:** Optimizaciones (1-2 semanas)
  - Código Java completo del Plugin Engine (Entregable 1.1)

- **[📊 ENTREGABLE_1.2_ANALYTICS_ENGINE.md](./ENTREGABLE_1.2_ANALYTICS_ENGINE.md)**  
  Motor de análisis configurable (Semana 2)
  - Evaluador de condiciones dinámicas
  - Detección automática de anomalías
  - Código completo con ejemplos

- **[📈 ENTREGABLE_1.3_KPIS_DINAMICOS.md](./ENTREGABLE_1.3_KPIS_DINAMICOS.md)**  
  Dashboard de KPIs personalizado (Semana 3)
  - Calculador de KPIs dinámicos
  - Parser de queries simple
  - Código de frontend React incluido

---

### 📚 Recursos Adicionales

- **[🎤 PITCH DECK - Notas de Presentación](./PITCH_DECK_NOTES.md)**  
  Cómo vender el sistema
  - ROI calculado
  - Comparativa con competencia (Splunk, DataDog)
  - Casos de uso

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

