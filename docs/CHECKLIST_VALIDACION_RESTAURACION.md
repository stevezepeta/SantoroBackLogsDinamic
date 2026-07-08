# ✅ Checklist de Validación Post-Restauración

**Fecha:** 2026-06-15  
**Sistema:** Backend Principal de Logs (Puerto 8040)  
**Base de datos:** logs_system

---

## 📋 Checklist Rápido

### ☑️ **Fase 1: Verificación de Configuración**

- [ ] **1.1** Ejecutar script de verificación
  ```powershell
  .\verificar-config-principal.ps1
  ```
  - **Resultado esperado:** `ESTADO: PERFECTO` (sin errores)

- [ ] **1.2** Verificar archivos de configuración modificados
  - [ ] `application.yml` - Puerto 8040 ✓
  - [ ] `application.yml` - Base datos logs_system ✓
  - [ ] `application.properties` - WebSocket correcto ✓
  - [ ] `application-dev.properties` - logs_system ✓
  - [ ] `application-dev.yml` - logs_system ✓
  - [ ] `application-prod.properties` - Puerto 8040 ✓

- [ ] **1.3** Verificar ausencia de referencias a Quintana Roo
  - [ ] No hay referencias a "logsQR" ✓
  - [ ] No hay referencias al puerto "8057" ✓
  - [ ] Nombre de correo correcto (no dice "Quintana Roo") ✓

---

### ☑️ **Fase 2: Compilación**

- [ ] **2.1** Limpiar compilaciones anteriores
  ```powershell
  .\mvnw.cmd clean
  ```

- [ ] **2.2** Compilar el proyecto
  ```powershell
  .\mvnw.cmd package -DskipTests
  ```
  - **Resultado esperado:** `BUILD SUCCESS`

- [ ] **2.3** Verificar JAR generado
  ```powershell
  Test-Path target\dinamico-0.0.1-SNAPSHOT.jar
  ```
  - **Resultado esperado:** `True`

---

### ☑️ **Fase 3: Ejecución Local (Desarrollo)**

- [ ] **3.1** Configurar variables de entorno (opcional)
  ```powershell
  .\config-windows.ps1
  ```

- [ ] **3.2** Iniciar el servidor
  ```powershell
  .\iniciar-backend-principal.ps1 -Development
  ```
  ó
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```

- [ ] **3.3** Verificar inicio exitoso
  - [ ] Servidor arranca sin errores
  - [ ] Puerto correcto: `8040`
  - [ ] Conexión a MongoDB exitosa
  - [ ] Mensaje: `Started BacklogsApplication` en consola

---

### ☑️ **Fase 4: Verificación de Endpoints**

- [ ] **4.1** Swagger UI accesible
  ```
  http://localhost:8040/swagger-ui.html
  ```
  - **Resultado esperado:** Página de Swagger carga correctamente

- [ ] **4.2** API Docs disponible
  ```
  http://localhost:8040/v3/api-docs
  ```
  - **Resultado esperado:** JSON de documentación

- [ ] **4.3** Endpoint de sistemas disponibles (Funnel Analytics)
  ```powershell
  Invoke-RestMethod -Uri "http://localhost:8040/api/analytics/funnel/available-systems" `
    -Headers @{"Authorization"="Bearer TOKEN"; "X-Tenant"="TENANT"}
  ```
  - **Resultado esperado:** Lista de sistemas configurados

- [ ] **4.4** Dashboard Stats
  ```
  GET http://localhost:8040/api/logs/dashboard/stats
  ```

- [ ] **4.5** Health Check (si está configurado)
  ```
  GET http://localhost:8040/actuator/health
  ```

---

### ☑️ **Fase 5: Verificación de Base de Datos**

- [ ] **5.1** Conectar a MongoDB
  ```bash
  mongo mongodb://localhost:27017/logs_system
  ```

- [ ] **5.2** Verificar colecciones
  ```javascript
  show collections
  ```
  - **Debe incluir:** `log_events`

- [ ] **5.3** Verificar documentos de prueba (si existen)
  ```javascript
  db.log_events.findOne()
  ```

- [ ] **5.4** Verificar índices
  ```javascript
  db.log_events.getIndexes()
  ```
  - **Debe incluir índices compuestos** por tenant_id, system, etc.

---

### ☑️ **Fase 6: Verificación de WebSocket**

- [ ] **6.1** Verificar configuración de orígenes
  - [ ] Desarrollo: `localhost:3000` habilitado
  - [ ] Producción: `dashboard-api.grupo-santoro.com.mx` configurado

- [ ] **6.2** Probar conexión WebSocket (si aplica)
  ```javascript
  const ws = new WebSocket('ws://localhost:8040/ws');
  ws.onopen = () => console.log('Conectado');
  ```

---

### ☑️ **Fase 7: Testing Funcional**

- [ ] **7.1** Test de Funnel Analytics (si hay token disponible)
  ```powershell
  .\test-funnel-analytics.ps1 -Token "JWT_TOKEN" -Tenant "tenant_name"
  ```
  - **Resultado esperado:** Tests pasan exitosamente

- [ ] **7.2** Crear un log de prueba (si hay API key)
  ```powershell
  Invoke-RestMethod -Uri "http://localhost:8040/api/logs/ingest" `
    -Method POST `
    -Headers @{"X-Api-Key"="API_KEY"; "X-Tenant"="TENANT"} `
    -Body '{"system":"TEST","eventType":"TEST_EVENT","message":"Test"}'
  ```

- [ ] **7.3** Consultar logs (si hay JWT)
  ```powershell
  Invoke-RestMethod -Uri "http://localhost:8040/api/logs/dashboard/stats" `
    -Headers @{"Authorization"="Bearer TOKEN"; "X-Tenant"="TENANT"}
  ```

