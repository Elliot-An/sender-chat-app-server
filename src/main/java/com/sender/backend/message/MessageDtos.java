package com.sender.backend.message;

import com.sender.backend.auth.AuthDtos.PublicUser;
import com.sender.backend.conversation.ConversationMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class MessageDtos {
    private MessageDtos() {}

    public record AttachmentCommitRequest(
            @NotBlank @Size(max = 512) String objectKey,
            @NotBlank @Size(max = 255) String originalFilename) {}

    public record SendRequest(
            @Size(max = 4000) String body,
            @NotNull UUID clientMessageId,
            @Size(max = 5) @Valid List<AttachmentCommitRequest> attachments) {
        public SendRequest {
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    public record AttachmentUploadRequest(
            @NotBlank @Size(max = 100) String contentType,
            @NotNull @Min(1) Long contentLength,
            @NotBlank @Size(max = 255) String originalFilename) {}

    public record AttachmentUploadResponse(String putUrl, String objectKey, Instant expiresAt) {}

    public record AttachmentDownloadResponse(String getUrl, Instant expiresAt) {}

    public record AttachmentResponse(
            UUID id,
            String originalFilename,
            String contentType,
            long sizeBytes,
            int sortOrder) {
        public static AttachmentResponse from(MessageAttachment attachment) {
            return new AttachmentResponse(
                    attachment.id(),
                    attachment.originalFilename(),
                    attachment.contentType(),
                    attachment.sizeBytes(),
                    attachment.sortOrder());
        }
    }

    public record MessageReceipt(Integer viewerId, Instant deliveredAt, Instant readAt) {}

    public record MessageResponse(Long id, Integer conversationId, PublicUser sender,
                                  String body, UUID clientMessageId, Instant createdAt,
                                  List<AttachmentResponse> attachments,
                                  List<MessageReceipt> receipts) {
        public static MessageResponse from(Message message) {
            return from(message, List.of());
        }

        public static MessageResponse from(Message message, List<ConversationMember> members) {
            List<MessageReceipt> receipts = members.stream()
                    .map(member -> receiptFor(message, member))
                    .filter(Objects::nonNull)
                    .toList();
            List<AttachmentResponse> attachments = message.getAttachments().stream()
                    .sorted(Comparator.comparingInt(MessageAttachment::sortOrder))
                    .map(AttachmentResponse::from)
                    .toList();
            return new MessageResponse(message.getId(), message.getConversation().getId(),
                    PublicUser.from(message.getSender()), message.getBody(), message.getClientMessageId(),
                    message.getCreatedAt(), attachments, receipts);
        }

        private static MessageReceipt receiptFor(Message message, ConversationMember member) {
            Instant deliveredAt = through(member.getLastDeliveredMessageId(), member.getLastDeliveredAt(), message)
                    ? member.getLastDeliveredAt() : null;
            Instant readAt = through(member.getLastReadMessageId(), member.getLastReadAt(), message)
                    ? member.getLastReadAt() : null;
            return deliveredAt == null && readAt == null
                    ? null
                    : new MessageReceipt(member.getUser().getId(), deliveredAt, readAt);
        }

        private static boolean through(Long cursorId, Instant cursorAt, Message message) {
            return cursorId != null && cursorAt != null
                    && (cursorAt.isAfter(message.getCreatedAt())
                    || (cursorAt.equals(message.getCreatedAt()) && cursorId >= message.getId()));
        }
    }

    public record MessageCreatedPayload(MessageResponse message) {}

    public record Page(List<MessageResponse> items, String nextCursor, boolean hasMore) {}

    public record SearchResponse(MessageResponse message, double rank, String snippet) {}

    public record SearchPage(List<SearchResponse> items, String nextCursor, boolean hasMore) {}
}
