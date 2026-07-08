# 🚀 ARQUITECTURA DEFINITIVA - Plataforma Universal de Logs

> **Sistema Modular de Observabilidad y Analytics para Múltiples Aplicaciones**

---

## 🎯 VISIÓN CORRECTA DEL SISTEMA

### ¿Qué ES el sistema?

**Una PLATAFORMA UNIVERSAL** que recibe logs de CUALQUIER tipo de aplicación/sistema y proporciona:

1. **Observabilidad Operacional** - Monitorear cómo funcionan las aplicaciones
2. **Analytics de Comportamiento** - Entender qué hacen los usuarios
3. **Detección Inteligente** - Identificar anomalías automáticamente
4. **Business Intelligence** - KPIs y métricas de negocio
5. **Auditoría y Compliance** - Registro forense cuando se necesite

### Usuarios del Sistema

**Gerentes / Supervisores / Analistas de Negocio**

Quieren responder preguntas como:
- "¿Cómo están usando la app TRUSTVALUE los empleados?"
- "¿Qué módulos del sistema de TICKETS son más usados?"
- "¿Hay algún patrón inusual que deba revisar?"
- "¿Cuántas asistencias se marcaron hoy vs ayer?"
- "¿Algún empleado está haciendo algo sospechoso?"

---

## 📊 SISTEMAS ACTUALES (Casos de Uso Reales)

### 1. TRUSTVALUE - App Móvil de Asistencias

**Tipo:** Mobile App  
**Usuarios:** Empleados de sucursales  
**Eventos que loguea:**
- Login/Logout
- Marcar asistencia (con geolocalización)
- Enviar evidencias (fotos, documentos)
- Ver comunicados
- Responder evaluaciones/encuestas
- Cambios de sucursal

**Lo que el gerente quiere ver:**
- ¿Cuántos empleados marcaron asistencia hoy?
- ¿Alguien marcó desde ubicación incorrecta?
- ¿Qué sucursales tienen más actividad?
- ¿Hay empleados marcando fuera de horario?

### 2. TICKETS - Sistema Web de Soporte

**Tipo:** Web App  
**Usuarios:** Agentes de soporte  
**Eventos que loguea:**
- Login/Navegación de módulos
- Crear ticket
- Ver tickets
- Resolver/Cerrar tickets
- Consultar clientes
- Generar reportes

**Lo que el gerente quiere ver:**
- ¿Cuántos tickets se resolvieron hoy?
- ¿Qué módulos usan más los agentes?
- ¿Hay agentes con baja productividad?
- ¿Alguien accediendo a datos sin autorización?

### 3. FUTUROS SISTEMAS

**Ejemplos potenciales:**
- Sistema de nómina
- ERP corporativo
- CRM de ventas
- App de inventarios
- Dispositivos IoT

**El sistema DEBE ser agnóstico** - funcionar para cualquier tipo.

---

## 🏗️ ARQUITECTURA MODULAR (Plugin-Based)

### Concepto Clave: ADAPTADORES POR TIPO DE SISTEMA

```
┌─────────────────────────────────────────────────────────────┐
│                    PLATAFORMA CORE                          │
│                                                             │
│  ┌─────────────┬─────────────┬─────────────┬─────────────┐ │
│  │   Ingesta   │   Storage   │  Analytics  │   Dashboard │ │
│  │   de Logs   │   MongoDB   │    Engine   │     UI      │ │
│  └─────────────┴─────────────┴─────────────┴─────────────┘ │
└─────────────────────────────────────────────────────────────┘
                          ▲
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
   ┌────▼─────┐     ┌────▼─────┐     ┌────▼─────┐
   │ PLUGIN   │     │ PLUGIN   │ ... │ PLUGIN   │
   │ TRUSTVAL │     │ TICKETS  │     │  CUSTOM  │
   └──────────┘     └──────────┘     └──────────┘
        │                 │                 │
   ┌────▼─────┐     ┌────▼─────┐     ┌────▼─────┐
   │  App     │     │  Web     │     │  Otro    │
   │  Móvil   │     │  App     │     │  Sistema │
   └──────────┘     └──────────┘     └──────────┘
```

### ¿Qué es un PLUGIN?

Un **adaptador de configuración** que define:

1. **Eventos específicos del sistema** (qué significan)
2. **KPIs relevantes** (métricas de negocio)
3. **Reglas de detección** (qué es "anormal" para ESTE sistema)
4. **Vistas del dashboard** (qué mostrar al gerente)

**NO es código** - es CONFIGURACIÓN en JSON/YAML.

---

## 🔌 EJEMPLO DE PLUGIN: TRUSTVALUE

