package com.sender.backend.common.ratelimit;

public class RateLimitExceededException extends RuntimeException {
	private final long retryAfterSeconds;

	public RateLimitExceededException(long retryAfterSeconds) {
		super("Too many requests. Try again shortly.");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
