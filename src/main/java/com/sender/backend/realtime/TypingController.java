package com.sender.backend.realtime;

import com.sender.backend.common.ratelimit.RateLimitPolicy;
import com.sender.backend.common.ratelimit.RateLimiter;
import com.sender.backend.conversation.ConversationMember;
import com.sender.backend.conversation.ConversationMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class TypingController {
    private static final Duration TYPING_TTL = Duration.ofSeconds(5);
    private static final Duration MIN_SIGNAL_INTERVAL = Duration.ofMillis(250);

    private final ConversationMemberRepository members;
    private final RealtimePublisher realtime;
    private final RateLimiter rateLimiter;
    private final Map<TypingKey, TypingState> states = new ConcurrentHashMap<>();

    public TypingController(ConversationMemberRepository members, RealtimePublisher realtime,
                            RateLimiter rateLimiter) {
        this.members = members;
        this.realtime = realtime;
        this.rateLimiter = rateLimiter;
    }

    @MessageMapping("/conversations/{conversationId}/typing")
    public void typing(@DestinationVariable Integer conversationId,
                       @Payload TypingRequest request,
                       Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Integer userId = parseUserId(principal);
        // Typing is best-effort: excess signals are dropped silently instead of erroring the STOMP session.
        if (!rateLimiter.allow(RateLimitPolicy.TYPING, "user:" + userId)) {
            return;
        }
        if (!members.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this conversation");
        }
        if (request == null || request.state() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Typing state is required");
        }
        Instant now = Instant.now();
        TypingKey key = new TypingKey(conversationId, userId);
        TypingState previous = states.get(key);
        if (previous != null && now.isBefore(previous.lastSignalAt().plus(MIN_SIGNAL_INTERVAL))) {
            return;
        }
        if (previous != null && previous.state() == request.state()) {
            states.put(key, new TypingState(request.state(), now.plus(TYPING_TTL), now));
            return;
        }
        TypingState emitted = new TypingState(request.state(), now.plus(TYPING_TTL), now);
        states.put(key, emitted);
        if (request.state() == TypingStateValue.STOPPED) {
            states.remove(key);
        }
        broadcast(conversationId, userId, request.state(), emitted.expiresAt());
    }

    @Scheduled(fixedDelay = 1000)
    public void expireTyping() {
        Instant now = Instant.now();
        states.entrySet().removeIf(entry -> {
            TypingState state = entry.getValue();
            if (now.isBefore(state.expiresAt())) return false;
            broadcast(entry.getKey().conversationId(), entry.getKey().userId(),
                    TypingStateValue.STOPPED, now);
            return true;
        });
    }

    private void broadcast(Integer conversationId, Integer senderId, TypingStateValue state, Instant expiresAt) {
        TypingPayload payload = new TypingPayload(conversationId, senderId, state, expiresAt);
        members.findByConversationId(conversationId).stream()
                .map(ConversationMember::getUser)
                .map(user -> user.getId())
                .filter(userId -> !userId.equals(senderId))
                .forEach(userId -> realtime.sendToUser(userId,
                        state == TypingStateValue.STARTED ? "TYPING_STARTED" : "TYPING_STOPPED",
                        payload));
    }

    private Integer parseUserId(Principal principal) {
        try {
            return Integer.valueOf(principal.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated principal");
        }
    }

    public record TypingRequest(TypingStateValue state) {}
    public record TypingPayload(Integer conversationId, Integer userId,
                                TypingStateValue state, Instant expiresAt) {}
    public enum TypingStateValue { STARTED, STOPPED }
    private record TypingKey(Integer conversationId, Integer userId) {}
    private record TypingState(TypingStateValue state, Instant expiresAt, Instant lastSignalAt) {
        private TypingState(TypingStateValue state, Instant expiresAt) {
            this(state, expiresAt, Instant.now());
        }
    }
}
