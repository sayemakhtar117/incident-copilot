package com.sayem.incidentcopilot.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.sayem.incidentcopilot.config.AuthProperties;
import com.sayem.incidentcopilot.security.LoginAttemptLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);

	private final AuthProperties properties;
	private final PasswordEncoder passwordEncoder;
	private final JwtEncoder jwtEncoder;
	private final LoginAttemptLimiter loginAttemptLimiter;

	public AuthController(AuthProperties properties, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
			LoginAttemptLimiter loginAttemptLimiter) {
		this.properties = properties;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
		this.loginAttemptLimiter = loginAttemptLimiter;
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
		if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
			return ResponseEntity.badRequest().body(Map.of("error", "Password must be at most 72 UTF-8 bytes."));
		}

		String clientAddress = Objects.toString(servletRequest.getRemoteAddr(), "unknown");
		if (loginAttemptLimiter.isBlocked(clientAddress)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
					.body(Map.of("error", "Too many failed login attempts. Try again in a minute."));
		}

		String submittedEmail = request.email().strip().toLowerCase(Locale.ROOT);
		String adminEmail = properties.adminEmail().strip().toLowerCase(Locale.ROOT);
		boolean emailMatches = MessageDigest.isEqual(
				submittedEmail.getBytes(StandardCharsets.UTF_8),
				adminEmail.getBytes(StandardCharsets.UTF_8));
		boolean passwordMatches = passwordEncoder.matches(request.password(), properties.adminPasswordHash());
		if (!emailMatches || !passwordMatches) {
			loginAttemptLimiter.recordFailure(clientAddress);
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("error", "Invalid email or password."));
		}

		loginAttemptLimiter.recordSuccess(clientAddress);
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plus(TOKEN_LIFETIME);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("incident-copilot")
				.subject(adminEmail)
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.claim("role", "ADMIN")
				.build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(new LoginResponse(token, TOKEN_LIFETIME.toSeconds()));
	}

	public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(min = 12, max = 72) String password) {
	}

	public record LoginResponse(String accessToken, long expiresInSeconds) {
	}
}
