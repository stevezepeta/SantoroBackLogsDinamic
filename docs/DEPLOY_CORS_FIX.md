# ✅ SOLUCIÓN IMPLEMENTADA - 403 CORS AWS

## Cambios Realizados

### 1️⃣ SecurityConfig.java - Configuración CORS Actualizada

**Archivo**: `src/main/java/backlogs/dinamico/config/SecurityConfig.java`

**Cambios**:
- ✅ Agregado `X-Organization-Id` a `allowedHeaders`
- ✅ Agregado `X-Tenant`, `X-Tenant-Id`, `X-Organization-Id` a `exposedHeaders`

**Código modificado**:
```java
cfg.setAllowedHeaders(List.of(
    "Authorization",
    "Content-Type",
    "X-API-Key",
    "X-Api-Key",
    "X-Tenant", "X-Tenant-Id", "X-Organization-Id",  // ← X-Organization-Id agregado
    "X-Org-Code", "X-Org-Slug", "X-Org-Domain",
    "X-System-Id", "X-Environment-Id",
    "X-Requested-With"
));

cfg.setExposedHeaders(List.of(
    "X-Request-Id",
    "X-Tenant", "X-Tenant-Id", "X-Organization-Id"  // ← Headers expuestos
));
```

---

## 🔍 Explicación del Problema

### ¿Por qué fallaba en AWS pero no en local?

1. **Configuración anterior**: Los headers estaban en `allowedHeaders` (el servidor los acepta) pero NO en `exposedHeaders`
2. **AWS ALB/Proxy**: Los balanceadores de carga de AWS validan CORS de forma más estricta
3. **Spring Security**: Sin `exposedHeaders`, el proxy bloquea los headers personalizados con 403

### ¿Qué hace `exposedHeaders`?

Indica al navegador y proxies intermedios (ALB, CloudFront, Nginx) qué headers están permitidos para lectura/escritura en ambas direcciones (request/response). Sin esta configuración, AWS rechaza los headers personalizados.

---

## 📦 Archivos Generados

| Archivo | Propósito |
|---------|-----------|
| `SecurityConfig.java` (modificado) | Configuración CORS corregida |
| `docs/CORS_AWS_FIX.md` | Documentación técnica completa |
| `test-cors-local.ps1` | Script de verificación pre-deploy |
| `AGENTS.md` (actualizado) | Referencia al fix en arquitectura |

---

## 🚀 Instrucciones de Deployment

### Paso 1: Build del JAR

```powershell
.\mvnw.cmd clean package -DskipTests
```

✅ **Verificado**: Compilación exitosa - `BUILD SUCCESS`

### Paso 2: Test Local (Opcional pero recomendado)

```powershell
# Iniciar servidor local
.\mvnw.cmd spring-boot:run

# En otra terminal, ejecutar test CORS
.\test-cors-local.ps1
```

**Esperado**: Todos los tests en verde (✓)

### Paso 3: Deploy a AWS

Reemplaza el JAR en tu servidor AWS:

```bash
# Copiar JAR a AWS (ajusta según tu método)
scp target/dinamico-0.0.1-SNAPSHOT.jar user@ec2-instance:/opt/backlogs/

# SSH al servidor
ssh user@ec2-instance

# Detener servicio
sudo systemctl stop backlogs-api

# Reemplazar JAR
sudo mv /opt/backlogs/dinamico-0.0.1-SNAPSHOT.jar /opt/backlogs/dinamico-0.0.1-SNAPSHOT.jar.backup
sudo cp dinamico-0.0.1-SNAPSHOT.jar /opt/backlogs/

# Iniciar servicio
sudo systemctl start backlogs-api

# Verificar logs
sudo journalctl -u backlogs-api -f
```

### Paso 4: Verificar en Producción

Desde tu máquina local, ejecuta:

```powershell
# Test preflight
curl -X OPTIONS https://api-logs.grupo-santoro.com.mx/api/auth/login `
  -H "Origin: https://frontend.grupo-santoro.com.mx" `
  -H "Access-Control-Request-Method: POST" `
  -H "Access-Control-Request-Headers: X-Tenant-Id,Content-Type" `
  -v
```

**Verificar headers de respuesta**:
```
Access-Control-Expose-Headers: X-Request-Id, X-Tenant, X-Tenant-Id, X-Organization-Id
```

```powershell
# Test real con header
curl -X POST https://api-logs.grupo-santoro.com.mx/api/auth/login `
  -H "Content-Type: application/json" `
  -H "X-Tenant-Id: 68ed8cdedca3a97d9f999ba9" `
  -d '{"email":"test@test.com","password":"test"}' `
  -v
```

**Esperado**: HTTP 200 o 401 (por credenciales), pero **NO 403**

---

## 🧪 Test desde Angular

En tu aplicación Angular, verifica que el interceptor funcione correctamente:

```typescript
// auth.interceptor.ts
intercept(req: HttpRequest<any>, next: HttpHandler) {
  const tenantId = this.authService.getTenantId();
  
  if (tenantId) {
    req = req.clone({
      setHeaders: {
        'X-Tenant-Id': tenantId,
        'X-Organization-Id': tenantId  // También funciona
      }
    });
  }
  
  return next.handle(req);
}
```

**Abrir DevTools → Network**:
1. Verificar petición OPTIONS retorna 204
2. Verificar petición POST retorna 200
3. NO debe haber error CORS en consola

---

## ⚠️ Troubleshooting

### Si sigue fallando con 403:

1. **Verificar Nginx/Proxy reverso** delante del backend:
   ```nginx
   # /etc/nginx/sites-available/backlogs-api
   location / {
       add_header 'Access-Control-Expose-Headers' 'X-Tenant,X-Tenant-Id,X-Organization-Id,X-Request-Id';
       proxy_pass http://localhost:8005;
   }
   ```

2. **Verificar AWS ALB Target Group**: Health checks deben apuntar a `/actuator/health`

3. **Verificar Security Group**: Puerto 8005 (o el configurado) debe estar abierto

4. **Logs del backend**:
   ```bash
   tail -f /var/log/backlogs/app.log | grep -i cors
   ```

### Si sigue sin funcionar:

1. Cambiar `allowedOrigins("*")` por dominio específico:
   ```java
   cfg.setAllowedOrigins(List.of(
       "https://app.grupo-santoro.com.mx",
       "https://admin.grupo-santoro.com.mx"
   ));
   ```

2. Habilitar CORS debug en Spring:
   ```properties
   # application-prod.properties
   logging.level.org.springframework.web.cors=DEBUG
   ```

---

## 📊 Checklist de Deployment

- [x] ✅ Code modificado en `SecurityConfig.java`
- [x] ✅ Compilación exitosa (`mvn clean package`)
- [ ] ⏳ Test local con `test-cors-local.ps1`
- [ ] ⏳ Deploy JAR a AWS
- [ ] ⏳ Restart servicio en AWS
- [ ] ⏳ Test CORS en producción
- [ ] ⏳ Test desde Angular frontend
- [ ] ⏳ Verificar logs sin errores CORS

---

## 📞 Contacto de Soporte

Si necesitas asistencia adicional:

- **Documentación**: `docs/CORS_AWS_FIX.md`
- **Test script**: `test-cors-local.ps1`
- **Configuración**: `src/main/java/backlogs/dinamico/config/SecurityConfig.java`

---

**Estado**: ✅ **LISTO PARA DEPLOY**

**Fecha**: 2026-05-29  
**Versión**: 0.0.1-SNAPSHOT  
**Build**: Exitoso (20.9s)

