package com.sender.backend.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3ObjectStoragePresignTest {

	private S3Client s3;
	private S3Presigner presigner;
	private S3ObjectStorage storage;

	@BeforeEach
	void setUp() {
		var credentials = StaticCredentialsProvider.create(
				AwsBasicCredentials.create("AKIATEST", "secretsecretsecretsecretsecret1"));
		s3 = S3Client.builder()
				.region(Region.AP_SOUTHEAST_1)
				.credentialsProvider(credentials)
				.build();
		presigner = S3Presigner.builder()
				.region(Region.AP_SOUTHEAST_1)
				.credentialsProvider(credentials)
				.build();
		storage = new S3ObjectStorage(s3, presigner, "test-bucket");
	}

	@AfterEach
	void tearDown() {
		presigner.close();
		s3.close();
	}

	@Test
	void presignPutSignsContentTypeButNotContentLength() {
		ObjectStorage.PresignedUpload upload = storage.presignPut(
				"message-attachments/1/1/x.pdf",
				"application/pdf",
				12_000L,
				Duration.ofMinutes(5));

		String query = upload.putUrl().getRawQuery();
		String signedHeaders = Arrays.stream(query.split("&"))
				.map(part -> URLDecoder.decode(part, StandardCharsets.UTF_8))
				.filter(part -> part.startsWith("X-Amz-SignedHeaders="))
				.map(part -> part.substring("X-Amz-SignedHeaders=".length()))
				.findFirst()
				.orElseThrow();

		assertTrue(signedHeaders.contains("content-type"));
		assertFalse(signedHeaders.contains("content-length"),
				"Signed Content-Length breaks browser PUT uploads");
	}
}
