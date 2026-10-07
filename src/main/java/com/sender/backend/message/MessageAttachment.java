package com.sender.backend.message;

import java.util.UUID;

/** Write-once attachment value stored in {@code messages.attachments} JSONB. */
public record MessageAttachment(
		UUID id,
		String objectKey,
		String originalFilename,
		String contentType,
		long sizeBytes,
		int sortOrder) {}
