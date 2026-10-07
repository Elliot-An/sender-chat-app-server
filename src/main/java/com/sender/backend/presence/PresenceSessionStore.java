package com.sender.backend.presence;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Ephemeral multi-session presence registry. Implementations must degrade safely
 * (treat failures as offline / no transition) rather than affecting durable state.
 */
public interface PresenceSessionStore {
    /** @return true when this was the user's first live session (became online) */
    boolean addSession(Integer userId, String sessionId);

    /** Refresh TTL for an existing session; registers the session if missing. @return true if became online */
    boolean refreshSession(Integer userId, String sessionId);

    /** @return the user id that became offline, if this removed their last session */
    Optional<Integer> removeSession(String sessionId);

    boolean isOnline(Integer userId);

    Set<Integer> onlineAmong(Collection<Integer> userIds);

    /** Remove expired sessions and return user ids that became offline because of expiry. */
    Set<Integer> sweepExpired();
}
