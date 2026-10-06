package com.sender.backend.conversation;

import com.sender.backend.auth.AuthDtos.PublicUser;
import com.sender.backend.friendship.Friendship;
import com.sender.backend.friendship.FriendshipRepository;
import com.sender.backend.user.*;
import com.sender.backend.conversation.ConversationDtos.*;
import com.sender.backend.message.MessageRepository;
import com.sender.backend.message.MessageProgressService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class ConversationService {
    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final MessageProgressService progress;

    public ConversationService(ConversationRepository conversations, ConversationMemberRepository members,
                               MessageRepository messages, FriendshipRepository friendships, UserRepository users,
                               MessageProgressService progress) {
        this.conversations = conversations;
        this.members = members;
        this.messages = messages;
        this.friendships = friendships;
        this.users = users;
        this.progress = progress;
    }

    @Transactional
    public ConversationResponse direct(Integer userId, DirectRequest request) {
        if (userId.equals(request.otherUserId())) {
            throw error(HttpStatus.BAD_REQUEST, "You cannot create a conversation with yourself");
        }
        User other = user(request.otherUserId());
        requireFriends(userId, other.getId());
        String key = directKey(userId, other.getId());
        Conversation conversation = conversations.findByDirectKey(key).orElse(null);
        if (conversation == null) {
            Instant now = Instant.now();
            boolean inserted = conversations.insertDirectIfAbsent(key, now, now) == 1;
            conversation = conversations.findByDirectKey(key)
                    .orElseThrow(() -> error(HttpStatus.CONFLICT, "Could not create direct conversation"));
            if (inserted) {
                addMember(conversation, user(userId));
                addMember(conversation, other);
            }
        }
        return response(conversation);
    }

    @Transactional
    public ConversationResponse group(Integer userId, GroupRequest request) {
        Set<Integer> ids = new LinkedHashSet<>(request.memberIds());
        ids.remove(userId);
        if (ids.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "A group needs at least one friend");
        User creator = user(userId);
        Conversation conversation = conversations.save(Conversation.group(request.name().trim()));
        addMember(conversation, creator);
        for (Integer memberId : ids) {
            User member = user(memberId);
            requireFriends(userId, memberId);
            addMember(conversation, member);
        }
        return response(conversation);
    }

    @Transactional(readOnly = true)
    public Page<Summary> list(Integer userId, String cursor, int limit) {
        int size = Math.min(Math.max(limit, 1), 50);
        List<Conversation> values;
        if (cursor == null || cursor.isBlank()) {
            values = conversations.findForUser(userId, PageRequest.of(0, size + 1));
        } else {
            Cursor decoded = decode(cursor);
            values = conversations.findForUserBefore(userId, decoded.updatedAt(), decoded.id(), PageRequest.of(0, size + 1));
        }
        boolean more = values.size() > size;
        if (more) values = values.subList(0, size);
        List<Summary> result = values.stream().map(c -> summary(c, userId)).toList();
        String next = more ? encode(values.getLast()) : null;
        return new Page<>(result, next, more);
    }

    @Transactional(readOnly = true)
    public ConversationResponse get(Integer userId, Integer conversationId) {
        return response(memberConversation(userId, conversationId));
    }

    @Transactional
    public ConversationResponse addMember(Integer userId, Integer conversationId, MemberRequest request) {
        Conversation conversation = lockedMemberConversation(userId, conversationId);
        if (conversation.getType() != Conversation.Type.GROUP) throw error(HttpStatus.BAD_REQUEST, "Direct conversations have no group members");
        if (userId.equals(request.userId())) throw error(HttpStatus.BAD_REQUEST, "You are already a member");
        requireFriends(userId, request.userId());
        if (members.existsByConversationIdAndUserId(conversationId, request.userId())) {
            throw error(HttpStatus.CONFLICT, "User is already a member");
        }
        addMember(conversation, user(request.userId()));
        conversation.touch(Instant.now());
        return response(conversation);
    }

    @Transactional
    public ConversationResponse removeMember(Integer userId, Integer conversationId, Integer targetUserId) {
        Conversation conversation = lockedMemberConversation(userId, conversationId);
        if (conversation.getType() != Conversation.Type.GROUP) throw error(HttpStatus.BAD_REQUEST, "Direct conversations have no group members");
        if (!members.existsByConversationIdAndUserId(conversationId, targetUserId)) {
            throw error(HttpStatus.NOT_FOUND, "Conversation member not found");
        }
        if (members.countByConversationId(conversationId) <= 1) {
            throw error(HttpStatus.CONFLICT, "A group cannot be empty");
        }
        members.deleteById(new ConversationMember.Id(conversationId, targetUserId));
        conversation.touch(Instant.now());
        return response(conversation);
    }

    private Summary summary(Conversation conversation, Integer userId) {
        PublicUser other = null;
        if (conversation.getType() == Conversation.Type.DIRECT) {
            other = members.findByConversationId(conversation.getId()).stream()
                    .map(ConversationMember::getUser)
                    .filter(user -> !user.getId().equals(userId))
                    .findFirst().map(PublicUser::from).orElse(null);
        }
        return new Summary(conversation.getId(), conversation.getType(), conversation.getName(), other,
                messages.findTopByConversationIdOrderByCreatedAtDescIdDesc(conversation.getId()).map(LatestMessage::from).orElse(null),
                Math.toIntExact(progress.unreadCount(userId, conversation.getId())), conversation.getUpdatedAt());
    }

    private ConversationResponse response(Conversation conversation) {
        return ConversationResponse.from(conversation, members.findByConversationId(conversation.getId()));
    }

    private Conversation memberConversation(Integer userId, Integer conversationId) {
        if (!members.existsByConversationIdAndUserId(conversationId, userId)) {
            throw error(HttpStatus.FORBIDDEN, "You are not a member of this conversation");
        }
        return conversations.findById(conversationId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private Conversation lockedMemberConversation(Integer userId, Integer conversationId) {
        if (!members.existsByConversationIdAndUserId(conversationId, userId)) {
            if (!conversations.existsById(conversationId)) {
                throw error(HttpStatus.NOT_FOUND, "Conversation not found");
            }
            throw error(HttpStatus.FORBIDDEN, "You are not a member of this conversation");
        }
        return conversations.findByIdForUpdate(conversationId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private void addMember(Conversation conversation, User user) {
        members.save(new ConversationMember(conversation, user));
    }

    private User user(Integer id) {
        return users.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void requireFriends(Integer first, Integer second) {
        boolean accepted = friendships.findBetween(first, second).stream()
                .anyMatch(friendship -> friendship.getStatus() == Friendship.Status.ACCEPTED);
        if (!accepted) throw error(HttpStatus.FORBIDDEN, "Only accepted friends can be in conversations");
    }

    private String directKey(Integer first, Integer second) {
        return first < second ? first + ":" + second : second + ":" + first;
    }

    private String encode(Conversation conversation) {
        String value = conversation.getUpdatedAt() + "|" + conversation.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decode(String cursor) {
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", 2);
            return new Cursor(Instant.parse(parts[0]), Integer.parseInt(parts[1]));
        } catch (RuntimeException exception) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid conversation cursor");
        }
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    private record Cursor(Instant updatedAt, Integer id) {}
}
