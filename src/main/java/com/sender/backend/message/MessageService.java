package com.sender.backend.message;

import com.sender.backend.conversation.*;
import com.sender.backend.user.*;
import com.sender.backend.message.MessageDtos.*;
import com.sender.backend.realtime.RealtimePublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class MessageService {
    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final UserRepository users;
    private final RealtimePublisher realtime;

    public MessageService(MessageRepository messages, ConversationRepository conversations,
                          ConversationMemberRepository members, UserRepository users,
                          RealtimePublisher realtime) {
        this.messages = messages;
        this.conversations = conversations;
        this.members = members;
        this.users = users;
        this.realtime = realtime;
    }

    @Transactional
    public MessageResponse send(Integer userId, Integer conversationId, SendRequest request) {
        Conversation conversation = authorizedConversationForUpdate(userId, conversationId);
        Optional<Message> existing = messages.findByConversationIdAndSenderIdAndClientMessageId(
                conversationId, userId, request.clientMessageId());
        if (existing.isPresent()) {
            if (!existing.get().getBody().equals(request.body())) {
                throw error(HttpStatus.CONFLICT, "clientMessageId was already used with a different body");
            }
            return MessageResponse.from(existing.get(), members.findByConversationId(conversationId));
        }
        Message message = messages.save(new Message(conversation, user(userId), request.body(), request.clientMessageId()));
        conversation.touch(message.getCreatedAt());
        List<ConversationMember> conversationMembers = members.findByConversationId(conversationId);
        MessageResponse response = MessageResponse.from(message, conversationMembers);
        MessageCreatedPayload payload = new MessageCreatedPayload(response);
        conversationMembers.forEach(member ->
                realtime.publishAfterCommit(member.getUser().getId(), "MESSAGE_CREATED", payload));
        return response;
    }

    @Transactional(readOnly = true)
    public Page list(Integer userId, Integer conversationId, String cursor, int limit) {
        authorizedConversation(userId, conversationId);
        int size = Math.clamp(limit, 1, 100);
        List<Message> values;
        if (cursor == null || cursor.isBlank()) {
            values = messages.findByConversationIdOrderByCreatedAtDescIdDesc(conversationId, PageRequest.of(0, size + 1));
        } else {
            Cursor decoded = decode(cursor);
            values = messages.findBefore(conversationId, decoded.createdAt(), decoded.id(), PageRequest.of(0, size + 1));
        }
        boolean more = values.size() > size;
        if (more) values = values.subList(0, size);
        String next = more ? encode(values.getLast()) : null;
        List<ConversationMember> conversationMembers = members.findByConversationId(conversationId);
        return new Page(values.stream().map(message -> MessageResponse.from(message, conversationMembers)).toList(), next, more);
    }

    private Conversation authorizedConversation(Integer userId, Integer conversationId) {
        if (!members.existsByConversationIdAndUserId(conversationId, userId)) {
            if (!conversations.existsById(conversationId)) throw error(HttpStatus.NOT_FOUND, "Conversation not found");
            throw error(HttpStatus.FORBIDDEN, "You are not a member of this conversation");
        }
        return conversations.findById(conversationId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private Conversation authorizedConversationForUpdate(Integer userId, Integer conversationId) {
        if (!members.existsByConversationIdAndUserId(conversationId, userId)) {
            if (!conversations.existsById(conversationId)) throw error(HttpStatus.NOT_FOUND, "Conversation not found");
            throw error(HttpStatus.FORBIDDEN, "You are not a member of this conversation");
        }
        return conversations.findByIdForUpdate(conversationId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private User user(Integer id) {
        return users.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String encode(Message message) {
        String value = message.getCreatedAt().toString() + "|" + message.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decode(String cursor) {
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", 2);
            return new Cursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (RuntimeException exception) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid message cursor");
        }
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    private record Cursor(Instant createdAt, Long id) {}
}
