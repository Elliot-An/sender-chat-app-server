package com.sender.backend.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Enforces {@link RateLimited} on controller methods. The subject is never taken from request input. */
public class RateLimitInterceptor implements HandlerInterceptor {
	private final RateLimiter limiter;

	public RateLimitInterceptor(RateLimiter limiter) {
		this.limiter = limiter;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod method)) {
			return true;
		}
		RateLimited annotation = method.getMethodAnnotation(RateLimited.class);
		if (annotation == null) {
			return true;
		}
		RateLimitPolicy policy = annotation.value();
		limiter.check(policy, subject(policy, request));
		return true;
	}

	private String subject(RateLimitPolicy policy, HttpServletRequest request) {
		if (policy.scope() == RateLimitPolicy.Scope.USER) {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication != null && authentication.isAuthenticated()
					&& !(authentication instanceof AnonymousAuthenticationToken)) {
				return "user:" + authentication.getName();
			}
		}
		return "ip:" + request.getRemoteAddr();
	}
}
