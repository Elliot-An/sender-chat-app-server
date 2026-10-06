package com.sender.backend.conversation;

import com.sender.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "conversation_members")
public class ConversationMember {
    @EmbeddedId
    private Id id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("conversationId")
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant joinedAt;

    @Column(name = "last_delivered_message_id")
    private Long lastDeliveredMessageId;

    @Column(name = "last_delivered_at")
    private Instant lastDeliveredAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    public ConversationMember(Conversation conversation, User user) {
        this.conversation = conversation;
        this.user = user;
        this.id = new Id(conversation.getId(), user.getId());
        this.joinedAt = Instant.now();
    }

    public void advanceDelivered(Long messageId, Instant messageCreatedAt) {
        if (isAfter(messageId, messageCreatedAt, lastDeliveredMessageId, lastDeliveredAt)) {
            lastDeliveredMessageId = messageId;
            lastDeliveredAt = messageCreatedAt;
        }
    }

    public void advanceRead(Long messageId, Instant messageCreatedAt) {
        advanceDelivered(messageId, messageCreatedAt);
        if (isAfter(messageId, messageCreatedAt, lastReadMessageId, lastReadAt)) {
            lastReadMessageId = messageId;
            lastReadAt = messageCreatedAt;
        }
    }

    private boolean isAfter(Long messageId, Instant messageCreatedAt, Long currentMessageId, Instant currentCreatedAt) {
        return currentCreatedAt == null
                || messageCreatedAt.isAfter(currentCreatedAt)
                || (messageCreatedAt.equals(currentCreatedAt)
                    && (currentMessageId == null || messageId > currentMessageId));
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {
        private Integer conversationId;
        private Integer userId;

        public Id(Integer conversationId, Integer userId) {
            this.conversationId = conversationId;
            this.userId = userId;
        }
    }
}
