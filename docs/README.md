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


