package com.sender.backend.common.exception;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice
public class SecurityExceptionHandler {
	@ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
	ResponseEntity<Map<String, Object>> badCredentials(Exception exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("code", "INVALID_CREDENTIALS", "message", exception.getMessage()));
	}

	@ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
	ResponseEntity<Map<String, Object>> validation(org.springframework.web.bind.MethodArgumentNotValidException exception) {
		Map<String, String> fields = new java.util.HashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", "Request validation failed", "fieldErrors", fields));
	}
}
