package com.sender.backend.realtime;

import com.sender.backend.conversation.ConversationMemberRepository;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.security.Principal;

@Configuration
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtDecoder jwtDecoder;
    private final ConversationMemberRepository members;

    public WebSocketSecurityConfig(JwtDecoder jwtDecoder, ConversationMemberRepository members) {
        this.jwtDecoder = jwtDecoder;
        this.members = members;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                if (accessor.getCommand() == StompCommand.CONNECT) {
                    authenticate(accessor);
                    return message;
                }
                if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
                    authorizeSubscription(accessor);
                }
                return message;
            }

            private void authenticate(StompHeaderAccessor accessor) {
                String authorization = accessor.getFirstNativeHeader("Authorization");
                if (authorization == null || !authorization.startsWith("Bearer ")) {
                    throw new IllegalArgumentException("Missing WebSocket access token");
                }
                try {
                    Jwt jwt = jwtDecoder.decode(authorization.substring(7));
                    accessor.setUser(() -> jwt.getSubject());
                } catch (JwtException exception) {
                    throw new IllegalArgumentException("Invalid WebSocket access token", exception);
                }
            }

            private void authorizeSubscription(StompHeaderAccessor accessor) {
                Principal principal = accessor.getUser();
                String destination = accessor.getDestination();
                if (principal == null || destination == null) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated subscription required");
                }
                if (destination.equals("/user/queue/events")) {
                    return;
                }
                if (!destination.startsWith("/topic/conversations/")) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Subscription destination is not allowed");
                }
                try {
                    Integer conversationId = Integer.valueOf(destination.substring("/topic/conversations/".length()));
                    Integer userId = Integer.valueOf(principal.getName());
                    if (!members.existsByConversationIdAndUserId(conversationId, userId)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conversation subscription is not allowed");
                    }
                } catch (NumberFormatException exception) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversation subscription");
                }
            }
        });
    }
}
