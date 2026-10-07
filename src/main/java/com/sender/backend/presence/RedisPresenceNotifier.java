package com.sender.backend.presence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

public class RedisPresenceNotifier implements PresenceNotifier {
    public static final String CHANNEL = "presence:transitions";

    private static final Logger log = LoggerFactory.getLogger(RedisPresenceNotifier.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisPresenceNotifier(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void presenceChanged(Integer userId, PresenceState state) {
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(new PresencePayload(userId, state)));
        } catch (RuntimeException exception) {
            log.warn("Could not publish presence transition for user {}", userId, exception);
        }
    }
}
