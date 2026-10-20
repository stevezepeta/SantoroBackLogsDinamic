package backlogs.dinamico.infra.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Orígenes permitidos en el handshake WebSocket/STOMP (separados por coma).
     * Misma lista explícita que CORS (app.cors.allowed-origins). Se usan patrones
     * para seguir soportando el comodín "*" en scripts locales que ya lo usaban.
     */
    @Value("${app.websocket.allowed-origins:http://187.188.66.56:8032,https://dashboard.grupo-santoro.com.mx,http://localhost:4200,http://localhost:3000,http://localhost:5173,http://localhost:9000}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Broker simple en memoria: /topic para broadcasts, /queue para mensajes privados
        registry.enableSimpleBroker("/topic", "/queue");

        // Prefijo para destinos de aplicación
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint nativo WebSocket (clientes modernos)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins);

        // Endpoint con SockJS: habilita credenciales y patrones de origen compatibles
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }

}
