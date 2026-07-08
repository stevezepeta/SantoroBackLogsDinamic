# ✅ CHECKLIST DE IMPLEMENTACIÓN

## 🎯 PREPARACIÓN (1-2 días)

### Revisar Documentación

- [ ] Leer `RESUMEN_EJECUTIVO.md` completo
- [ ] Revisar `ARQUITECTURA_UNIVERSAL_DEFINITIVA.md`
- [ ] Entender concepto de plugins
- [ ] Revisar ejemplos de TRUSTVALUE y TICKETS

### Decisiones Técnicas

- [ ] Confirmar formato de plugins (JSON recomendado)
- [ ] Decidir estrategia de caché de KPIs (recomendado: 5-10 min)
- [ ] Confirmar scheduler de análisis (recomendado: cada hora)
- [ ] Definir ubicación de archivos de plugins (`/resources/plugins/`)

---

## 📦 FASE 1 - SEMANA 1: PLUGIN ENGINE (5-7 días)

### Día 1-2: Modelos y Repository

- [ ] Crear `backlogs.dinamico.model.plugin.SystemPlugin`
- [ ] Crear clases internas: `EventTypeDefinition`, `KpiDefinition`, `AnomalyRule`, `DashboardWidget`
- [ ] Crear `SystemPluginRepository`
- [ ] Probar guardar/leer plugins de MongoDB

**Archivos:**
```
src/main/java/backlogs/dinamico/
├── model/plugin/
│   └── SystemPlugin.java
└── repository/plugin/
    └── SystemPluginRepository.java
```

### Día 3-4: Services

- [ ] Crear `PluginValidator` (validar estructura de plugins)
- [ ] Crear `PluginService` (cargar, listar, actualizar plugins)
- [ ] Implementar método `loadPluginFromFile()`
- [ ] Implementar método `listActivePlugins()`
- [ ] Probar validación con plugin inválido (debe fallar)
- [ ] Probar carga de plugin válido (debe funcionar)

**Archivos:**
```
src/main/java/backlogs/dinamico/service/plugin/
├── PluginService.java
└── PluginValidator.java
```

### Día 5: Controller y API

- [ ] Crear `PluginController`
- [ ] Implementar `GET /api/plugins` (listar)
- [ ] Implementar `GET /api/plugins/{systemId}` (detalle)
- [ ] Implementar `POST /api/plugins/upload` (cargar JSON)
- [ ] Implementar `PUT /api/plugins/{id}` (actualizar)
- [ ] Implementar `DELETE /api/plugins/{id}` (desactivar)
- [ ] Probar con Postman/curl todos los endpoints

**Archivos:**
```
src/main/java/backlogs/dinamico/controller/plugin/
└── PluginController.java
```

### Día 6: Plugins Iniciales

- [ ] Crear directorio `src/main/resources/plugins/`
- [ ] Crear `trustvalue-plugin.json` (ver ejemplo en docs)
- [ ] Crear `tickets-plugin.json` (ver ejemplo en docs)
- [ ] Crear `PluginSeeder` (cargar plugins al inicio)
- [ ] Configurar tenant ID por defecto para seed
- [ ] Probar que plugins se cargan automáticamente al iniciar

**Archivos:**
```
src/main/resources/plugins/
├── trustvalue-plugin.json
└── tickets-plugin.json

src/main/java/backlogs/dinamico/seed/
└── PluginSeeder.java
```

### Día 7: Testing y Ajustes

- [ ] Probar CRUD completo de plugins
- [ ] Verificar validación funciona correctamente
- [ ] Cargar plugin por API (upload)
- [ ] Ver plugins activos en base de datos
- [ ] **MILESTONE:** Plugin Engine funcionando ✅

---

## 📊 FASE 1 - SEMANA 2: ANALYTICS ENGINE (5-7 días)

### Día 8-9: Modelos de Anomalías

- [ ] Crear `backlogs.dinamico.model.analytics.DetectedAnomaly`
- [ ] Crear `DetectedAnomalyRepository`
- [ ] Probar guardar/leer anomalías

**Archivos:**
```
src/main/java/backlogs/dinamico/
├── model/analytics/
│   └── DetectedAnomaly.java
└── repository/analytics/
    └── DetectedAnomalyRepository.java
```

### Día 10-11: Evaluador de Condiciones

