# 📊 Seed Data para Funnel Templates

## 🎯 Objetivo

Este documento contiene los scripts MongoDB para insertar las configuraciones iniciales de embudos de conversión (Funnel Templates) en la colección `funnel_templates`.

## 📝 Script de Inserción (MongoDB Shell)

```javascript
// Conectar a la base de datos
use logs_system;

// Limpiar templates existentes (OPCIONAL - solo en desarrollo)
// db.funnel_templates.deleteMany({});

// ═══════════════════════════════════════════════════════════════════════════
// INSERCIÓN DE TEMPLATES DE FUNNEL
// ═══════════════════════════════════════════════════════════════════════════

db.funnel_templates.insertMany([
  
  // ──────────────────────────────────────────────────────────────────────────
  // 1. TRUSTVALUE - Flujo de Jornada Laboral (FLUJO REAL)
  // ──────────────────────────────────────────────────────────────────────────
  {
    "systemName": "TRUSTVALUE",
    "funnelName": "Flujo de Jornada Laboral",
    "description": "Flujo completo de registro de asistencia de empleados en TrustValue",
    "active": true,
    "createdAt": new Date(),
    "updatedAt": new Date(),
    "createdBy": "system",
    "steps": [
      {
        "order": 1,
        "eventType": "INICIO_SESION",
        "label": "Inicio de Sesión",
        "description": "Usuario inicia sesión en el sistema"
      },
      {
        "order": 2,
        "eventType": "SELECCION_SUCURSAL",
        "label": "Selección de Sucursal",
        "description": "Usuario selecciona la sucursal donde laborará"
      },
      {
        "order": 3,
        "eventType": "ENVIAR_EVIDENCIAS",
        "label": "Envío de Evidencias",
        "description": "Usuario envía evidencias fotográficas o biométricas"
      },
      {
        "order": 4,
        "eventType": "FINALIZAR_ASISTENCIA",
        "label": "Cierre de Jornada",
        "description": "Registro de asistencia completado exitosamente"
      }
    ]
  },

  // ──────────────────────────────────────────────────────────────────────────
  // 2. CITA_GUYANA - Flujo de Tramitación de Citas
  // ──────────────────────────────────────────────────────────────────────────
  {
    "systemName": "CITA_GUYANA",
    "funnelName": "Flujo de Trámite de Cita",
    "description": "Proceso completo de agendamiento de citas consulares en Guyana",
    "active": true,
    "createdAt": new Date(),
    "updatedAt": new Date(),
    "createdBy": "system",
    "steps": [
      {
        "order": 1,
        "eventType": "AUTH_LOGIN",
        "label": "Inicio de Sesión",
        "description": "Usuario se autentica en el sistema"
      },
      {
        "order": 2,
        "eventType": "CONSULTA_ESTADO_REGISTRO",
        "label": "Validación de Registro",
        "description": "Sistema valida el registro del usuario"
      },
      {
        "order": 3,
        "eventType": "RESERVA_DE_CITA",
        "label": "Cita Completada",
        "description": "Usuario completa la reserva de su cita"
      }
    ]
  },

  // ──────────────────────────────────────────────────────────────────────────
  // 3. TICKETS - Flujo de Atención de Tickets de Soporte
  // ──────────────────────────────────────────────────────────────────────────
  {
    "systemName": "TICKETS",
    "funnelName": "Flujo de Atención de Tickets",
    "description": "Ciclo completo de gestión de tickets de soporte técnico",
    "active": true,
    "createdAt": new Date(),
    "updatedAt": new Date(),
    "createdBy": "system",
    "steps": [
      {
        "order": 1,
        "eventType": "CREAR_TICKET",
        "label": "Creación de Ticket",
        "description": "Usuario crea un nuevo ticket de soporte"
      },
      {
        "order": 2,
        "eventType": "ASIGNAR_TECNICO",
        "label": "Asignación a Técnico",
        "description": "Ticket es asignado a un técnico disponible"
      },
      {
        "order": 3,
        "eventType": "RESOLVER_TICKET",
        "label": "Ticket Resuelto",
        "description": "Técnico marca el ticket como resuelto"
      }
    ]
  },

  // ──────────────────────────────────────────────────────────────────────────
  // 4. PASSPORT - Flujo de Tramitación de Pasaportes
  // ──────────────────────────────────────────────────────────────────────────
  {
    "systemName": "PASSPORT",
    "funnelName": "Flujo de Tramitación de Pasaporte",
    "description": "Proceso completo de solicitud y emisión de pasaportes",
    "active": true,
    "createdAt": new Date(),
    "updatedAt": new Date(),
    "createdBy": "system",
    "steps": [
      {
        "order": 1,
        "eventType": "SOLICITUD_PASAPORTE",
        "label": "Solicitud Iniciada",
        "description": "Ciudadano inicia solicitud de pasaporte"
      },
      {
        "order": 2,
        "eventType": "VALIDACION_DOCUMENTOS",
        "label": "Documentos Validados",
        "description": "Documentos presentados son validados"
      },
      {
        "order": 3,
        "eventType": "APROBACION_PASAPORTE",
        "label": "Pasaporte Aprobado",
        "description": "Solicitud es aprobada por autoridades"
      },
      {
        "order": 4,
        "eventType": "EMISION_PASAPORTE",
        "label": "Pasaporte Emitido",
        "description": "Pasaporte físico es emitido y entregado"
      }
    ]
  },

  // ──────────────────────────────────────────────────────────────────────────
  // 5. CITA_QUINTANAROO - Flujo de Citas Quintana Roo
  // ──────────────────────────────────────────────────────────────────────────
  {
    "systemName": "CITA_QUINTANAROO",
    "funnelName": "Flujo de Citas Quintana Roo",
    "description": "Sistema de agendamiento de citas para oficina de Quintana Roo",
    "active": true,
    "createdAt": new Date(),
    "updatedAt": new Date(),
    "createdBy": "system",
    "steps": [
      {
        "order": 1,
        "eventType": "AUTH_LOGIN",
        "label": "Inicio de Sesión",
        "description": "Usuario inicia sesión"
      },
      {
        "order": 2,
        "eventType": "CONSULTA_DISPONIBILIDAD",
        "label": "Consulta de Disponibilidad",
        "description": "Usuario consulta horarios disponibles"
      },
      {
        "order": 3,
        "eventType": "RESERVA_CITA",
        "label": "Cita Reservada",
        "description": "Usuario reserva una cita disponible"
      },
      {
        "order": 4,
        "eventType": "CONFIRMACION_CITA",
        "label": "Cita Confirmada",
        "description": "Sistema confirma la reserva de la cita"
      }
    ]
  }

]);

// Verificar inserción
print("\n✅ Funnel Templates insertados exitosamente:");
db.funnel_templates.find({}, { systemName: 1, funnelName: 1, active: 1 }).pretty();

// Crear índice único en systemName
db.funnel_templates.createIndex({ "systemName": 1 }, { unique: true });

print("\n📊 Total de templates activos:", db.funnel_templates.countDocuments({ active: true }));
```

