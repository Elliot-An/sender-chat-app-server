package com.sender.backend.presence;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;

@Service
public class PresenceService {
    private final PresenceSessionStore store;
    private final PresenceNotifier notifier;

    public PresenceService(PresenceSessionStore store, PresenceNotifier notifier) {
        this.store = store;
        this.notifier = notifier;
    }

    public void sessionConnected(Integer userId, String sessionId) {
        if (store.addSession(userId, sessionId)) {
            notifier.presenceChanged(userId, PresenceState.ONLINE);
        }
    }

    public void heartbeat(Integer userId, String sessionId) {
        if (store.refreshSession(userId, sessionId)) {
            notifier.presenceChanged(userId, PresenceState.ONLINE);
        }
    }

    public void sessionDisconnected(String sessionId) {
        store.removeSession(sessionId).ifPresent(userId ->
                notifier.presenceChanged(userId, PresenceState.OFFLINE));
    }

    public boolean isOnline(Integer userId) {
        return store.isOnline(userId);
    }

    public Set<Integer> onlineAmong(Collection<Integer> userIds) {
        return store.onlineAmong(userIds);
    }

    public void expireStaleSessions() {
        for (Integer userId : store.sweepExpired()) {
            notifier.presenceChanged(userId, PresenceState.OFFLINE);
        }
    }
}
