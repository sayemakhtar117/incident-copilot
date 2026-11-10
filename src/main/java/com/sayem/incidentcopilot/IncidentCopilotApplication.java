package com.sayem.incidentcopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IncidentCopilotApplication {

	public static void main(String[] args) {
		SpringApplication.run(IncidentCopilotApplication.class, args);
	}
}
