package com.sender.backend.presence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryPresenceSessionStore implements PresenceSessionStore {
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Session> sessionsById = new ConcurrentHashMap<>();
    private final Map<Integer, Set<String>> sessionsByUser = new ConcurrentHashMap<>();

    public InMemoryPresenceSessionStore(Duration ttl) {
        this(ttl, Clock.systemUTC());
    }

    InMemoryPresenceSessionStore(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public boolean addSession(Integer userId, String sessionId) {
        pruneExpired();
        boolean wasOnline = hasLiveSession(userId);
        put(userId, sessionId);
        return !wasOnline;
    }

    @Override
    public boolean refreshSession(Integer userId, String sessionId) {
        pruneExpired();
        Session existing = sessionsById.get(sessionId);
        if (existing != null && existing.userId().equals(userId)) {
            put(userId, sessionId);
            return false;
        }
        return addSession(userId, sessionId);
    }

    @Override
    public Optional<Integer> removeSession(String sessionId) {
        pruneExpired();
        Session removed = sessionsById.remove(sessionId);
        if (removed == null) {
            return Optional.empty();
        }
        Set<String> userSessions = sessionsByUser.get(removed.userId());
        if (userSessions != null) {
            userSessions.remove(sessionId);
            if (userSessions.isEmpty()) {
                sessionsByUser.remove(removed.userId());
                return Optional.of(removed.userId());
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isOnline(Integer userId) {
        pruneExpired();
        return hasLiveSession(userId);
    }

    @Override
    public Set<Integer> onlineAmong(Collection<Integer> userIds) {
        pruneExpired();
        return userIds.stream().filter(this::hasLiveSession).collect(Collectors.toSet());
    }

    @Override
    public Set<Integer> sweepExpired() {
        Set<Integer> previouslyOnline = Set.copyOf(sessionsByUser.keySet());
        pruneExpired();
        return previouslyOnline.stream()
                .filter(userId -> !sessionsByUser.containsKey(userId))
                .collect(Collectors.toSet());
    }

    private void put(Integer userId, String sessionId) {
        sessionsById.put(sessionId, new Session(userId, clock.instant().plus(ttl)));
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    private boolean hasLiveSession(Integer userId) {
        Set<String> sessionIds = sessionsByUser.get(userId);
        return sessionIds != null && !sessionIds.isEmpty();
    }

    private void pruneExpired() {
        Instant now = clock.instant();
        Iterator<Map.Entry<String, Session>> iterator = sessionsById.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Session> entry = iterator.next();
            if (entry.getValue().expiresAt().isAfter(now)) {
                continue;
            }
            iterator.remove();
            Set<String> userSessions = sessionsByUser.get(entry.getValue().userId());
            if (userSessions != null) {
                userSessions.remove(entry.getKey());
                if (userSessions.isEmpty()) {
                    sessionsByUser.remove(entry.getValue().userId());
                }
            }
        }
    }

    private record Session(Integer userId, Instant expiresAt) {}
}
