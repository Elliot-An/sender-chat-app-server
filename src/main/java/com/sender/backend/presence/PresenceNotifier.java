package com.sender.backend.presence;

/** Broadcasts a presence transition to all backend instances (e.g. Redis pub/sub). */
public interface PresenceNotifier {
    void presenceChanged(Integer userId, PresenceState state);
}
