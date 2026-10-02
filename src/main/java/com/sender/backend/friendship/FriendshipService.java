package com.sender.backend.friendship;

import com.sender.backend.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

import static com.sender.backend.friendship.FriendshipDtos.*;

@Service
public class FriendshipService {
    private final FriendshipRepository friendships;
    private final UserRepository users;

    public FriendshipService(FriendshipRepository friendships, UserRepository users) {
        this.friendships = friendships;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<FriendshipResponse> accepted(Integer userId) {
        return friendships.findAcceptedForUser(userId, Friendship.Status.ACCEPTED).stream().map(FriendshipResponse::from).toList();
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
        return FriendshipResponse.from(friendships.save(new Friendship(requester, addressee)));
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
        return FriendshipResponse.from(friendship);
    }

    private User findUser(Integer id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
