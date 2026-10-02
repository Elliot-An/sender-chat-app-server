package com.sender.backend.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@Configuration
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtDecoder jwtDecoder;

    public WebSocketSecurityConfig(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
                    return message;
                }
                String authorization = accessor.getFirstNativeHeader("Authorization");
                if (authorization == null || !authorization.startsWith("Bearer ")) {
                    throw new IllegalArgumentException("Missing WebSocket access token");
                }
                try {
                    Jwt jwt = jwtDecoder.decode(authorization.substring(7));
                    accessor.setUser(() -> jwt.getSubject());
                    return message;
                } catch (JwtException exception) {
                    throw new IllegalArgumentException("Invalid WebSocket access token", exception);
                }
            }
        });
    }
}