```json
{
  "systemId": "TRUSTVALUE",
  "displayName": "TrustValue - Asistencias",
  "type": "MOBILE_APP",
  "version": "1.0",
  
  "eventTypes": {
    "AUTH_LOGIN": {
      "displayName": "Inicio de Sesión",
      "category": "AUTHENTICATION",
      "icon": "🔐",
      "businessImpact": "LOW"
    },
    "ASISTENCIA_MARCADA": {
      "displayName": "Asistencia Registrada",
      "category": "CORE_OPERATION",
      "icon": "✅",
      "businessImpact": "HIGH",
      "requiredFields": ["sucursal", "geo"]
    },
    "EVIDENCIA_ENVIADA": {
      "displayName": "Evidencia Subida",
      "category": "DOCUMENT_SUBMISSION",
      "icon": "📎",
      "businessImpact": "MEDIUM"
    }
  },
  
  "kpis": [
    {
      "id": "asistencias_dia",
      "name": "Asistencias del Día",
      "query": "COUNT events WHERE eventType=ASISTENCIA_MARCADA AND date=TODAY",
      "displayType": "NUMBER_CARD",
      "icon": "👥"
    },
    {
      "id": "cobertura_sucursales",
      "name": "Cobertura de Sucursales",
      "query": "COUNT DISTINCT location WHERE eventType=ASISTENCIA_MARCADA",
      "displayType": "PERCENTAGE",
      "target": 100
    },
    {
      "id": "asistencias_fuera_ubicacion",
      "name": "Marcadas Fuera de Ubicación",
      "query": "COUNT WHERE eventType=ASISTENCIA_MARCADA AND geo.distance > 500m",
      "displayType": "ALERT_COUNT",
      "severity": "WARNING"
    }
  ],
  
  "anomalyRules": [
    {
      "id": "asistencia_fuera_horario",
      "name": "Asistencia fuera de horario laboral",
      "condition": "eventType=ASISTENCIA_MARCADA AND (hour < 6 OR hour > 22)",
      "riskScore": 60,
      "severity": "MEDIUM",
      "description": "Empleado marcó asistencia fuera del horario normal"
    },
    {
      "id": "multiples_asistencias_dia",
      "name": "Múltiples asistencias en un día",
      "condition": "COUNT(ASISTENCIA_MARCADA per actor per day) > 2",
      "riskScore": 75,
      "severity": "HIGH",
      "description": "Empleado marcó asistencia más de 2 veces en el mismo día"
    },
    {
      "id": "asistencia_ubicacion_incorrecta",
      "name": "Asistencia desde ubicación no autorizada",
      "condition": "eventType=ASISTENCIA_MARCADA AND geo.distance(sucursal_asignada) > 1km",
      "riskScore": 85,
      "severity": "HIGH",
      "description": "Asistencia marcada lejos de la sucursal asignada"
    }
  ],
  
  "dashboardWidgets": [
    {
      "type": "MAP_HEATMAP",
      "title": "Mapa de Asistencias",
      "dataSource": "geo.coordinates WHERE eventType=ASISTENCIA_MARCADA"
    },
    {
      "type": "TIME_SERIES",
      "title": "Asistencias por Hora",
      "dataSource": "COUNT(ASISTENCIA_MARCADA) GROUP BY hour"
    },
    {
      "type": "TOP_LOCATIONS",
      "title": "Sucursales Más Activas",
      "dataSource": "COUNT GROUP BY location.name"
    }
  ]
}
```

---

## 🔌 EJEMPLO DE PLUGIN: TICKETS

