package com.sender.backend.friendship;

import com.sender.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "friendships", uniqueConstraints = @UniqueConstraint(name = "uk_friendships_pair", columnNames = {"requester_id", "addressee_id"}))
public class Friendship {
    public enum Status { PENDING, ACCEPTED, DECLINED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addressee_id", nullable = false)
    private User addressee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public Friendship(User requester, User addressee) {
        this.requester = requester;
        this.addressee = addressee;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void accept() {
        status = Status.ACCEPTED;
        updatedAt = Instant.now();
    }

    public void decline() {
        status = Status.DECLINED;
        updatedAt = Instant.now();
    }
}
