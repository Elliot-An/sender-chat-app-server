package com.sender.backend.presence;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

public class PresenceTransitionListener implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(PresenceTransitionListener.class);

    private final FriendshipPresenceFanout fanout;
    private final ObjectMapper objectMapper;

    public PresenceTransitionListener(FriendshipPresenceFanout fanout, ObjectMapper objectMapper) {
        this.fanout = fanout;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            PresencePayload payload = objectMapper.readValue(message.getBody(), PresencePayload.class);
            fanout.deliver(payload.userId(), payload.state());
        } catch (Exception exception) {
            log.warn("Could not handle presence transition message", exception);
        }
    }
}
