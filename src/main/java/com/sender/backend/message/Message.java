package com.sender.backend.message;

import com.sender.backend.conversation.Conversation;
import com.sender.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "messages", indexes = @Index(name = "idx_messages_conversation_created", columnList = "conversation_id, created_at, id"))
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(length = 4000)
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<MessageAttachment> attachments = new ArrayList<>();

    @Column(name = "client_message_id", nullable = false)
    private UUID clientMessageId;

    @Column(nullable = false)
    private Instant createdAt;

    public Message(Conversation conversation, User sender, String body, UUID clientMessageId,
                   List<MessageAttachment> attachments) {
        this.conversation = conversation;
        this.sender = sender;
        this.body = body;
        this.clientMessageId = clientMessageId;
        this.attachments = attachments == null ? List.of() : List.copyOf(attachments);
        this.createdAt = Instant.now();
    }
}
