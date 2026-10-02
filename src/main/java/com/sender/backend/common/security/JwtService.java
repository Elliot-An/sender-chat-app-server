package com.sender.backend.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sender.backend.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {
	private final JwtEncoder encoder;
	private final long accessTokenMinutes;

	public JwtService(@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.access-token-minutes}") long accessTokenMinutes) {
		if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
		}
		SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		this.accessTokenMinutes = accessTokenMinutes;
	}

	public Token issue(User user) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plusSeconds(accessTokenMinutes * 60);
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(user.getId().toString())
				.claim("username", user.getUsername())
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.id(UUID.randomUUID().toString())
				.build();
		String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new Token(value, expiresAt);
	}

	public record Token(String value, Instant expiresAt) {}
}
