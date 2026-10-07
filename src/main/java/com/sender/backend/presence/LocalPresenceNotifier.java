package com.sender.backend.presence;

/**
 * Single-process notifier used when Redis pub/sub is unavailable (tests / memory store).
 */
public class LocalPresenceNotifier implements PresenceNotifier {
    private final FriendshipPresenceFanout fanout;

    public LocalPresenceNotifier(FriendshipPresenceFanout fanout) {
        this.fanout = fanout;
    }

    @Override
    public void presenceChanged(Integer userId, PresenceState state) {
        fanout.deliver(userId, state);
    }
}
