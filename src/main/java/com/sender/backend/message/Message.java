package com.sender.backend.message;

import com.sender.backend.conversation.Conversation;
import com.sender.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
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

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "client_message_id", nullable = false)
    private UUID clientMessageId;

    @Column(nullable = false)
    private Instant createdAt;

    public Message(Conversation conversation, User sender, String body, UUID clientMessageId) {
        this.conversation = conversation;
        this.sender = sender;
        this.body = body;
        this.clientMessageId = clientMessageId;
        this.createdAt = Instant.now();
    }
}
