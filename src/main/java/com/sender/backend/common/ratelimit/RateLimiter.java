package com.sender.backend.common.ratelimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter shared by every API. State is in-memory and ephemeral:
 * losing it only resets the counters.
 */
@Component
public class RateLimiter {
	private final RateLimitProperties properties;
	private final Clock clock;
	private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

	@Autowired
	public RateLimiter(RateLimitProperties properties) {
		this(properties, Clock.systemUTC());
	}

	RateLimiter(RateLimitProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	/** Records a hit and throws {@link RateLimitExceededException} when the limit is exceeded. */
	public void check(RateLimitPolicy policy, String subject) {
		long retryAfterMillis = tryAcquire(policy, subject);
		if (retryAfterMillis > 0) {
			throw new RateLimitExceededException(Math.max(1, (retryAfterMillis + 999) / 1000));
		}
	}

	/** Records a hit and returns {@code true} when allowed, {@code false} when the limit is exceeded. */
	public boolean allow(RateLimitPolicy policy, String subject) {
		return tryAcquire(policy, subject) == 0;
	}

	/** @return 0 when allowed, otherwise the milliseconds until the oldest hit leaves the window. */
	private long tryAcquire(RateLimitPolicy policy, String subject) {
		long windowMillis = properties.window().toMillis();
		int maxHits = properties.maxHits().get(policy);
		long now = clock.millis();
		long cutoff = now - windowMillis;
		Deque<Long> times = hits.computeIfAbsent(policy.name() + ":" + subject, ignored -> new ArrayDeque<>());
		synchronized (times) {
			while (!times.isEmpty() && times.peekFirst() <= cutoff) {
				times.pollFirst();
			}
			if (times.size() >= maxHits) {
				return Math.max(1, times.peekFirst() + windowMillis - now);
			}
			times.addLast(now);
			return 0;
		}
	}

	@Scheduled(fixedDelay = 60_000)
	void evictExpired() {
		long cutoff = clock.millis() - properties.window().toMillis();
		hits.entrySet().removeIf(entry -> {
			Deque<Long> times = entry.getValue();
			synchronized (times) {
				return times.isEmpty() || times.peekLast() <= cutoff;
			}
		});
	}
}
