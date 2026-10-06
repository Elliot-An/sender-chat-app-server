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

    @Transactional(readOnly = true)
    public SearchPage search(Integer userId, Integer conversationId, String query, String cursor, int limit) {
        authorizedConversation(userId, conversationId);
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 2 || normalized.length() > 200) {
            throw error(HttpStatus.BAD_REQUEST, "Search query must contain 2 to 200 characters");
        }
        int size = Math.clamp(limit, 1, 50);
        List<MessageRepository.SearchRow> rows = cursor == null || cursor.isBlank()
                ? messages.search(conversationId, normalized, PageRequest.of(0, size + 1))
                : searchBefore(conversationId, normalized, cursor, size);
        boolean more = rows.size() > size;
        if (more) rows = rows.subList(0, size);
        List<ConversationMember> conversationMembers = members.findByConversationId(conversationId);
        Map<Long, Message> byId = new HashMap<>();
        messages.findAllById(rows.stream().map(MessageRepository.SearchRow::getId).toList())
                .forEach(message -> byId.put(message.getId(), message));
        String next = more ? encodeSearchCursor(rows.getLast(), normalized) : null;
        List<SearchResponse> results = rows.stream()
                .map(row -> new SearchResponse(MessageResponse.from(byId.get(row.getId()), conversationMembers),
                        row.getRank(), row.getSnippet()))
                .toList();
        return new SearchPage(results, next, more);
    }

    private List<MessageRepository.SearchRow> searchBefore(Integer conversationId, String query,
                                                            String cursor, int size) {
        SearchCursor decoded = decodeSearchCursor(cursor);
        if (!decoded.query().equals(query)) {
            throw error(HttpStatus.BAD_REQUEST, "Search cursor does not match the query");
        }
        return messages.searchBefore(conversationId, query, decoded.rank(), decoded.createdAt(),
                decoded.id(), PageRequest.of(0, size + 1));
    }

    private String encodeSearchCursor(MessageRepository.SearchRow row, String query) {
        String encodedQuery = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(query.getBytes(StandardCharsets.UTF_8));
        String value = encodedQuery + "|" + row.getRank() + "|" + row.getCreatedAt() + "|" + row.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private SearchCursor decodeSearchCursor(String cursor) {
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", 4);
            String query = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            return new SearchCursor(query, Double.parseDouble(parts[1]), Instant.parse(parts[2]), Long.parseLong(parts[3]));
        } catch (RuntimeException exception) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid search cursor");
        }
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
    private record SearchCursor(String query, double rank, Instant createdAt, Long id) {}
}
