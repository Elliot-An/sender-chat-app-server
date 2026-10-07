package com.sender.backend.auth;

import com.sender.backend.auth.AuthDtos.AuthResponse;
import com.sender.backend.auth.AuthDtos.LoginRequest;
import com.sender.backend.auth.AuthDtos.PublicUser;
import com.sender.backend.auth.AuthDtos.RegisterRequest;
import com.sender.backend.auth.AuthDtos.LogoutRequest;
import com.sender.backend.auth.AuthDtos.ChangePasswordRequest;
import com.sender.backend.common.ratelimit.RateLimitPolicy;
import com.sender.backend.common.ratelimit.RateLimited;
import com.sender.backend.user.UserRepository;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Register and manage authenticated sessions")
public class AuthController {
	private static final String REFRESH_COOKIE = "sender_refresh";
	private final AuthService authService;
	private final UserRepository users;
	private final long refreshTokenDays;

	public AuthController(AuthService authService, UserRepository users,
			@Value("${app.auth.refresh-token-days}") long refreshTokenDays) {
		this.authService = authService;
		this.users = users;
		this.refreshTokenDays = refreshTokenDays;
	}

	@PostMapping("/register")
	@RateLimited(RateLimitPolicy.AUTH_CREDENTIALS)
	@Operation(summary = "Register a user", description = "Creates an account and starts an authenticated session.")
	@ApiResponse(responseCode = "200", description = "User registered")
	@ApiResponse(responseCode = "400", description = "Invalid request")
	@ApiResponse(responseCode = "401", description = "Registration rejected")
	@ApiResponse(responseCode = "429", description = "Too many requests")
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
			HttpServletResponse response) {
		return sessionResponse(authService.register(request), response);
	}

	@PostMapping("/login")
	@RateLimited(RateLimitPolicy.AUTH_CREDENTIALS)
	@Operation(summary = "Log in", description = "Authenticates by email and starts an authenticated session.")
	@ApiResponse(responseCode = "200", description = "Login successful")
	@ApiResponse(responseCode = "400", description = "Invalid request")
	@ApiResponse(responseCode = "401", description = "Invalid credentials")
	@ApiResponse(responseCode = "429", description = "Too many requests")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
			HttpServletResponse response) {
		return sessionResponse(authService.login(request), response);
	}

	@PostMapping("/refresh")
	@RateLimited(RateLimitPolicy.AUTH_SESSION)
	@Operation(summary = "Refresh access token", description = "Rotates the refresh cookie and returns a new access token.")
	@ApiResponse(responseCode = "200", description = "Token refreshed")
	@ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
	@ApiResponse(responseCode = "429", description = "Too many requests")
	public ResponseEntity<AuthResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
			HttpServletResponse response) {
		return sessionResponse(authService.refresh(token), response);
	}

	@PostMapping("/logout")
	@RateLimited(RateLimitPolicy.AUTH_SESSION)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Log out", description = "Revokes the current refresh session.")
	@ApiResponse(responseCode = "204", description = "Logged out")
	@ApiResponse(responseCode = "429", description = "Too many requests")
	public void logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
			@RequestBody(required = false) LogoutRequest request,
			HttpServletResponse response) {
		authService.logout(token, request != null && request.allDevices());
		clearCookie(response);
	}

	@GetMapping("/me")
	@Operation(summary = "Get current user")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponse(responseCode = "200", description = "Current user returned")
	@ApiResponse(responseCode = "401", description = "Missing or invalid access token")
	public PublicUser me(@AuthenticationPrincipal Jwt jwt) {
		return PublicUser.from(users.findById(Integer.valueOf(jwt.getSubject()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
	}

	@PostMapping("/change-password")
	@RateLimited(RateLimitPolicy.PASSWORD_CHANGE)
	@Operation(summary = "Change password", description = "Changes the authenticated user's password and revokes all refresh sessions.")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponse(responseCode = "204", description = "Password changed")
	@ApiResponse(responseCode = "401", description = "Invalid current password or access token")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "429", description = "Too many requests")
	public void changePassword(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody ChangePasswordRequest request) {
		authService.changePassword(Integer.valueOf(jwt.getSubject()), request);
	}

	private ResponseEntity<AuthResponse> sessionResponse(AuthService.SessionResult session, HttpServletResponse response) {
		ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, session.refreshToken())
				.httpOnly(true).secure(false).sameSite("Lax")
				.path("/api/v1/auth").maxAge(java.time.Duration.ofDays(refreshTokenDays)).build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
		return ResponseEntity.ok(new AuthResponse(session.user(), session.accessToken().value(), session.accessToken().expiresAt()));
	}

	private void clearCookie(HttpServletResponse response) {
		ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true).path("/api/v1/auth").maxAge(0).build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}
}