```json
{
  "systemId": "TICKETS",
  "displayName": "Sistema de Tickets",
  "type": "WEB_APP",
  "version": "1.0",
  
  "eventTypes": {
    "CONSULTA_DE_CLIENTES": {
      "displayName": "Consulta de Cliente",
      "category": "DATA_ACCESS",
      "icon": "👤",
      "businessImpact": "LOW",
      "sensitive": true
    },
    "CREACION_DE_TICKET": {
      "displayName": "Ticket Creado",
      "category": "CORE_OPERATION",
      "icon": "🎫",
      "businessImpact": "HIGH"
    },
    "RESOLUCION_DE_TICKET": {
      "displayName": "Ticket Resuelto",
      "category": "CORE_OPERATION",
      "icon": "✅",
      "businessImpact": "HIGH"
    }
  },
  
  "kpis": [
    {
      "id": "tickets_resueltos_dia",
      "name": "Tickets Resueltos Hoy",
      "query": "COUNT events WHERE eventType=RESOLUCION_DE_TICKET AND date=TODAY",
      "displayType": "NUMBER_CARD",
      "icon": "✅"
    },
    {
      "id": "tiempo_promedio_resolucion",
      "name": "Tiempo Promedio de Resolución",
      "query": "AVG(sla.elapsedSeconds) WHERE eventType=RESOLUCION_DE_TICKET",
      "displayType": "DURATION",
      "unit": "minutes"
    },
    {
      "id": "backlog_tickets",
      "name": "Tickets Pendientes",
      "query": "COUNT DISTINCT caseId WHERE status=PENDING",
      "displayType": "NUMBER_CARD",
      "severity": "INFO"
    }
  ],
  
  "anomalyRules": [
    {
      "id": "acceso_masivo_clientes",
      "name": "Consulta masiva de clientes",
      "condition": "COUNT(CONSULTA_DE_CLIENTES per actor per hour) > 50",
      "riskScore": 80,
      "severity": "HIGH",
      "description": "Agente consultó datos de muchos clientes en poco tiempo"
    },
    {
      "id": "resolucion_muy_rapida",
      "name": "Tickets resueltos sospechosamente rápido",
      "condition": "eventType=RESOLUCION_DE_TICKET AND sla.elapsedSeconds < 60",
      "riskScore": 65,
      "severity": "MEDIUM",
      "description": "Ticket marcado como resuelto en menos de 1 minuto"
    }
  ],
  
  "dashboardWidgets": [
    {
      "type": "FUNNEL",
      "title": "Embudo de Tickets",
      "stages": ["CREACION", "ASIGNACION", "EN_PROCESO", "RESOLUCION"]
    },
    {
      "type": "AGENT_PERFORMANCE",
      "title": "Performance de Agentes",
      "metrics": ["tickets_resueltos", "tiempo_promedio", "satisfaccion"]
    }
  ]
}
```

---

## 🎯 COMPONENTES CORE DE LA PLATAFORMA

### 1. **Plugin Engine** (NUEVO)

Componente que:
- Carga plugins desde archivos JSON/YAML
- Valida estructura del plugin
- Registra en catálogo de sistemas
- Aplica configuración al motor de analytics

### 2. **Analytics Engine Configurable** (MEJORAR)

En lugar de reglas hardcoded, usa configuración de plugins:
- Lee `anomalyRules` del plugin
- Evalúa condiciones dinámicamente
- Calcula risk scores configurables
- Genera alertas según plugin

### 3. **Dashboard Builder Dinámico** (NUEVO)

Genera vistas automáticamente según plugin:
- Lee `dashboardWidgets` del plugin
- Renderiza componentes configurados
- Muestra KPIs definidos en plugin
- Filtros específicos por sistema

### 4. **Eva Context-Aware** (MEJORAR)

Eva conoce el contexto del sistema:
- Sabe qué significan los eventos de cada sistema
- Explica en lenguaje de negocio (no técnico)
- Sugiere acciones relevantes por tipo de sistema

---

## 📋 PLAN DE IMPLEMENTACIÓN (Paso a Paso)

### FASE 1: FUNDAMENTOS (2-3 semanas)

#### Semana 1: Plugin Engine

**Objetivo:** Sistema pueda leer y cargar plugins

1. Crear modelo `SystemPlugin`
2. Service para cargar plugins desde JSON
3. Validador de estructura de plugin
4. API para CRUD de plugins
5. Integrar con sistema existente

**Entregables:**
- ✅ Plugins de TRUSTVALUE y TICKETS funcionando
- ✅ Endpoint `/api/plugins` para gestionar
- ✅ Validación automática de plugins

#### Semana 2: Analytics Engine Configurable

**Objetivo:** Detección de anomalías usando reglas de plugins

1. Motor de evaluación de condiciones dinámicas
2. Calculador de risk scores configurable
3. Generador de alertas por plugin
4. Dashboard de anomalías por sistema

**Entregables:**
- ✅ Anomalías detectadas según reglas de plugin
- ✅ Risk scores automáticos
- ✅ Alertas configurables

#### Semana 3: KPIs Dinámicos

**Objetivo:** Dashboard muestra KPIs definidos en plugins

1. Query builder dinámico (interpreta queries del plugin)
2. Service de cálculo de KPIs
3. Widgets configurables en frontend
4. Caché de KPIs pre-calculados

**Entregables:**
- ✅ Dashboard muestra KPIs de TRUSTVALUE
- ✅ Dashboard muestra KPIs de TICKETS
- ✅ Actualización automática cada X minutos

---

### FASE 2: EVA INTELIGENTE (2 semanas)

#### Semana 4: Eva Context-Aware

**Objetivo:** Eva entiende cada sistema específicamente

1. Eva lee metadata de plugins
2. Explica eventos en términos de negocio
3. Sugiere acciones según tipo de sistema
4. Resúmenes ejecutivos por sistema

**Entregables:**
- ✅ Eva explica "ASISTENCIA_MARCADA" como negocio
- ✅ Sugerencias contextuales
- ✅ Resumen diario por sistema

#### Semana 5: Reportes Automáticos

