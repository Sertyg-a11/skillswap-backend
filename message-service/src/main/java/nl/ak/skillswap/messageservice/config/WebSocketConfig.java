package nl.ak.skillswap.messageservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable simple in-memory broker for subscriptions
        // /topic for broadcast, /queue for point-to-point
        registry.enableSimpleBroker("/topic", "/queue");

        // Prefix for messages FROM client TO server
        registry.setApplicationDestinationPrefixes("/app");

        // Prefix for user-specific destinations
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint - clients connect here
        registry.addEndpoint("/ws")
                .setAllowedOrigins(
                    "http://localhost:5173",
                    "http://localhost:3000"
                )
                .withSockJS(); // Fallback for older browsers

        // Pure WebSocket endpoint (no SockJS)
        registry.addEndpoint("/ws")
                .setAllowedOrigins(
                    "http://localhost:5173",
                    "http://localhost:3000"
                );
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Add JWT authentication interceptor
        registration.interceptors(authInterceptor);
    }
}
