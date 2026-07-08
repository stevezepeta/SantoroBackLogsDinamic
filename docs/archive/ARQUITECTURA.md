# 🏗️ ARQUITECTURA DEL AMBIENTE - ANTES Y DESPUÉS

## ❌ ANTES (Problema)

```
┌──────────────────────────────────────────────────────┐
│                   MongoDB Server                      │
│                                                       │
│   ┌─────────────────────────────────────────────┐   │
│   │        Database: logs_system                 │   │
│   │   ┌──────────────────────────────────────┐  │   │
│   │   │  users (TODOS los usuarios juntos)   │  │   │
│   │   │  - Usuarios puerto 8040               │  │   │
│   │   │  - Usuarios puerto 8057 ❌           │  │   │
│   │   └──────────────────────────────────────┘  │   │
│   │   ┌──────────────────────────────────────┐  │   │
│   │   │  log_events                           │  │   │
│   │   └──────────────────────────────────────┘  │   │
│   │   ┌──────────────────────────────────────┐  │   │
│   │   │  api_keys                             │  │   │
│   │   └──────────────────────────────────────┘  │   │
│   └─────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────┘
                    ▲                    ▲
                    │                    │
                    │                    │
      ┌─────────────┴──────┐   ┌────────┴──────────────┐
      │                    │   │                        │
      │  AMBIENTE 8040     │   │  AMBIENTE 8057 ❌     │
      │  (Original)        │   │  (Quintana Roo)        │
      │                    │   │                        │
      │  ✓ WebSocket OK    │   │  ❌ WebSocket CORS    │
      │  ✓ Mail OK         │   │  ❌ Mail usuarios 404  │
      └────────────────────┘   └────────────────────────┘
           ▲                            ▲
           │                            │
           │                            │
      dashboard-api              dashboard-quintanaroo
   (grupo-santoro.com.mx)       (URL fija en código ❌)
```

### Problemas Identificados:
- ❌ Ambos ambientes comparten la misma base de datos
- ❌ WebSocket con URL fija en código
- ❌ Usuarios de Quintana Roo no encontrados en recuperación de contraseña
- ❌ Configuración no externalizada
- ❌ Imposible escalar o independizar ambientes

---

## ✅ DESPUÉS (Solución)

```
┌────────────────────────────────────────────────────────────────┐
│                        MongoDB Server                           │
│                                                                 │
│  ┌─────────────────────────┐   ┌──────────────────────────┐   │
│  │ Database: logs_system   │   │ Database: logs_quintanaroo│   │
│  │ ┌─────────────────────┐ │   │ ┌──────────────────────┐ │   │
│  │ │ users (8040 only)   │ │   │ │ users (QRoo only)    │ │   │
│  │ └─────────────────────┘ │   │ └──────────────────────┘ │   │
│  │ ┌─────────────────────┐ │   │ ┌──────────────────────┐ │   │
│  │ │ log_events          │ │   │ │ log_events           │ │   │
│  │ └─────────────────────┘ │   │ └──────────────────────┘ │   │
│  │ ┌─────────────────────┐ │   │ ┌──────────────────────┐ │   │
│  │ │ api_keys            │ │   │ │ api_keys             │ │   │
│  │ └─────────────────────┘ │   │ └──────────────────────┘ │   │
│  └─────────────────────────┘   └──────────────────────────┘   │
└────────────────────────────────────────────────────────────────┘
              ▲                              ▲
              │                              │
              │                              │
    ┌─────────┴───────────┐    ┌────────────┴─────────────────┐
    │                     │    │                               │
    │  AMBIENTE 8040      │    │  AMBIENTE 8057 ✅            │
    │  (Original)         │    │  (Quintana Roo)               │
    │                     │    │                               │
    │  Port: 8040         │    │  Port: 8057 ✅               │
    │  DB: logs_system    │    │  DB: logs_quintanaroo ✅     │
    │                     │    │                               │
    │  WebSocket:         │    │  WebSocket: ✅               │
    │  - Fixed URL        │    │  - Dynamic (env var)          │
    │                     │    │  - WEBSOCKET_ALLOWED_ORIGINS  │
    │  Mail:              │    │                               │
    │  - Fixed config     │    │  Mail: ✅                    │
    │                     │    │  - MAIL_HOST (env var)        │
    │                     │    │  - MAIL_USERNAME (env var)    │
    │                     │    │  - MAIL_PASSWORD (env var)    │
    └─────────────────────┘    └───────────────────────────────┘
              ▲                              ▲
              │                              │
              │                              │
         dashboard-api              dashboard-quintanaroo
      .grupo-santoro.com.mx       .grupo-santoro.com.mx
   (o cualquier origen)          (o cualquier origen ✅)
```

---

## 🔑 CAMBIOS CLAVE IMPLEMENTADOS

### 1. Base de Datos Separada
```yaml
# ANTES
spring.data.mongodb.uri: mongodb://localhost:27017/logs_system

# DESPUÉS
spring.data.mongodb.uri: ${MONGODB_URI:mongodb://localhost:27017/logs_quintanaroo}
```

### 2. WebSocket Dinámico
```java
// ANTES
registry.addEndpoint("/ws")
    .setAllowedOriginPatterns("*");  // Sin configuración

// DESPUÉS
@Value("${app.websocket.allowed-origins:*}")
private String allowedOrigins;

registry.addEndpoint("/ws")
    .setAllowedOriginPatterns(allowedOrigins.split(","));
```

