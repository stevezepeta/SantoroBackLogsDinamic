# Endpoint de Validación Externa de Credenciales

## Descripción

Endpoint público que permite validar credenciales de usuario (email + password) sin generar tokens JWT.
Implementado como parte de la **Estrategia A** para unificar la autenticación con el sistema de Tickets.

## Especificaciones Técnicas

### Endpoint
```
POST /api/auth/validate-external
```

### Seguridad
- ✅ **Acceso público** (no requiere autenticación previa)
- ✅ Configurado como `.permitAll()` en `SecurityConfig`
- ✅ No requiere header `X-Api-Key` ni `Authorization`

### Request

**Content-Type:** `application/json`

```json
{
  "email": "usuario@example.com",
  "password": "contraseña_en_texto_plano"
}
```

#### Validaciones
- `email`: requerido, debe ser un email válido
- `password`: requerido, no puede estar vacío

### Response

**HTTP Status:** `200 OK` (siempre, incluso si las credenciales son inválidas)

**Content-Type:** `application/json`

#### Credenciales válidas
```json
{
  "success": true,
  "message": "Credenciales válidas",
  "errorCode": null,
  "data": {
    "isValid": true
  }
}
```

#### Credenciales inválidas
```json
{
  "success": true,
  "message": "Credenciales inválidas",
  "errorCode": null,
  "data": {
    "isValid": false
  }
}
```

## Lógica de Validación

1. **Normalización del email**: Se convierte a minúsculas y se elimina espacios en blanco
2. **Búsqueda del usuario**: Se busca en MongoDB sin restricción de tenant usando `findByEmailIgnoreCase`
3. **Validación de estado**: Solo usuarios con `status = "active"` son considerados válidos
4. **Comparación de contraseña**: Se usa `PasswordEncoder.matches()` para comparar la contraseña en texto plano contra el hash BCrypt almacenado
5. **Respuesta unificada**: Siempre retorna HTTP 200 con `isValid: true/false` para evitar ataques de enumeración de usuarios

## Seguridad Adicional

### Protección contra Timing Attacks
El método `PasswordEncoder.matches()` (BCrypt) ya incluye protección contra timing attacks.

### Prevención de Enumeración de Usuarios
- Siempre retorna HTTP 200
- El mensaje y estructura de respuesta son idénticos para credenciales válidas e inválidas
- Solo cambia el valor de `isValid`

### Logging
- ✅ Logs informativos para credenciales válidas
- ✅ Logs de debug para credenciales inválidas (no expone información sensible en producción)
- ❌ Nunca se registra la contraseña en texto plano

## Archivos Implementados

### DTOs
- `src/main/java/backlogs/dinamico/api/dto/auth/ValidateExternalRequest.java`
- `src/main/java/backlogs/dinamico/api/dto/auth/ValidateExternalResponse.java`

### Service
- `src/main/java/backlogs/dinamico/service/auth/ExternalValidationService.java`

### Controller
- `src/main/java/backlogs/dinamico/controller/auth/ExternalValidationController.java`

### Configuración
- `src/main/java/backlogs/dinamico/config/SecurityConfig.java` (línea 82: agregado `/api/auth/validate-external`)

## Uso desde el Sistema de Tickets (Cliente)

### Ejemplo con cURL
```bash
curl -X POST http://localhost:8005/api/auth/validate-external \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@santoro.com",
    "password": "Admin2024!"
  }'
```

### Ejemplo con JavaScript (Fetch API)
```javascript
async function validateCredentials(email, password) {
  const response = await fetch('http://localhost:8005/api/auth/validate-external', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ email, password })
  });
  
  const data = await response.json();
  return data.data.isValid; // true o false
}
```

### Ejemplo con Java (Spring RestTemplate)
```java
ValidateExternalRequest request = new ValidateExternalRequest();
request.setEmail("admin@santoro.com");
request.setPassword("Admin2024!");

ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
    "http://localhost:8005/api/auth/validate-external",
    request,
    ApiResponse.class
);

boolean isValid = (boolean) ((Map) response.getBody().getData()).get("isValid");
```

## Testing

### Caso 1: Usuario válido con contraseña correcta
```bash
POST /api/auth/validate-external
{
  "email": "admin@santoro.com",
  "password": "Admin2024!"
}

Esperado: { "data": { "isValid": true } }
```

### Caso 2: Usuario válido con contraseña incorrecta
```bash
POST /api/auth/validate-external
{
  "email": "admin@santoro.com",
  "password": "WrongPassword"
}

Esperado: { "data": { "isValid": false } }
```

### Caso 3: Usuario no existe
```bash
POST /api/auth/validate-external
{
  "email": "noexiste@example.com",
  "password": "cualquiera"
}

Esperado: { "data": { "isValid": false } }
```

### Caso 4: Usuario inactivo
```bash
POST /api/auth/validate-external
{
  "email": "user.deshabilitado@santoro.com",
  "password": "CorrectPassword"
}

Esperado: { "data": { "isValid": false } }
```

## Documentación Swagger

El endpoint está documentado automáticamente en Swagger UI:
- **URL:** http://localhost:8005/swagger-ui.html
- **Tag:** Auth External
- **Operación:** Validar credenciales externas

## Consideraciones de Producción

### Rate Limiting
⚠️ **Importante:** Este endpoint NO tiene rate limiting implementado actualmente. Se recomienda agregar:
- Bucket4j por IP de origen
- Límite recomendado: 10 intentos por minuto por IP
- Bloqueo temporal después de 5 intentos fallidos consecutivos

### Monitoreo
Se recomienda monitorear en producción:
- Tasa de éxito vs. fallo
- IPs con múltiples intentos fallidos
- Patrones de ataque de fuerza bruta

### HTTPS
⚠️ **Crítico:** En producción, este endpoint DEBE ser accedido únicamente mediante HTTPS para proteger las credenciales en tránsito.

## Mantenimiento

### Actualizar lógica de validación
Editar: `ExternalValidationService.validateCredentials()`

### Agregar campos adicionales a la respuesta
1. Modificar `ValidateExternalResponse`
2. Actualizar `ExternalValidationController.validateExternal()`

### Cambiar comportamiento de seguridad
Editar: `SecurityConfig.securityFilterChain()` línea 82

