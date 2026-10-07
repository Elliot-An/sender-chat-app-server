package com.sender.backend.presence;

import com.sender.backend.friendship.Friendship;
import com.sender.backend.friendship.FriendshipRepository;
import com.sender.backend.realtime.RealtimePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipPresenceFanoutTest {
    @Mock FriendshipRepository friendships;
    @Mock RealtimePublisher realtime;

    @Test
    void sendsPresenceChangedOnlyToAcceptedFriends() {
        when(friendships.findFriendIds(10, Friendship.Status.ACCEPTED)).thenReturn(List.of(20, 21));
        FriendshipPresenceFanout fanout = new FriendshipPresenceFanout(friendships, realtime);

        fanout.deliver(10, PresenceState.ONLINE);

        verify(realtime).sendToUser(20, "PRESENCE_CHANGED", new PresencePayload(10, PresenceState.ONLINE));
        verify(realtime).sendToUser(21, "PRESENCE_CHANGED", new PresencePayload(10, PresenceState.ONLINE));
    }

    @Test
    void skipsWhenUserHasNoFriends() {
        when(friendships.findFriendIds(10, Friendship.Status.ACCEPTED)).thenReturn(List.of());
        FriendshipPresenceFanout fanout = new FriendshipPresenceFanout(friendships, realtime);

        fanout.deliver(10, PresenceState.OFFLINE);

        verifyNoInteractions(realtime);
    }
}