- [ ] Crear `ConditionEvaluator`
- [ ] Implementar `evaluateSimpleCondition()` (ej: `eventType='VALOR'`)
- [ ] Implementar `evaluateLogicalCondition()` (AND/OR)
- [ ] Implementar `evaluateCountCondition()` (ej: `COUNT(...) > 100`)
- [ ] Probar con condiciones reales de plugins
- [ ] Verificar que detecta correctamente

**Archivos:**
```
src/main/java/backlogs/dinamico/service/analytics/
├── ConditionEvaluator.java
├── EvaluationContext.java
└── EvaluationResult.java
```

### Día 12-13: Analytics Engine Service

- [ ] Crear `AnalyticsEngineService`
- [ ] Implementar `analyzeSystem()` (analizar un sistema)
- [ ] Implementar `evaluateRule()` (evaluar una regla)
- [ ] Implementar `createAnomaly()` (crear registro de anomalía)
- [ ] Configurar `@Scheduled(cron = "0 0 * * * *")` (cada hora)
- [ ] Probar análisis manual primero
- [ ] Verificar scheduler funciona

**Archivos:**
```
src/main/java/backlogs/dinamico/service/analytics/
└── AnalyticsEngineService.java
```

### Día 14: Controller de Anomalías

- [ ] Crear `AnomaliesController`
- [ ] Implementar `GET /api/analytics/anomalies` (listar)
- [ ] Implementar `GET /api/analytics/anomalies/{id}` (detalle)
- [ ] Implementar `POST /{id}/acknowledge` (reconocer)
- [ ] Implementar `POST /{id}/resolve` (resolver)
- [ ] Implementar `POST /{id}/mark-false-positive`
- [ ] Probar con Postman

**Archivos:**
```
src/main/java/backlogs/dinamico/controller/analytics/
└── AnomaliesController.java
```

### Día 15: Testing Integral

- [ ] Crear logs de prueba de TRUSTVALUE (asistencia fuera de horario)
- [ ] Esperar análisis programado o ejecutar manualmente
- [ ] Verificar que se detecta anomalía
- [ ] Probar reconocer y resolver anomalía
- [ ] **MILESTONE:** Analytics Engine funcionando ✅

---

## 📈 FASE 1 - SEMANA 3: KPIs DINÁMICOS (5-7 días)

### Día 16-17: Calculador de KPIs

- [ ] Crear `KpiCalculatorService`
- [ ] Implementar `parseQuery()` (parsear query simple)
- [ ] Implementar `executeQuery()` (ejecutar en MongoDB)
- [ ] Soportar operación `COUNT`
- [ ] Soportar operación `COUNT_DISTINCT`
- [ ] Soportar operación `AVG`
- [ ] Soportar operación `SUM`
- [ ] Probar con queries de ejemplo

**Archivos:**
```
src/main/java/backlogs/dinamico/service/analytics/
├── KpiCalculatorService.java
├── KpiQuery.java
└── KpiValue.java
```

### Día 18: Dashboard KPI Service

- [ ] Crear `DashboardKpiService`
- [ ] Implementar `getDashboardKpis()`
- [ ] Integrar con `KpiCalculatorService`
- [ ] Probar con TRUSTVALUE
- [ ] Probar con TICKETS

**Archivos:**
```
src/main/java/backlogs/dinamico/service/analytics/
├── DashboardKpiService.java
└── DashboardKpiDto.java
```

### Día 19: Controller de KPIs

- [ ] Crear `KpiController`
- [ ] Implementar `GET /api/analytics/kpis/{systemId}`
- [ ] Parámetro opcional: `?date=2026-06-10`
- [ ] Probar API con Postman
- [ ] Verificar respuesta correcta

**Archivos:**
```
src/main/java/backlogs/dinamico/controller/analytics/
└── KpiController.java
```

### Día 20-21: Frontend (Integración)

- [ ] Crear componente React `DashboardKpis.jsx` (ver ejemplo en docs)
- [ ] Integrar con API `/api/analytics/kpis/{systemId}`
- [ ] Renderizar KPIs según `displayType`
- [ ] Probar con TRUSTVALUE
- [ ] Probar con TICKETS
- [ ] Verificar cambio dinámico entre sistemas

