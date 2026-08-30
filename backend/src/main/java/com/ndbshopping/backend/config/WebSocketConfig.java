package com.ndbshopping.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Notifications admin temps réel (interne uniquement, pas de WhatsApp).
 * Le dashboard Django se connecte en SockJS/STOMP sur /ws
 * et s'abonne au topic /topic/admin-notifications.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties appProperties;

    public WebSocketConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = ArraysOrWildcard.origins(appProperties.frontendUrl());
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins)
                .withSockJS();
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    private static final class ArraysOrWildcard {
        private static String[] origins(String frontendUrl) {
            return java.util.Arrays.stream(frontendUrl.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toArray(String[]::new);
        }
    }
}
