package com.sender.backend.friendship;

import com.sender.backend.presence.PresenceService;
import com.sender.backend.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.sender.backend.realtime.RealtimePublisher;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.sender.backend.friendship.FriendshipDtos.*;

@Service
public class FriendshipService {
    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final RealtimePublisher realtime;
    private final PresenceService presence;

    public FriendshipService(FriendshipRepository friendships, UserRepository users, RealtimePublisher realtime,
                             PresenceService presence) {
        this.friendships = friendships;
        this.users = users;
        this.realtime = realtime;
        this.presence = presence;
    }

    @Transactional(readOnly = true)
    public List<FriendshipResponse> accepted(Integer userId) {
        List<Friendship> accepted = friendships.findAcceptedForUser(userId, Friendship.Status.ACCEPTED);
        Set<Integer> peerIds = new HashSet<>();
        for (Friendship friendship : accepted) {
            peerIds.add(friendship.getRequester().getId());
            peerIds.add(friendship.getAddressee().getId());
        }
        peerIds.remove(userId);
        Set<Integer> onlinePeers = presence.onlineAmong(peerIds);
        return accepted.stream()
                .map(friendship -> FriendshipResponse.from(
                        friendship,
                        onlinePeers.contains(friendship.getRequester().getId()),
                        onlinePeers.contains(friendship.getAddressee().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendshipResponse> incomingRequests(Integer userId) {
        return friendships.findByAddresseeIdAndStatusOrderByCreatedAtDesc(userId, Friendship.Status.PENDING)
                .stream().map(FriendshipResponse::from).toList();
    }

    @Transactional
    public FriendshipResponse request(Integer requesterId, Integer addresseeId) {
        if (requesterId.equals(addresseeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot add yourself");
        }
        User requester = findUser(requesterId);
        User addressee = findUser(addresseeId);
        List<Friendship> existing = friendships.findBetween(requesterId, addresseeId);
        if (!existing.isEmpty()) {
            Friendship current = existing.getFirst();
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A friendship already exists");
        }
        Friendship friendship = friendships.save(new Friendship(requester, addressee));
        FriendshipResponse response = FriendshipResponse.from(friendship);
        realtime.publishAfterCommit(addresseeId, "FRIEND_REQUEST_CREATED", new FriendRequestCreatedPayload(response));
        return response;
    }

    @Transactional
    public FriendshipResponse decide(Integer userId, Integer friendshipId, Decision decision) {
        Friendship friendship = friendships.findById(friendshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Friend request not found"));
        if (!friendship.getAddressee().getId().equals(userId) || friendship.getStatus() != Friendship.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot decide this request");
        }
        if (decision == Decision.ACCEPT) friendship.accept();
        else friendship.decline();
        FriendshipResponse response;
        if (decision == Decision.ACCEPT) {
            response = FriendshipResponse.from(
                    friendship,
                    presence.isOnline(friendship.getRequester().getId()),
                    presence.isOnline(friendship.getAddressee().getId()));
            realtime.publishAfterCommit(
                    friendship.getRequester().getId(),
                    "FRIENDSHIP_UPDATED",
                    new FriendshipUpdatedPayload(response)
            );
        } else {
            response = FriendshipResponse.from(friendship);
        }
        return response;
    }

    private User findUser(Integer id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private record FriendRequestCreatedPayload(FriendshipResponse friendship) {}
    private record FriendshipUpdatedPayload(FriendshipResponse friendship) {}
}
