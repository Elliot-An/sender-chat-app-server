package com.sender.backend.presence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {
    @Mock PresenceNotifier notifier;

    private InMemoryPresenceSessionStore store;
    private PresenceService presence;

    @BeforeEach
    void setUp() {
        store = new InMemoryPresenceSessionStore(Duration.ofSeconds(90));
        presence = new PresenceService(store, notifier);
    }

    @Test
    void firstSessionMakesUserOnlineAndNotifies() {
        presence.sessionConnected(10, "s1");

        assertTrue(presence.isOnline(10));
        verify(notifier).presenceChanged(10, PresenceState.ONLINE);
    }

    @Test
    void secondSessionDoesNotRenotifyOnline() {
        presence.sessionConnected(10, "s1");
        presence.sessionConnected(10, "s2");

        verify(notifier, times(1)).presenceChanged(10, PresenceState.ONLINE);
        verifyNoMoreInteractions(notifier);
    }

    @Test
    void lastSessionDisconnectMakesUserOffline() {
        presence.sessionConnected(10, "s1");
        presence.sessionConnected(10, "s2");
        presence.sessionDisconnected("s1");
        assertTrue(presence.isOnline(10));

        presence.sessionDisconnected("s2");
        assertFalse(presence.isOnline(10));
        verify(notifier).presenceChanged(10, PresenceState.OFFLINE);
    }

    @Test
    void heartbeatUnknownSessionRegistersWithoutDuplicateOnlineWhenAlreadyOnline() {
        presence.sessionConnected(10, "s1");
        presence.heartbeat(10, "s2");

        assertTrue(presence.isOnline(10));
        verify(notifier, times(1)).presenceChanged(10, PresenceState.ONLINE);
    }

    @Test
    void onlineAmongReturnsOnlyOnlineUsers() {
        presence.sessionConnected(10, "s1");

        assertEquals(Set.of(10), presence.onlineAmong(List.of(10, 11, 12)));
    }
}
