package com.sender.backend.message;

import com.sender.backend.auth.AuthDtos.PublicUser;
import com.sender.backend.conversation.ConversationMember;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class MessageDtos {
    private MessageDtos() {}

    public record SendRequest(
            @NotBlank @Size(max = 4000) String body,
            @NotNull UUID clientMessageId) {}

    public record MessageReceipt(Integer viewerId, Instant deliveredAt, Instant readAt) {}

    public record MessageResponse(Long id, Integer conversationId, PublicUser sender,
                                  String body, UUID clientMessageId, Instant createdAt,
                                  List<MessageReceipt> receipts) {
        public static MessageResponse from(Message message) {
            return from(message, List.of());
        }

        public static MessageResponse from(Message message, List<ConversationMember> members) {
            List<MessageReceipt> receipts = members.stream()
                    .map(member -> receiptFor(message, member))
                    .filter(Objects::nonNull)
                    .toList();
            return new MessageResponse(message.getId(), message.getConversation().getId(),
                    PublicUser.from(message.getSender()), message.getBody(), message.getClientMessageId(),
                    message.getCreatedAt(), receipts);
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
