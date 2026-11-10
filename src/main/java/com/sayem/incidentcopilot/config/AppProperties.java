package com.sayem.incidentcopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed application configuration for the LLM, embedding, vector-store, and
 * retrieval settings.
 */
@ConfigurationProperties(prefix = "copilot")
public record AppProperties(@DefaultValue Llm llm, @DefaultValue Embedding embedding, @DefaultValue Store store,
		@DefaultValue Rag rag) {

	/** Settings for the primary OpenAI-compatible chat model. */
	public record Llm(@DefaultValue("https://api.groq.com/openai/v1") String baseUrl, @DefaultValue("") String apiKey,
			@DefaultValue("llama-3.3-70b-versatile") String model, @DefaultValue("30") int timeoutSeconds,
			@DefaultValue("4") int maxToolIterations) {
		public boolean enabled() {
			return apiKey != null && !apiKey.isBlank();
		}
	}

	/** Settings for the OpenAI-compatible embedding service. */
	public record Embedding(@DefaultValue("https://generativelanguage.googleapis.com/v1beta/openai") String baseUrl,
			@DefaultValue("") String apiKey, @DefaultValue("gemini-embedding-001") String model,
			@DefaultValue("768") int dimensions) {
		public boolean enabled() {
			return apiKey != null && !apiKey.isBlank();
		}
	}

	/** Selects the vector-store implementation and controls whether MongoDB persistence is enabled. */
	public record Store(@DefaultValue("memory") String type, @DefaultValue("incidents") String collection,
			@DefaultValue("incident_vector_index") String indexName,
			@DefaultValue("false") boolean persistenceEnabled) {
	}

	/**
	 * Controls the number of retrieved incidents and the minimum similarity score.
	 */
	public record Rag(@DefaultValue("4") int topK, @DefaultValue("0.05") double minScore) {
	}
}
