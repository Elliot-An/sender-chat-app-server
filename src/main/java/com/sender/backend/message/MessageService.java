package com.sender.backend.message;

import com.sender.backend.conversation.*;
import com.sender.backend.message.MessageDtos.*;
import com.sender.backend.realtime.RealtimePublisher;
import com.sender.backend.storage.ObjectStorage;
import com.sender.backend.user.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MessageService {
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf", "text/plain");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf",
            "text/plain", "txt");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
    private static final Pattern OWNED_KEY = Pattern.compile(
            "^message-attachments/(\\d+)/(\\d+)/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|jpeg|png|webp|pdf|txt)$");
    private static final long MAX_TOTAL_BYTES = 25L * 1024 * 1024;

    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final UserRepository users;
    private final RealtimePublisher realtime;
    private final ObjectStorage storage;
    private final Duration presignTtl;
    private final long maxBytesPerFile;

    public MessageService(MessageRepository messages, ConversationRepository conversations,
                          ConversationMemberRepository members, UserRepository users,
                          RealtimePublisher realtime, ObjectStorage storage,
                          @Value("${app.s3.presign-seconds:300}") long presignSeconds,
                          @Value("${app.attachment.max-bytes:10485760}") long maxBytesPerFile) {
        this.messages = messages;
        this.conversations = conversations;
        this.members = members;
        this.users = users;
        this.realtime = realtime;
        this.storage = storage;
        this.presignTtl = Duration.ofSeconds(presignSeconds);
        this.maxBytesPerFile = maxBytesPerFile;
    }

    public AttachmentUploadResponse createAttachmentUpload(Integer userId, Integer conversationId,
                                                           AttachmentUploadRequest request) {
        authorizedConversation(userId, conversationId);
        String contentType = normalizeContentType(request.contentType());
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw error(HttpStatus.BAD_REQUEST, "Attachment type is not allowed");
        }
        long length = request.contentLength() == null ? 0 : request.contentLength();
        if (length < 1) {
            throw error(HttpStatus.BAD_REQUEST, "Attachment file size is required");
        }
        if (length > maxBytesPerFile) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "Attachment must be 10MB or smaller");
        }
        String filename = sanitizeFilename(request.originalFilename());
        String key = "message-attachments/" + conversationId + "/" + userId + "/"
                + UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
        ObjectStorage.PresignedUpload upload = storage.presignPut(key, contentType, length, presignTtl);
        return new AttachmentUploadResponse(upload.putUrl().toString(), key, upload.expiresAt());
    }

    @Transactional
    public MessageResponse send(Integer userId, Integer conversationId, SendRequest request) {
        Conversation conversation = authorizedConversationForUpdate(userId, conversationId);
        String body = normalizeBody(request.body());
        List<AttachmentCommitRequest> commits = request.attachments();
        if (body == null && commits.isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "Provide a message body and/or attachments");
        }
        if (body != null && (body.length() < 1 || body.length() > 4000)) {
            throw error(HttpStatus.BAD_REQUEST, "Message body must be 1 to 4000 characters");
        }

        Optional<Message> existing = messages.findByConversationIdAndSenderIdAndClientMessageId(
                conversationId, userId, request.clientMessageId());
        if (existing.isPresent()) {
            Message prior = existing.get();
            if (!Objects.equals(normalizeBody(prior.getBody()), body)
                    || !sameObjectKeys(prior.getAttachments(), commits)) {
                throw error(HttpStatus.CONFLICT, "clientMessageId was already used with different content");
            }
            return MessageResponse.from(prior, members.findByConversationId(conversationId));
        }

        if (commits.size() > 5) {
            throw error(HttpStatus.BAD_REQUEST, "A message may include at most 5 attachments");
        }
        List<MessageAttachment> attachments = commitAttachments(userId, conversationId, commits);
        Message message = messages.save(new Message(conversation, user(userId), body, request.clientMessageId(), attachments));
        conversation.touch(message.getCreatedAt());
        List<ConversationMember> conversationMembers = members.findByConversationId(conversationId);
        MessageResponse response = MessageResponse.from(message, conversationMembers);
        MessageCreatedPayload payload = new MessageCreatedPayload(response);
        conversationMembers.forEach(member ->
                realtime.publishAfterCommit(member.getUser().getId(), "MESSAGE_CREATED", payload));
        return response;
    }

    @Transactional(readOnly = true)
    public AttachmentDownloadResponse createDownload(Integer userId, Integer conversationId,
                                                     Long messageId, UUID attachmentId) {
        authorizedConversation(userId, conversationId);
        Message message = messages.findById(messageId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Message not found"));
        if (!message.getConversation().getId().equals(conversationId)) {
            throw error(HttpStatus.NOT_FOUND, "Message not found");
        }
        MessageAttachment attachment = message.getAttachments().stream()
                .filter(item -> item.id().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Attachment not found"));
        ObjectStorage.PresignedDownload download = storage.presignGet(attachment.objectKey(), presignTtl);
        return new AttachmentDownloadResponse(download.getUrl().toString(), download.expiresAt());
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

    private List<MessageAttachment> commitAttachments(Integer userId, Integer conversationId,
                                                      List<AttachmentCommitRequest> commits) {
        if (commits.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        long total = 0;
        List<MessageAttachment> attachments = new ArrayList<>();
        int order = 0;
        for (AttachmentCommitRequest commit : commits) {
            String key = commit.objectKey().trim();
            if (!seen.add(key)) {
                throw error(HttpStatus.BAD_REQUEST, "Duplicate attachment object key");
            }
            assertOwnedKey(userId, conversationId, key);
            String filename = sanitizeFilename(commit.originalFilename());
            ObjectStorage.StoredObject object = storage.head(key)
                    .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "Attachment object was not found"));
            String type = normalizeContentType(object.contentType());
            if (!ALLOWED_TYPES.contains(type)) {
                throw error(HttpStatus.BAD_REQUEST, "Uploaded attachment has an unsupported type");
            }
            if (object.contentLength() < 1 || object.contentLength() > maxBytesPerFile) {
                throw error(HttpStatus.BAD_REQUEST, "Uploaded attachment size is invalid");
            }
            total += object.contentLength();
            if (total > MAX_TOTAL_BYTES) {
                throw error(HttpStatus.PAYLOAD_TOO_LARGE, "Attachments must total 25MB or less");
            }
            attachments.add(new MessageAttachment(
                    UUID.randomUUID(), key, filename, type, object.contentLength(), order++));
        }
        return List.copyOf(attachments);
    }

    private boolean sameObjectKeys(List<MessageAttachment> stored, List<AttachmentCommitRequest> commits) {
        Set<String> left = stored.stream().map(MessageAttachment::objectKey).collect(Collectors.toSet());
        Set<String> right = commits.stream().map(item -> item.objectKey().trim()).collect(Collectors.toSet());
        return left.equals(right);
    }

    private void assertOwnedKey(Integer userId, Integer conversationId, String key) {
        var matcher = OWNED_KEY.matcher(key);
        if (!matcher.matches()
                || !String.valueOf(conversationId).equals(matcher.group(1))
                || !String.valueOf(userId).equals(matcher.group(2))) {
            throw error(HttpStatus.BAD_REQUEST, "Attachment object key is invalid");
        }
    }

    private String sanitizeFilename(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > 255 || CONTROL.matcher(name).find()
                || name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw error(HttpStatus.BAD_REQUEST, "Original filename is invalid");
        }
        return name;
    }

    private String normalizeBody(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeContentType(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.split(";")[0].trim().toLowerCase(Locale.ROOT);
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
