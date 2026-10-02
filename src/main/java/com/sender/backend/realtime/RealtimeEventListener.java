package com.sender.backend.realtime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RealtimeEventListener {
    private final RealtimePublisher publisher;

    public RealtimeEventListener(RealtimePublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(RealtimeEvent event) {
        publisher.send(event);
    }
}
