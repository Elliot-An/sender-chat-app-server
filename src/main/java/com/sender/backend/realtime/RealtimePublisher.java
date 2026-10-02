package com.sender.backend.realtime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealtimePublisher {
    private final ApplicationEventPublisher events;
    private final SimpMessagingTemplate messaging;

    public RealtimePublisher(ApplicationEventPublisher events, SimpMessagingTemplate messaging) {
        this.events = events;
        this.messaging = messaging;
    }

    public void publishAfterCommit(Integer userId, String type, Object payload) {
        events.publishEvent(RealtimeEvent.forUser(userId, type, payload));
    }

    public void sendToUser(Integer userId, String type, Object payload) {
        send(RealtimeEvent.forUser(userId, type, payload));
    }

    void send(RealtimeEvent event) {
        messaging.convertAndSendToUser(event.recipientUserId().toString(), "/queue/events", event);
    }
}
