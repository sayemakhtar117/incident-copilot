package com.sayem.incidentcopilot.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Single-admin credentials and token signing configuration.
 */
@Validated
@ConfigurationProperties(prefix = "copilot.auth")
public record AuthProperties(@NotBlank @Email String adminEmail,
		@NotBlank @Pattern(regexp = "^\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}$") String adminPasswordHash,
		@NotBlank String jwtSecretBase64) {
}
