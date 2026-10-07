package com.sender.backend.user;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users", indexes = {
		@Index(name = "idx_users_username", columnList = "username"),
		@Index(name = "idx_users_email", columnList = "email")
})
public class User {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, unique = true, length = 30)
	private String username;

	@Column(nullable = false, unique = true, length = 320)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false, length = 30)
	private String displayName;

	@Column(name = "avatar_url", length = 2048)
	private String avatarUrl;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	public User(String username, String email, String passwordHash) {
		this.username = username;
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = username;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}
}
