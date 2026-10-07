package com.sender.backend.auth;

import com.sender.backend.user.User;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class AuthDtos {
	private AuthDtos() {}

	public record RegisterRequest(
			@NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,30}$") String username,
			@NotBlank @Email @Size(max = 320) String email,
			@NotBlank @Size(min = 8, max = 72) String password) {}

	public record LoginRequest(
			@NotBlank @Email @Size(max = 320) String email,
			@NotBlank String password) {}

	public record LogoutRequest(boolean allDevices) {}

	public record ChangePasswordRequest(
			@NotBlank String currentPassword,
			@NotBlank @Size(min = 8, max = 72) String newPassword) {}

	public record PublicUser(Integer id, String username, String email, String displayName, String avatarUrl) {
		public static PublicUser from(User user) {
			return new PublicUser(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(),
					user.getAvatarUrl());
		}
	}

	public record AuthResponse(PublicUser user, String accessToken, Instant accessTokenExpiresAt) {}
}
