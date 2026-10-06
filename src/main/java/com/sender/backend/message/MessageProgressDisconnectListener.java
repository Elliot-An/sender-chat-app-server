package com.sender.backend.message;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class MessageProgressDisconnectListener {
    private final MessageProgressService progress;

    public MessageProgressDisconnectListener(MessageProgressService progress) {
        this.progress = progress;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        progress.flushPendingNow();
    }
}
