package com.sayem.incidentcopilot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Loads the sample knowledge base on startup. A failure (e.g. embedding API
 * down) must not stop the app.
 */
@Component
public class SeedRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

	private final IncidentService incidents;

	public SeedRunner(IncidentService incidents) {
		this.incidents = incidents;
	}

	@Override
	public void run(ApplicationArguments args) {
		try {
			incidents.seedIfEmpty();
		} catch (RuntimeException e) {
			log.warn("Seeding skipped: {}. Call POST /api/seed once the embedding/store backend is reachable.",
					e.toString());
		}
	}
}
