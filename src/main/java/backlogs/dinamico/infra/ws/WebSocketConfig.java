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

    @Value("${app.websocket.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        // Broker simple en memoria
        registry.enableSimpleBroker("/topic");

        // Prefijo para destinos
        registry.setApplicationDestinationPrefixes("/app");

    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        // Endpoint WebSocket con orígenes configurables
        String[] origins = allowedOrigins.split(",");
        
        // Endpoint con SockJS (recomendado para compatibilidad con navegadores)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins)
                .withSockJS();

        // Endpoint nativo WebSocket (sin SockJS)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins);


    }

}
