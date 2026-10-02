package com.sender.backend.auth;

import com.sender.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "auth_sessions", indexes = {
		@Index(name = "idx_auth_sessions_user", columnList = "user_id"),
		@Index(name = "idx_auth_sessions_expires", columnList = "expires_at")
})
public class AuthSession {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public AuthSession(User user, String tokenHash, Instant expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = Instant.now();
	}

	public boolean isActive(Instant now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}

	public void revoke() { revokedAt = Instant.now(); }
}
