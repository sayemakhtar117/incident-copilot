package com.sayem.incidentcopilot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides descriptive metadata for the generated OpenAPI specification. */
@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI incidentCopilotOpenApi() {
		return new OpenAPI().info(new Info().title("Incident Copilot API")
				.description("API for incident analysis, knowledge-base management, and retrieval evaluation.")
				.version("1.0.0"));
	}
}
