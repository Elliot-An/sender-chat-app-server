package com.sender.backend.user;

import com.sender.backend.auth.AuthDtos.PublicUser;
import com.sender.backend.storage.ObjectPublicUrl;
import com.sender.backend.storage.ObjectStorage;
import com.sender.backend.user.UserDtos.AvatarUploadRequest;
import com.sender.backend.user.UserDtos.AvatarUploadResponse;
import com.sender.backend.user.UserDtos.UpdateProfileRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserService {
	private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	private static final Map<String, String> EXTENSIONS = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png",
			"image/webp", "webp");
	private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
	private static final Pattern OWNED_KEY = Pattern.compile(
			"^avatars/(\\d+)/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|jpeg|png|webp)$");

	private final UserRepository users;
	private final ObjectStorage storage;
	private final ObjectPublicUrl publicUrls;
	private final Duration presignTtl;
	private final long maxBytes;

	public UserService(
			UserRepository users,
			ObjectStorage storage,
			ObjectPublicUrl publicUrls,
			@Value("${app.s3.presign-seconds:300}") long presignSeconds,
			@Value("${app.avatar.max-bytes:2097152}") long maxBytes) {
		this.users = users;
		this.storage = storage;
		this.publicUrls = publicUrls;
		this.presignTtl = Duration.ofSeconds(presignSeconds);
		this.maxBytes = maxBytes;
	}

	public AvatarUploadResponse createAvatarUpload(Integer userId, AvatarUploadRequest request) {
		String contentType = request.contentType() == null ? "" : request.contentType().trim().toLowerCase(Locale.ROOT);
		if (!ALLOWED_TYPES.contains(contentType)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar must be a JPEG, PNG, or WebP image");
		}
		long length = request.contentLength() == null ? 0 : request.contentLength();
		if (length < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar file size is required");
		}
		if (length > maxBytes) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Avatar must be 2MB or smaller");
		}
		String key = "avatars/" + userId + "/" + UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
		ObjectStorage.PresignedUpload upload = storage.presignPut(key, contentType, length, presignTtl);
		return new AvatarUploadResponse(upload.putUrl().toString(), key, publicUrls.of(key), upload.expiresAt());
	}

	@Transactional
	public PublicUser updateProfile(Integer userId, UpdateProfileRequest request) {
		boolean hasName = request.displayName() != null;
		boolean hasAvatar = request.avatarObjectKey() != null && !request.avatarObjectKey().isBlank();
		if (!hasName && !hasAvatar) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a display name and/or avatar object key");
		}
		User user = users.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (hasName) {
			user.setDisplayName(normalizeDisplayName(request.displayName()));
		}
		String previousKey = null;
		if (hasAvatar) {
			String key = request.avatarObjectKey().trim();
			assertOwnedKey(userId, key);
			ObjectStorage.StoredObject object = storage.head(key)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar object was not found"));
			String type = object.contentType() == null ? "" : object.contentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
			if (!ALLOWED_TYPES.contains(type)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded avatar has an unsupported type");
			}
			previousKey = publicUrls.objectKeyFrom(user.getAvatarUrl());
			user.setAvatarUrl(publicUrls.of(key));
		}
		user.setUpdatedAt(Instant.now());
		users.save(user);
		if (previousKey != null && !previousKey.equals(request.avatarObjectKey().trim())) {
			deleteAfterCommit(previousKey);
		}
		return PublicUser.from(user);
	}

	private void assertOwnedKey(Integer userId, String key) {
		var matcher = OWNED_KEY.matcher(key);
		if (!matcher.matches() || !String.valueOf(userId).equals(matcher.group(1))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar object key is invalid");
		}
	}

	private String normalizeDisplayName(String raw) {
		String name = raw.trim();
		if (name.isEmpty() || name.length() > 30 || CONTROL.matcher(name).find()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name must be 1 to 30 characters without control characters");
		}
		return name;
	}

	private void deleteAfterCommit(String objectKey) {
		Runnable delete = () -> {
			try {
				storage.delete(objectKey);
			} catch (RuntimeException ignored) {
				// Best-effort cleanup; the new avatar URL is already durable.
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					delete.run();
				}
			});
		} else {
			delete.run();
		}
	}
}
