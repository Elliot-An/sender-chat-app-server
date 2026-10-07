	package com.sender.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds and parses public object URLs from {@code app.s3.public-base-url}.
 * Features store the resulting URL; clients never supply raw object URLs.
 */
@Component
public class ObjectPublicUrl {
	private final String baseUrl;

	public ObjectPublicUrl(@Value("${app.s3.public-base-url}") String publicBaseUrl) {
		this.baseUrl = trimTrailingSlash(publicBaseUrl);
	}

	public String of(String objectKey) {
		return baseUrl + "/" + objectKey;
	}

	/** @return object key when {@code publicUrl} was built from this base, otherwise {@code null} */
	public String objectKeyFrom(String publicUrl) {
		if (publicUrl == null || publicUrl.isBlank()) {
			return null;
		}
		String prefix = baseUrl + "/";
		if (!publicUrl.startsWith(prefix)) {
			return null;
		}
		return publicUrl.substring(prefix.length());
	}

	private static String trimTrailingSlash(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
