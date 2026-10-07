package com.sender.backend.presence;

import com.sender.backend.friendship.Friendship;
import com.sender.backend.friendship.FriendshipRepository;
import com.sender.backend.realtime.RealtimePublisher;
import org.springframework.stereotype.Component;

@Component
public class FriendshipPresenceFanout {
    private final FriendshipRepository friendships;
    private final RealtimePublisher realtime;

    public FriendshipPresenceFanout(FriendshipRepository friendships, RealtimePublisher realtime) {
        this.friendships = friendships;
        this.realtime = realtime;
    }

    public void deliver(Integer userId, PresenceState state) {
        PresencePayload payload = new PresencePayload(userId, state);
        for (Integer friendId : friendships.findFriendIds(userId, Friendship.Status.ACCEPTED)) {
            realtime.sendToUser(friendId, "PRESENCE_CHANGED", payload);
        }
    }
}
