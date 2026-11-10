package com.sayem.incidentcopilot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Configures API CORS for the exact comma-separated origins supplied by
 * application settings.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	private final String[] origins;

	public CorsConfig(@Value("${copilot.cors.allowed-origins:}") String allowedOrigins) {
		this.origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(origin -> !origin.isEmpty())
				.toArray(String[]::new);
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		if (origins.length == 0) {
			return;
		}
		registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET", "POST", "OPTIONS")
				.allowedHeaders("*").maxAge(3600);
	}
}
