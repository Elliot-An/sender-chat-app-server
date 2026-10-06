package com.sender.backend.conversation;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "conversations", indexes = @Index(name = "idx_conversations_updated_at", columnList = "updated_at"))
public class Conversation {
    public enum Type { DIRECT, GROUP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Type type;

    @Column(length = 100)
    private String name;

    @Column(name = "direct_key", length = 64, unique = true)
    private String directKey;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Conversation(Type type, String name, String directKey) {
        this.type = type;
        this.name = name;
        this.directKey = directKey;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Conversation direct(String directKey) {
        return new Conversation(Type.DIRECT, null, directKey);
    }

    public static Conversation group(String name) {
        return new Conversation(Type.GROUP, name, null);
    }

    public void touch(Instant at) {
        updatedAt = at;
    }
}
