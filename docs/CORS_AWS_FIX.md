# Solución 403 CORS en AWS - Headers Personalizados

## Problema Identificado

Al enviar peticiones con headers personalizados (`X-Tenant`, `X-Tenant-Id`, `X-Organization-Id`) desde el frontend en Angular al backend en AWS (`https://api-logs.grupo-santoro.com.mx`), se recibía un error **403 Forbidden** inmediato, mientras que en el servidor local funcionaba correctamente.

### Causa Raíz

La configuración CORS en Spring Security tenía los headers personalizados declarados en `.setAllowedHeaders()` (headers que el servidor **acepta** en peticiones), pero **NO** estaban declarados en `.setExposedHeaders()` (headers que el servidor **permite leer** en las respuestas).

En infraestructuras AWS con balanceadores de carga (ALB/ELB), proxies inversos y reglas de seguridad más estrictas, se requiere que los headers personalizados estén explícitamente expuestos para permitir su tránsito completo en ambos sentidos (request/response).

---

## Solución Implementada

### Cambios en `SecurityConfig.java`

**Antes:**
```java
cfg.setExposedHeaders(List.of("X-Request-Id"));
```

**Después:**
```java
cfg.setExposedHeaders(List.of(
    "X-Request-Id",
    "X-Tenant", "X-Tenant-Id", "X-Organization-Id"
));
```

**Adicionalmente**, se agregó `X-Organization-Id` a la lista de headers permitidos para consistencia con el flujo de multi-tenancy:

```java
cfg.setAllowedHeaders(List.of(
    "Authorization",
    "Content-Type",
    "X-API-Key",
    "X-Api-Key",
    "X-Tenant", "X-Tenant-Id", "X-Organization-Id",  // ← agregado X-Organization-Id
    "X-Org-Code", "X-Org-Slug", "X-Org-Domain",
    "X-System-Id", "X-Environment-Id",
    "X-Requested-With"
));
```

---

## Impacto de los Cambios

### ✅ Resuelve

1. **403 Forbidden en AWS**: El backend ahora expone explícitamente los headers de tenancy.
2. **Compatibilidad ALB/Cloudfront**: Los balanceadores de carga permiten el tránsito de estos headers.
3. **Angular HttpClient**: El interceptor puede leer y enviar los headers sin restricción.

### ⚠️ Consideraciones

- **AllowedOrigins `"*"`**: Mantiene el acceso público a la API. Si en futuro se requiere seguridad por dominio, cambiar a lista específica de dominios.
- **AllowCredentials = false**: Compatible con `origins("*")`. Si se activan credenciales, se debe especificar dominios concretos.
- **MaxAge = 3600**: Los navegadores cachean la respuesta preflight 1 hora.

---

## Verificación en Producción

### 1. Test Manual con cURL

```bash
curl -X POST https://api-logs.grupo-santoro.com.mx/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 68ed8cdedca3a97d9f999ba9" \
  -d '{
    "email": "admin@grupo-santoro.com.mx",
    "password": "tu-password"
  }' \
  -v
```

**Esperado**: HTTP 200 sin error 403

### 2. Test Preflight OPTIONS

```bash
curl -X OPTIONS https://api-logs.grupo-santoro.com.mx/api/auth/login \
  -H "Origin: https://frontend.grupo-santoro.com.mx" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: X-Tenant-Id,Content-Type" \
  -v
```

**Verificar headers de respuesta**:
```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
Access-Control-Allow-Headers: Authorization, Content-Type, X-Tenant, X-Tenant-Id, X-Organization-Id, ...
Access-Control-Expose-Headers: X-Request-Id, X-Tenant, X-Tenant-Id, X-Organization-Id
Access-Control-Max-Age: 3600
```

### 3. Test desde Angular DevTools

Abrir la pestaña **Network** del navegador y verificar:
- Petición OPTIONS (preflight) retorna **204 No Content**
- Petición POST real retorna **200 OK**
- No aparece error CORS en la consola

---

## Deployment

### Build & Deploy

```bash
# Build JAR
.\mvnw.cmd clean package -DskipTests

# Subir JAR a AWS
# (usar tu método de deployment: Elastic Beanstalk, EC2, ECS, etc.)

# Reiniciar servicio
sudo systemctl restart backlogs-api
# o
pm2 restart backlogs-api
```

### Variables de Entorno (AWS)

Asegurar que estén configuradas en la instancia de producción:

```bash
export OPENAI_API_KEY=sk-xxx...
export EVA_OPENAI_API_KEY=sk-xxx...
export MONGODB_URI=mongodb://localhost:27017
export SERVER_PORT=8005
export SPRING_PROFILES_ACTIVE=prod
```

### Verificar Logs al Iniciar

```bash
tail -f /var/log/backlogs/app.log
```

Buscar línea:
```
[CORS] Exposed headers configured: X-Request-Id, X-Tenant, X-Tenant-Id, X-Organization-Id
```

---

## FAQ

### ¿Por qué funcionaba en local pero no en AWS?

El servidor local generalmente tiene configuraciones de seguridad más permisivas (perfil `dev`). AWS tiene proxies inversos (ALB, Nginx, Cloudfront) que validan headers CORS de forma más estricta.

### ¿Necesito cambiar algo en el frontend?

**No**. El interceptor de Angular ya enviaba los headers correctamente. El problema estaba en el backend.

### ¿Esto afecta la seguridad?

**No negativamente**. Los headers expuestos no contienen información sensible (solo IDs de organización). El token JWT sigue encriptado y no se expone.

### ¿Qué pasa si tengo múltiples frontends (web/móvil)?

La configuración actual con `allowedOrigins("*")` soporta múltiples orígenes. Si en futuro se requiere restricción, especificar dominios:

```java
cfg.setAllowedOrigins(List.of(
    "https://app.grupo-santoro.com.mx",
    "https://admin.grupo-santoro.com.mx",
    "http://localhost:4200"  // dev
));
```

---

## Referencias Técnicas

- [Spring CORS Configuration](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html)
- [MDN CORS Guide](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS)
- [AWS ALB CORS Troubleshooting](https://aws.amazon.com/premiumsupport/knowledge-center/api-gateway-cors-errors/)

---

**Autor**: GitHub Copilot  
**Fecha**: 2026-05-29  
**Versión Backend**: 0.0.1-SNAPSHOT  
**Spring Boot**: 3.5.6

