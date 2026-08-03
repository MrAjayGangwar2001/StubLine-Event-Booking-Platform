package com.eventbooking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Enables a STOMP-over-WebSocket broker so seat status changes (locked /
 * available / booked) can be pushed to every client viewing an event's seat
 * map in real time, instead of each client having to poll for changes.
 *
 * Clients connect to /ws (with SockJS fallback for environments that block
 * raw WebSockets) and subscribe to /topic/event/{eventId} to receive updates
 * scoped to the event they're currently looking at.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // tightened via app.cors config in production
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");   // server -> client broadcasts
        registry.setApplicationDestinationPrefixes("/app"); // client -> server, unused for now but reserved
    }
}
