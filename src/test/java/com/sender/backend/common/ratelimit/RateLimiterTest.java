package com.sender.backend.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {
	private final MutableClock clock = new MutableClock();

	@Test
	void blocksHitsOverTheLimitAndReportsRetryAfter() {
		RateLimiter limiter = limiter(2);
		limiter.check(RateLimitPolicy.MESSAGE_SEND, "user:1");
		limiter.check(RateLimitPolicy.MESSAGE_SEND, "user:1");

		RateLimitExceededException exception = assertThrows(RateLimitExceededException.class,
				() -> limiter.check(RateLimitPolicy.MESSAGE_SEND, "user:1"));

		assertEquals(60, exception.retryAfterSeconds());
	}

	@Test
	void allowsHitsAgainAfterTheWindowPasses() {
		RateLimiter limiter = limiter(1);
		assertTrue(limiter.allow(RateLimitPolicy.MESSAGE_SEND, "user:1"));
		assertFalse(limiter.allow(RateLimitPolicy.MESSAGE_SEND, "user:1"));

		clock.advance(Duration.ofSeconds(61));

		assertTrue(limiter.allow(RateLimitPolicy.MESSAGE_SEND, "user:1"));
	}

	@Test
	void countsPoliciesAndSubjectsIndependently() {
		RateLimiter limiter = limiter(1);
		assertTrue(limiter.allow(RateLimitPolicy.MESSAGE_SEND, "user:1"));

		assertTrue(limiter.allow(RateLimitPolicy.MESSAGE_SEARCH, "user:1"));
		assertTrue(limiter.allow(RateLimitPolicy.MESSAGE_SEND, "user:2"));
	}

	@Test
	void evictsIdleSubjects() {
		RateLimiter limiter = limiter(1);
		limiter.allow(RateLimitPolicy.MESSAGE_SEND, "user:1");
		clock.advance(Duration.ofMinutes(2));

		limiter.evictExpired();

		assertTrue(limiter.allow(RateLimitPolicy.MESSAGE_SEND, "user:1"));
	}

	@Test
	void rejectsConfigurationMissingAPolicy() {
		assertThrows(IllegalArgumentException.class,
				() -> new RateLimitProperties(Duration.ofMinutes(1), java.util.Map.of()));
	}

	private RateLimiter limiter(int maxHits) {
		return new RateLimiter(new RateLimitProperties(Duration.ofMinutes(1),
				Arrays.stream(RateLimitPolicy.values()).collect(Collectors.toMap(p -> p, p -> maxHits))), clock);
	}

	private static final class MutableClock extends Clock {
		private Instant now = Instant.parse("2026-01-01T00:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(java.time.ZoneId zone) { return this; }
		@Override public Instant instant() { return now; }
	}
}