---

### ☑️ **Fase 8: Verificación de Logs del Sistema**

- [ ] **8.1** Revisar logs de inicio
  - [ ] No hay errores de conexión a MongoDB
  - [ ] No hay warnings críticos
  - [ ] Todos los beans se inicializaron correctamente

- [ ] **8.2** Verificar logs de WebSocket
  - [ ] Configuración de orígenes cargada correctamente

- [ ] **8.3** Verificar logs de seguridad
  - [ ] JWT configurado correctamente
  - [ ] API Keys funcionando

---

### ☑️ **Fase 9: Despliegue en Producción (Opcional)**

- [ ] **9.1** Copiar JAR al servidor
  ```bash
  scp target/dinamico-0.0.1-SNAPSHOT.jar usuario@servidor:/opt/backlogs/
  ```

- [ ] **9.2** Configurar variables de entorno en servidor
  - [ ] `MONGODB_URI` apuntando a logs_system
  - [ ] `SERVER_PORT=8040`
  - [ ] `JWT_SECRET` seguro y único
  - [ ] `WEBSOCKET_ALLOWED_ORIGINS` con dominios correctos

- [ ] **9.3** Crear servicio systemd (Linux) o servicio de Windows
  ```bash
  # Ver: docs/RESTAURACION_CONFIG_PRINCIPAL.md
  ```

- [ ] **9.4** Iniciar servicio
  ```bash
  java -jar dinamico-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
  ```

- [ ] **9.5** Verificar acceso externo
  ```
  http://IP_SERVIDOR:8040/swagger-ui.html
  ```

- [ ] **9.6** Configurar firewall (si aplica)
  - [ ] Abrir puerto 8040

- [ ] **9.7** Configurar nginx/Apache como reverse proxy (si aplica)

---

### ☑️ **Fase 10: Monitoreo Post-Despliegue**

- [ ] **10.1** Monitorear logs del servidor (primeras 24h)
  - [ ] No hay errores recurrentes
  - [ ] Uso de memoria estable
  - [ ] Uso de CPU normal

- [ ] **10.2** Verificar conexiones a base de datos
  - [ ] Pool de conexiones funcionando correctamente
  - [ ] No hay timeout de conexiones

- [ ] **10.3** Verificar tráfico de red
  - [ ] WebSocket funcionando correctamente
  - [ ] HTTP requests respondiendo normalmente

- [ ] **10.4** Verificar espacio en disco
  - [ ] Suficiente espacio para logs
  - [ ] Base de datos no está cerca del límite

---

## 📊 Resumen de Estado

| Fase | Descripción | Estado | Notas |
|------|-------------|--------|-------|
| 1 | Verificación de Configuración | ⬜ | |
| 2 | Compilación | ⬜ | |
| 3 | Ejecución Local | ⬜ | |
| 4 | Verificación de Endpoints | ⬜ | |
| 5 | Verificación de Base de Datos | ⬜ | |
| 6 | Verificación de WebSocket | ⬜ | |
| 7 | Testing Funcional | ⬜ | |
| 8 | Verificación de Logs | ⬜ | |
| 9 | Despliegue en Producción | ⬜ | Opcional |
| 10 | Monitoreo Post-Despliegue | ⬜ | Opcional |

**Leyenda:**
- ⬜ Pendiente
- ✅ Completado
- ⚠️ Con advertencias
- ❌ Error

---

## 🔧 Troubleshooting Rápido

### Problema: Servidor no arranca

**Solución:**
1. Verificar que el puerto 8040 no esté en uso:
   ```powershell
   Get-NetTCPConnection -LocalPort 8040
   ```
2. Revisar logs de inicio para identificar el error
3. Verificar conectividad con MongoDB

### Problema: No se conecta a MongoDB

**Solución:**
1. Verificar que MongoDB esté corriendo:
   ```powershell
   Get-Service MongoDB  # Windows
   ```
2. Verificar URI de conexión en variables de entorno
3. Probar conexión manual:
   ```bash
   mongo mongodb://localhost:27017/logs_system
   ```

### Problema: Endpoints devuelven 401 Unauthorized

**Solución:**
1. Verificar que el JWT sea válido
2. Verificar header `Authorization: Bearer TOKEN`
3. Verificar header `X-Tenant: nombre_tenant`

### Problema: WebSocket no se conecta

**Solución:**
1. Verificar configuración de `WEBSOCKET_ALLOWED_ORIGINS`
2. Verificar que el frontend use el protocolo correcto (`ws://` o `wss://`)
3. Revisar logs del servidor para errores de CORS

---

## 📝 Notas Finales

### ✅ Si Todo Funciona Correctamente:

1. **Marcar este checklist como completado**
2. **Documentar cualquier configuración adicional aplicada**
3. **Hacer backup de la configuración actual**
4. **Comunicar al equipo que el sistema está operativo**
5. **Configurar monitoreo continuo**

### ⚠️ Si Hay Problemas:

1. **Documentar el error específico**
2. **Consultar:** `docs/RESTAURACION_CONFIG_PRINCIPAL.md`
3. **Revisar logs:** Sección de Troubleshooting
4. **Contactar soporte:** soporte.tecnico@grupo-santoro.com.mx

---

## 📞 Contacto y Soporte

**Email:** soporte.tecnico@grupo-santoro.com.mx  
**Documentación:** `docs/RESTAURACION_CONFIG_PRINCIPAL.md`  
**Swagger UI:** `http://localhost:8040/swagger-ui.html`

---

**Fecha de Validación:** _________________  
**Validado por:** _________________  
**Firma:** _________________

---

**Estado Final:** ⬜ TODO CORRECTO - Sistema operativo en puerto 8040 con base de datos logs_system

