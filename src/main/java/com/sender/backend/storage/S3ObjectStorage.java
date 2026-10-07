package com.sender.backend.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;

@Component
public class S3ObjectStorage implements ObjectStorage {
	private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

	private final S3Client s3;
	private final S3Presigner presigner;
	private final String bucket;

	public S3ObjectStorage(S3Client s3, S3Presigner presigner, @Value("${app.s3.bucket}") String bucket) {
		this.s3 = s3;
		this.presigner = presigner;
		this.bucket = bucket;
	}

	@Override
	public PresignedUpload presignPut(String objectKey, String contentType, long contentLength, Duration ttl) {
		PutObjectRequest put = PutObjectRequest.builder()
				.bucket(bucket)
				.key(objectKey)
				.contentType(contentType)
				.contentLength(contentLength)
				.build();
		PresignedPutObjectRequest presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
				.signatureDuration(ttl)
				.putObjectRequest(put)
				.build());
		try {
			return new PresignedUpload(presigned.url().toURI(), presigned.expiration());
		} catch (URISyntaxException exception) {
			throw new IllegalStateException("Presigned upload URL was not valid", exception);
		}
	}

	@Override
	public Optional<StoredObject> head(String objectKey) {
		try {
			HeadObjectResponse response = s3.headObject(HeadObjectRequest.builder()
					.bucket(bucket)
					.key(objectKey)
					.build());
			long length = response.contentLength() == null ? 0L : response.contentLength();
			return Optional.of(new StoredObject(objectKey, response.contentType(), length));
		} catch (NoSuchKeyException notFound) {
			return Optional.empty();
		} catch (S3Exception exception) {
			if (exception.statusCode() == 404) {
				return Optional.empty();
			}
			log.warn("Could not inspect object {}", objectKey);
			return Optional.empty();
		} catch (RuntimeException exception) {
			log.warn("Could not inspect object {}", objectKey);
			return Optional.empty();
		}
	}

	@Override
	public void delete(String objectKey) {
		s3.deleteObject(DeleteObjectRequest.builder()
				.bucket(bucket)
				.key(objectKey)
				.build());
	}
}
