package com.sender.backend.presence;

public record PresencePayload(Integer userId, PresenceState state) {}
