package com.sender.backend.common.ratelimit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class RateLimitExceptionHandler {
	@ExceptionHandler(RateLimitExceededException.class)
	ResponseEntity<Map<String, Object>> rateLimited(RateLimitExceededException exception) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
				.body(Map.of("code", "RATE_LIMITED", "message", exception.getMessage()));
	}
}
