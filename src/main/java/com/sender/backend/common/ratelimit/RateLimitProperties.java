package com.sender.backend.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Duration window, Map<RateLimitPolicy, Integer> maxHits) {
	public RateLimitProperties {
		if (window == null || window.isNegative() || window.isZero()) {
			throw new IllegalArgumentException("app.rate-limit.window must be positive");
		}
		maxHits = maxHits == null ? Map.of() : Map.copyOf(maxHits);
		for (RateLimitPolicy policy : RateLimitPolicy.values()) {
			Integer value = maxHits.get(policy);
			if (value == null || value < 1) {
				throw new IllegalArgumentException(
						"app.rate-limit.max-hits." + policy.name().toLowerCase().replace('_', '-')
								+ " must be a positive integer");
			}
		}
	}
}