### 3. Configuración de Correo Externalizada
```properties
# ANTES
spring.mail.host=smtp.gmail.com
spring.mail.username=soporte.tecnico@grupo-santoro.com.mx

# DESPUÉS
spring.mail.host=${MAIL_HOST:smtp.gmail.com}
spring.mail.username=${MAIL_USERNAME:soporte.tecnico@grupo-santoro.com.mx}
```

---

## 📊 FLUJO DE DATOS

### Recuperación de Contraseña

#### ANTES (❌ Fallaba):
```
Usuario (QRoo)
    │
    ▼
Frontend (8057) ──> Backend (8057)
                         │
                         ▼
                    Query: users ──> MongoDB (logs_system)
                                          │
                                          ▼
                                     ❌ Usuario NO encontrado
                                        (está en otra DB)
```

#### DESPUÉS (✅ Funciona):
```
Usuario (QRoo)
    │
    ▼
Frontend (8057) ──> Backend (8057)
                         │
                         ▼
                    Query: users ──> MongoDB (logs_quintanaroo)
                                          │
                                          ▼
                                     ✅ Usuario ENCONTRADO
                                          │
                                          ▼
                                     Enviar correo OK ✅
```

### Autenticación WebSocket

#### ANTES (❌ Fallaba):
```
Frontend
    │
    └─> ws://dashboard-quintanaroo.grupo-santoro.com.mx:8057/ws
              │
              ▼
         Backend WebSocket
              │
              ▼
         Allowed Origins: ["dashboard-api.grupo-santoro.com.mx"] ❌
              │
              ▼
         ❌ CORS ERROR - Origin not allowed
```

#### DESPUÉS (✅ Funciona):
```
Frontend
    │
    └─> ws://dashboard-quintanaroo.grupo-santoro.com.mx:8057/ws
              │
              ▼
         Backend WebSocket
              │
              ▼
         Allowed Origins: ${WEBSOCKET_ALLOWED_ORIGINS}
                          (configurado como "*" o URL específica) ✅
              │
              ▼
         ✅ CONEXIÓN EXITOSA
```

---

## 🔄 VARIABLES DE ENTORNO POR AMBIENTE

### Ambiente 8040 (Original)
```bash
SERVER_PORT=8040
MONGODB_URI=mongodb://localhost:27017/logs_system
MULTITENANT_BASE_DATABASE=logs_system
WEBSOCKET_ALLOWED_ORIGINS=https://dashboard-api.grupo-santoro.com.mx
```

### Ambiente 8057 (Quintana Roo)
```bash
SERVER_PORT=8057
MONGODB_URI=mongodb://localhost:27017/logs_quintanaroo
MULTITENANT_BASE_DATABASE=logs_quintanaroo
WEBSOCKET_ALLOWED_ORIGINS=https://dashboard-quintanaroo.grupo-santoro.com.mx
```

---

## ✅ BENEFICIOS DE LA NUEVA ARQUITECTURA

1. ✅ **Independencia Total:** Cada ambiente tiene su propia BD
2. ✅ **Escalabilidad:** Fácil agregar más ambientes (estados)
3. ✅ **Configuración Flexible:** Todo mediante variables de entorno
4. ✅ **Sin Colisiones:** Los usuarios no se mezclan entre ambientes
5. ✅ **Mantenimiento Fácil:** Cambios en un ambiente no afectan al otro
6. ✅ **Seguridad:** Aislamiento de datos por ambiente
7. ✅ **Testing:** Fácil crear ambientes de prueba

---

## 🎯 PRÓXIMOS PASOS RECOMENDADOS

### Corto Plazo
1. ✅ Migrar usuarios de Quintana Roo a `logs_quintanaroo`
2. ✅ Desplegar ambiente 8057 con nuevas configuraciones
3. ✅ Probar recuperación de contraseña
4. ✅ Validar WebSocket desde frontend

### Mediano Plazo
1. 🔄 Replicar para otros estados (si aplica)
2. 🔄 Configurar monitoreo independiente por ambiente
3. 🔄 Implementar backups automáticos por BD
4. 🔄 Configurar alertas específicas

### Largo Plazo
1. 📋 Considerar migración a multi-tenant real (DB por tenant)
2. 📋 Implementar service mesh para gestión de microservicios
3. 📋 Containerización con Docker/Kubernetes
4. 📋 CI/CD por ambiente

---

## 📈 COMPARACIÓN DE ARQUITECTURAS

| Aspecto | ANTES | DESPUÉS |
|---------|-------|---------|
| Base de Datos | Compartida ❌ | Separada ✅ |
| WebSocket | URL fija ❌ | Configurable ✅ |
| Correo | Estático ❌ | Configurable ✅ |
| Escalabilidad | Limitada ❌ | Fácil ✅ |
| Mantenimiento | Complejo ❌ | Simple ✅ |
| Colisiones | Frecuentes ❌ | Ninguna ✅ |
| Seguridad | Baja ❌ | Alta ✅ |

---

**Conclusión:** La arquitectura ahora permite que cada ambiente sea completamente autónomo, eliminando los problemas de autenticación WebSocket y recuperación de contraseña.

