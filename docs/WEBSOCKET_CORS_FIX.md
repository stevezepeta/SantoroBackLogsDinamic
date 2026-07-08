# Solución Error 403 CORS en WebSockets

**Fecha:** 2026-07-08  
**Problema:** El frontend no podía conectarse al endpoint WebSocket en el puerto 8040 debido a un error HTTP/1.1 403 Forbidden causado por políticas CORS restrictivas.

## Cambios Realizados

### 1. WebSocketConfig.java (`src/main/java/backlogs/dinamico/infra/ws/WebSocketConfig.java`)

**Modificaciones:**
- ✅ Habilitado el endpoint con **SockJS** para mejor compatibilidad con navegadores
- ✅ Mantenido el endpoint nativo WebSocket (sin SockJS) para clientes modernos
- ✅ Ambos endpoints configurados con `.setAllowedOriginPatterns(origins)` para permitir conexiones desde cualquier origen

**Código modificado:**
```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    // Endpoint WebSocket con orígenes configurables
    // Se puede especificar mediante la variable de entorno WEBSOCKET_ALLOWED_ORIGINS
    // o dejar * para permitir todos los orígenes (por defecto)
    String[] origins = allowedOrigins.split(",");
    
    // Endpoint con SockJS (recomendado para compatibilidad con navegadores)
    registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(origins)
            .withSockJS();

    // Endpoint nativo WebSocket (sin SockJS)
    registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(origins);
}
```

**Valor por defecto:** `app.websocket.allowed-origins=*` (permite todos los orígenes)

### 2. SecurityConfig.java (`src/main/java/backlogs/dinamico/config/SecurityConfig.java`)

**Modificaciones:**
- ✅ Cambiado de `setAllowedOrigins(List.of("*"))` a `setAllowedOriginPatterns(List.of("*"))` 
- ✅ Mejora la compatibilidad con WebSockets y CORS
- ✅ El endpoint `/ws/**` ya estaba configurado como `.permitAll()` (línea 73)

**Código modificado:**
```java
@Bean
CorsConfigurationSource corsConfigurationSource() {
    var cfg = new CorsConfiguration();
    cfg.setAllowedOriginPatterns(List.of("*"));  // Mejor compatibilidad con WebSockets
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    // ... resto de la configuración
}
```

## ¿Por qué se produce el error 403?

El handshake inicial de WebSocket es una petición HTTP que Spring Boot valida con las políticas CORS. Si el origen del cliente no está permitido, la conexión se rechaza inmediatamente con un código 403.

**Causas comunes:**
1. Falta configurar `.setAllowedOriginPatterns()` en `registerStompEndpoints`
2. Uso de `setAllowedOrigins("*")` en lugar de `setAllowedOriginPatterns("*")`
3. Bloqueo del endpoint `/ws/**` en Spring Security

## Verificación de la Solución

### 1. Verificar la configuración (sin reiniciar el servidor)

```powershell
# Verificar que el valor por defecto sea "*"
Select-String -Path src/main/resources/application.yml -Pattern "websocket"
Select-String -Path src/main/resources/application.properties -Pattern "websocket"
```

### 2. Reiniciar el servidor backend

```powershell
# Detener cualquier instancia en el puerto 8040
Stop-Process -Name java -Force -ErrorAction SilentlyContinue

# Iniciar el backend (ajustar según tu script de inicio)
./iniciar-backend-principal.ps1
```

### 3. Probar la conexión desde el frontend

**Conexión con SockJS (recomendado):**
```javascript
import SockJS from 'sockjs-client';
import { Stomp } from '@stomp/stompjs';

const socket = new SockJS('http://localhost:8040/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, (frame) => {
    console.log('Conectado:', frame);
    
    // Suscribirse a un topic
    stompClient.subscribe('/topic/events', (message) => {
        console.log('Mensaje recibido:', JSON.parse(message.body));
    });
}, (error) => {
    console.error('Error de conexión:', error);
});
```

**Conexión nativa WebSocket:**
```javascript
import { Client } from '@stomp/stompjs';

const client = new Client({
    brokerURL: 'ws://localhost:8040/ws',
    onConnect: (frame) => {
        console.log('Conectado:', frame);
        client.subscribe('/topic/events', (message) => {
            console.log('Mensaje recibido:', JSON.parse(message.body));
        });
    },
    onStompError: (frame) => {
        console.error('Error STOMP:', frame);
    }
});

client.activate();
```

### 4. Probar desde la consola del navegador

Abre las **DevTools** (F12) y verifica:

✅ **Antes del fix:** 
```
WebSocket connection to 'ws://localhost:8040/ws' failed: HTTP/1.1 403 Forbidden
```

✅ **Después del fix:**
```
Opening Web Socket...
Web Socket Opened...
>>> CONNECTED
```

### 5. Verificar el handshake en la pestaña Network

1. Abre **Network** → **WS** (WebSockets)
2. Busca la petición a `/ws` o `/ws/.../websocket`
3. Verifica el código de respuesta: **101 Switching Protocols** ✅
4. Si ves **403 Forbidden**, el problema persiste ❌

## Configuración Avanzada (Opcional)

### Permitir solo orígenes específicos

Si en producción quieres restringir los orígenes, puedes configurar:

**application-prod.yml:**
```yaml
app:
  websocket:
    allowed-origins: "https://tudominio.com,https://app.tudominio.com"
```

O mediante variable de entorno:
```powershell
$env:WEBSOCKET_ALLOWED_ORIGINS = "https://tudominio.com,https://app.tudominio.com"
```

### Verificar el puerto del servidor

Asegúrate de que el backend esté escuchando en el puerto correcto:

```powershell
# Verificar qué proceso está usando el puerto 8040
Get-NetTCPConnection -LocalPort 8040 -ErrorAction SilentlyContinue | Format-Table -Property LocalAddress,LocalPort,State,OwningProcess
```

## Resumen de la Solución

| Archivo | Cambio | Estado |
|---------|--------|--------|
| `WebSocketConfig.java` | Habilitado SockJS + WebSocket nativo con `.setAllowedOriginPatterns("*")` | ✅ Completado |
| `SecurityConfig.java` | Cambiado a `.setAllowedOriginPatterns("*")` para mejor compatibilidad | ✅ Completado |
| Endpoint `/ws/**` | Configurado como `.permitAll()` en Spring Security | ✅ Ya estaba configurado |

## Posibles Problemas Adicionales

Si después de estos cambios aún ves el error 403:

1. **Verificar que el servidor se reinició correctamente**
   ```powershell
   Get-Process java | Where-Object { $_.Path -like "*SantoroBackLogsDinamic*" }
   ```

2. **Verificar los logs del servidor al iniciar**
   Busca líneas como:
   ```
   Mapped "{[/ws]}" onto public void WebSocketConfig.registerStompEndpoints(...)
   ```

3. **Verificar que no hay un proxy o firewall bloqueando**
   ```powershell
   Test-NetConnection -ComputerName localhost -Port 8040
   ```

4. **Verificar la URL del frontend**
   - Con SockJS: `http://localhost:8040/ws`
   - Sin SockJS: `ws://localhost:8040/ws`

## Referencias

- [Spring WebSocket Documentation](https://docs.spring.io/spring-framework/reference/web/websocket.html)
- [STOMP Protocol](https://stomp.github.io/)
- [SockJS Client](https://github.com/sockjs/sockjs-client)

---

**Nota:** Este fix permite conexiones desde cualquier origen (`*`). Para producción, considera restringir los orígenes permitidos mediante la variable `WEBSOCKET_ALLOWED_ORIGINS`.

