package com.sender.backend.realtime;

import java.time.Instant;
import java.util.UUID;

public record RealtimeEvent(
        Integer recipientUserId,
        UUID eventId,
        String type,
        Instant occurredAt,
        Object payload
) {
    public static RealtimeEvent forUser(Integer recipientUserId, String type, Object payload) {
        return new RealtimeEvent(recipientUserId, UUID.randomUUID(), type, Instant.now(), payload);
    }
}