## 🔍 Queries de Verificación

### Listar todos los sistemas configurados

```javascript
db.funnel_templates.find(
  { active: true },
  { systemName: 1, funnelName: 1, _id: 0 }
).sort({ systemName: 1 });
```

### Ver configuración de un sistema específico

```javascript
db.funnel_templates.findOne({ systemName: "TRUSTVALUE" });
```

### Actualizar un template existente

```javascript
db.funnel_templates.updateOne(
  { systemName: "TRUSTVALUE" },
  {
    $set: {
      updatedAt: new Date(),
      description: "Nueva descripción actualizada"
    }
  }
);
```

### Desactivar un template sin eliminarlo

```javascript
db.funnel_templates.updateOne(
  { systemName: "TICKETS" },
  {
    $set: {
      active: false,
      updatedAt: new Date()
    }
  }
);
```

### Agregar un nuevo sistema dinámicamente

```javascript
db.funnel_templates.insertOne({
  "systemName": "NUEVO_SISTEMA",
  "funnelName": "Flujo de Nuevo Sistema",
  "description": "Descripción del nuevo flujo",
  "active": true,
  "createdAt": new Date(),
  "updatedAt": new Date(),
  "createdBy": "admin",
  "steps": [
    {
      "order": 1,
      "eventType": "PASO_1",
      "label": "Primer Paso",
      "description": "Descripción del primer paso"
    },
    {
      "order": 2,
      "eventType": "PASO_2",
      "label": "Segundo Paso",
      "description": "Descripción del segundo paso"
    }
  ]
});
```

## 🚀 Ejecución del Script

### Opción 1: MongoDB Shell (mongosh)

```bash
# Conectar a MongoDB
mongosh "mongodb://localhost:27017/logs_system"

# Ejecutar el script (copiar y pegar el contenido)
```

### Opción 2: MongoDB Compass

1. Conectar a la base de datos `logs_system`
2. Ir a la colección `funnel_templates`
3. Usar la opción "Import Data" o usar "mongosh" desde Compass
4. Pegar el script de inserción

### Opción 3: Comando desde archivo

```bash
mongosh logs_system < funnel_templates_seed.js
```

## ✅ Validación Post-Inserción

Después de ejecutar el script, verifica que todo se haya insertado correctamente:

```javascript
// Contar templates activos
db.funnel_templates.countDocuments({ active: true });
// Debería retornar: 5

// Listar sistemas
db.funnel_templates.distinct("systemName");
// Debería retornar: ["CITA_GUYANA", "CITA_QUINTANAROO", "PASSPORT", "TICKETS", "TRUSTVALUE"]
```

## 🔐 Seguridad

**IMPORTANTE:** 
- Los templates NO están segregados por tenant en este diseño inicial
- Si necesitas configuraciones diferentes por tenant, agrega el campo `tenantId` a cada documento
- Modifica el repositorio para filtrar por `tenantId` en las consultas

## 📚 Referencias

- **Entidad:** `backlogs.dinamico.model.analytics.FunnelTemplate`
- **Repositorio:** `backlogs.dinamico.repository.analytics.FunnelTemplateRepository`
- **Servicio:** `backlogs.dinamico.service.analytics.FunnelAnalyticsService`
- **Endpoint:** `GET /api/analytics/funnel/{systemName}`

---

**Última actualización:** 2026-06-16  
**Autor:** Sistema de Analytics - Grupo Santoro