**Archivos:**
```
frontend/src/components/
├── DashboardKpis.jsx
└── KpiCard.jsx
```

### Día 22: Testing y Optimización

- [ ] Crear caché de KPIs (opcional pero recomendado)
- [ ] Configurar refresh automático (cada 5-10 min)
- [ ] Probar performance con volumen real de logs
- [ ] **MILESTONE:** KPIs Dinámicos funcionando ✅

---

## 🎉 FIN DE FASE 1 (3 semanas completas)

### Verificación Final

- [ ] Plugin Engine: Cargar y gestionar plugins ✅
- [ ] Analytics Engine: Detectar anomalías automáticamente ✅
- [ ] KPIs Dinámicos: Dashboard personalizado por sistema ✅
- [ ] Plugins de TRUSTVALUE y TICKETS funcionando ✅
- [ ] Anomalías se detectan cada hora ✅
- [ ] Dashboard muestra KPIs correctos ✅

### Demo para Stakeholders

- [ ] Preparar demo de sistema completo
- [ ] Mostrar dashboard de TRUSTVALUE
- [ ] Mostrar dashboard de TICKETS
- [ ] Demostrar detección de anomalía en vivo
- [ ] Explicar cómo agregar nuevo sistema (cargar JSON)
- [ ] Recopilar feedback

---

## 🚀 FASE 2 (OPCIONAL): EVA CONTEXT-AWARE (2 semanas)

### Semana 4: Eva Mejorada

- [ ] Eva lee metadata de plugins
- [ ] Explica eventos en términos de negocio
- [ ] Genera resúmenes ejecutivos por sistema
- [ ] Responde preguntas contextuales

### Semana 5: Reportes Automáticos

- [ ] Scheduler de reportes diarios
- [ ] Email automático a gerentes
- [ ] PDF con KPIs y anomalías
- [ ] Templates personalizables

---

## 💡 TIPS DE IMPLEMENTACIÓN

### Priorización

Si tienes poco tiempo, implementa en este orden:
1. **Plugin Engine** (crítico - base de todo)
2. **KPIs Dinámicos** (alto valor de negocio)
3. **Analytics Engine** (útil pero no bloqueante)
4. **Eva Context-Aware** (nice to have)

### Testing

- Usa logs reales de TRUSTVALUE y TICKETS para probar
- Crea datos de prueba si no tienes suficientes logs
- Verifica cada componente individualmente antes de integrar

### Performance

- Agrega índices en MongoDB:
  ```javascript
  db.log_events.createIndex({ "tenant_id": 1, "system": 1, "eventTime": -1 })
  db.system_plugins.createIndex({ "systemId": 1, "tenantId": 1 })
  db.detected_anomalies.createIndex({ "tenantId": 1, "status": 1, "detectedAt": -1 })
  ```

### Debugging

- Habilita logs de debug en `application.yml`:
  ```yaml
  logging:
    level:
      backlogs.dinamico.service.plugin: DEBUG
      backlogs.dinamico.service.analytics: DEBUG
  ```

---

## 📊 MÉTRICAS DE ÉXITO

### Al finalizar Fase 1, debes poder:

- [ ] Agregar un nuevo sistema en < 1 hora (solo crear JSON)
- [ ] Dashboard muestra KPIs específicos de cada sistema
- [ ] Anomalías se detectan automáticamente sin intervención
- [ ] Gerentes ven información de negocio (no técnica)
- [ ] Sistema escala a N sistemas sin cambio de código

---

## 🎯 PRÓXIMO PASO INMEDIATO

**AHORA MISMO:**

1. ✅ Revisa `RESUMEN_EJECUTIVO.md` (ya abierto)
2. ✅ Decide si aprobar arquitectura o ajustar
3. ✅ Si apruebas → Empezar Día 1: Crear modelos de plugins
4. ✅ Si ajustas → Dime qué cambiar

**Primera tarea concreta (30 minutos):**

Crear el archivo `SystemPlugin.java`:

```bash
# Ubicación:
# src/main/java/backlogs/dinamico/model/plugin/SystemPlugin.java

# Copiar código de: PLAN_IMPLEMENTACION_MODULAR.md
# Sección: "1. Modelo de Plugin"
```

---

**¿Listo para empezar?** 🚀 

Marca el primer checkbox y sigue la lista. ¡Éxito!

