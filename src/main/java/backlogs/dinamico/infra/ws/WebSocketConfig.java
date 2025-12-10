package backlogs.dinamico.infra.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        // Broker simple en memoria
        registry.enableSimpleBroker("/topic");

        // Prefijo para destinos
        registry.setApplicationDestinationPrefixes("/app");

    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        // Endpoint WebSocket
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");

        // Si quieres soporte SockJS (recomendado para navegadores viejos):
        // registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();

    }

}