1. Scheduler de reportes por sistema
2. Templates configurables
3. Email/PDF automáticos
4. Dashboards exportables

---

### FASE 3: OPTIMIZACIONES (1-2 semanas)

#### Semana 6: Performance y UX

1. Caché inteligente de queries
2. Pre-cálculo de agregaciones
3. UI mejorada para multi-sistema
4. Testing con gerentes reales

---

## 🎨 MEJORAS A EVA (Específico)

### Eva Actual (Limitaciones):
- ❌ Solo responde preguntas generales
- ❌ No entiende contexto de negocio
- ❌ Explicaciones técnicas (no de negocio)

### Eva Mejorada (Propuesta):

#### 1. **Eva Descriptiva**

```
Gerente: "Eva, ¿qué pasó hoy en TRUSTVALUE?"

Eva (antes): "Hoy se registraron 398 eventos en el sistema TRUSTVALUE"

Eva (después): "Hoy 145 empleados marcaron asistencia en 12 sucursales. 
La sucursal 'Centro' tuvo la mayor actividad con 45 asistencias.
Detecté 3 anomalías:
- José Pérez marcó desde 5km fuera de su sucursal asignada
- 2 empleados marcaron después de las 10 PM
Todo lo demás operó normalmente."
```

#### 2. **Eva Analítica**

```
Gerente: "¿Por qué bajaron las asistencias esta semana?"

Eva: "Analicé los datos y encontré 3 causas probables:
1. Lunes fue día festivo (0 asistencias esperado)
2. Sucursal 'Norte' estuvo cerrada martes y miércoles
3. 8 empleados están de vacaciones esta semana

Comparado con semana normal: -32% es explicable por estos factores."
```

#### 3. **Eva Predictiva** (Fase 2)

```
Eva (proactiva): "Gerente, detecto un patrón inusual:

En los últimos 5 días, el agente 'Carlos M.' ha resuelto tickets
3x más rápido que el promedio del equipo (2 min vs 8 min).

Esto podría significar:
1. ✅ Es muy eficiente (bueno)
2. ⚠️ Está cerrando sin resolver bien (revisar calidad)

Recomiendo revisar 5 tickets al azar de Carlos para validar calidad."
```

---

## 🚀 ESCALABILIDAD

### ¿Cómo agregar un NUEVO sistema?

**Proceso simple:**

1. Crear archivo JSON del plugin (ej: `mi-sistema-plugin.json`)
2. Definir eventos, KPIs, reglas de anomalías
3. Subir plugin vía API o UI
4. Sistema automáticamente:
   - Registra el nuevo sistema
   - Aplica reglas de analytics
   - Genera dashboard con KPIs
   - Eva lo entiende inmediatamente

**¡Sin tocar código backend!**

### Ejemplo: Agregar Sistema de Inventarios

```json
{
  "systemId": "INVENTARIO",
  "displayName": "Control de Inventarios",
  "type": "ERP_MODULE",
  
  "eventTypes": {
    "ENTRADA_PRODUCTO": {...},
    "SALIDA_PRODUCTO": {...},
    "AJUSTE_INVENTARIO": {...}
  },
  
  "kpis": [...]  // KPIs específicos de inventario
}
```

Listo — ya funciona.

---

## 📊 COMPARACIÓN: ANTES vs DESPUÉS

| Aspecto | Antes (Actual) | Después (Con Plugins) |
|---------|---------------|----------------------|
| **Agregar sistema nuevo** | Modificar código backend | Subir archivo JSON |
| **Tiempo para nuevo sistema** | 1-2 semanas desarrollo | 1 hora configuración |
| **Flexibilidad** | Hardcoded por sistema | Configurable dinámicamente |
| **KPIs** | Genéricos (logs, errores) | Específicos por negocio |
| **Eva** | Explicaciones técnicas | Contexto de negocio |
| **Escalabilidad** | Limitada | Infinita |
| **Mantenimiento** | Cada cambio = código | Solo actualizar JSON |

---

## ✅ CHECKLIST DE DECISIONES

Antes de empezar a codear, confirma:

- [ ] ¿Aprobar arquitectura de plugins?
- [ ] ¿Formato JSON o YAML para plugins?
- [ ] ¿Prioridad Fase 1 completa o solo Plugin Engine?
- [ ] ¿Eva debe ser proactiva (notificar sin preguntar)?
- [ ] ¿Necesitas demo visual de cómo funcionarían los plugins?

---

## 🎯 PRÓXIMO PASO

**Opción A:** Aprobar estrategia y empezar con código del Plugin Engine

**Opción B:** Ajustar algo de la arquitectura primero

**Opción C:** Ver mockups/wireframes de cómo se vería

**¿Qué prefieres?** Una vez confirmes, empiezo con el código.

