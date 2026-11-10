package com.sayem.incidentcopilot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sayem.incidentcopilot.embedding.EmbeddingService;
import com.sayem.incidentcopilot.guardrails.Redactor;
import com.sayem.incidentcopilot.model.Incident;
import com.sayem.incidentcopilot.model.NewIncidentRequest;
import com.sayem.incidentcopilot.store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Manages incident records, including redaction, embedding, persistence, and
 * sample-data seeding.
 */
@Service
public class IncidentService {

	private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

	private final VectorStore store;
	private final EmbeddingService embeddings;
	private final Redactor redactor;
	private final ObjectMapper mapper;

	public IncidentService(VectorStore store, EmbeddingService embeddings, Redactor redactor, ObjectMapper mapper) {
		this.store = store;
		this.embeddings = embeddings;
		this.redactor = redactor;
		this.mapper = mapper;
	}

	/**
	 * Redacts incident text, generates its embedding, and stores the incident and
	 * vector together.
	 *
	 * @param req validated incident fields
	 * @return the stored incident
	 */
	public Incident add(NewIncidentRequest req) {
		Incident incident = new Incident("INC-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
				redactor.redact(req.title()).text(), req.service().trim(),
				req.severity() == null || req.severity().isBlank() ? "SEV3" : req.severity().trim(),
				redactor.redact(req.symptoms()).text(), redactor.redact(req.rootCause()).text(),
				redactor.redact(req.resolution()).text(), LocalDate.now().toString());
		store.upsert(incident, embeddings.embed(incident.embeddingText()));
		return incident;
	}

	/**
	 * Returns all incidents from the configured vector store.
	 *
	 * @return stored incidents
	 */
	public List<Incident> list() {
		return store.all();
	}

	/**
	 * Loads the bundled knowledge base if the store is empty. Returns how many
	 * incidents were loaded.
	 */
	public int seedIfEmpty() {
		if (store.count() > 0) {
			return 0;
		}
		return seed();
	}

	/**
	 * Loads every bundled sample incident, embeds it, and upserts it into the
	 * configured store.
	 *
	 * @return the number of bundled incidents processed
	 * @throws IllegalStateException if the sample data cannot be read
	 */
	public int seed() {
		try (InputStream in = new ClassPathResource("seed/incidents.json").getInputStream()) {
			List<Incident> incidents = mapper.readValue(in, new TypeReference<>() {
			});
			for (Incident i : incidents) {
				store.upsert(i, embeddings.embed(i.embeddingText()));
			}
			log.info("Seeded {} incidents into {}", incidents.size(), store.name());
			return incidents.size();
		} catch (IOException e) {
			throw new IllegalStateException("Could not read seed incidents", e);
		}
	}
}
