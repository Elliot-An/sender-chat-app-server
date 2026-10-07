package com.sender.backend.presence;

import com.sender.backend.common.ratelimit.RateLimitPolicy;
import com.sender.backend.common.ratelimit.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@Controller
public class PresenceController {
    private final PresenceService presence;
    private final RateLimiter rateLimiter;

    public PresenceController(PresenceService presence, RateLimiter rateLimiter) {
        this.presence = presence;
        this.rateLimiter = rateLimiter;
    }

    @MessageMapping("/presence/heartbeat")
    public void heartbeat(Principal principal, StompHeaderAccessor accessor) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Integer userId;
        try {
            userId = Integer.valueOf(principal.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated principal");
        }
        if (!rateLimiter.allow(RateLimitPolicy.PRESENCE_HEARTBEAT, "user:" + userId)) {
            return;
        }
        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            return;
        }
        presence.heartbeat(userId, sessionId);
    }
}
