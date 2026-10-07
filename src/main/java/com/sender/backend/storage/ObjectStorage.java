package com.sender.backend.storage;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * AWS S3 object storage seam shared by features (avatars, group avatars, message attachments).
 * Callers own object-key layout, content-type rules, and size limits.
 */
public interface ObjectStorage {
	PresignedUpload presignPut(String objectKey, String contentType, long contentLength, Duration ttl);

	PresignedDownload presignGet(String objectKey, Duration ttl);

	Optional<StoredObject> head(String objectKey);

	void delete(String objectKey);

	record PresignedUpload(URI putUrl, Instant expiresAt) {}

	record PresignedDownload(URI getUrl, Instant expiresAt) {}

	record StoredObject(String objectKey, String contentType, long contentLength) {}
}
