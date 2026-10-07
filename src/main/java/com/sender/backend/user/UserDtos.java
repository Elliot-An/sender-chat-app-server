package com.sender.backend.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UserDtos {
	private UserDtos() {}

	public record UpdateProfileRequest(
			@Schema(description = "Display name shown to other users", example = "Alice Nguyen")
			@Size(max = 30) String displayName,
			@Schema(description = "Object key returned by the avatar upload endpoint")
			@Size(max = 512) String avatarObjectKey) {}

	public record AvatarUploadRequest(
			@Schema(description = "Image MIME type", example = "image/png")
			@NotBlank String contentType,
			@Schema(description = "Exact byte length of the file to upload", example = "1200")
			@NotNull @Min(1) Long contentLength) {}

	public record AvatarUploadResponse(
			String putUrl,
			String objectKey,
			String publicUrl,
			java.time.Instant expiresAt) {}
}
