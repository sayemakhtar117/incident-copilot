package com.sayem.incidentcopilot.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sayem.incidentcopilot.config.AppProperties;
import com.sayem.incidentcopilot.llm.JsonHttp;
import com.sayem.incidentcopilot.store.Vectors;

import java.io.IOException;
import java.time.Duration;

/**
 * Calls any OpenAI-compatible {@code /embeddings} endpoint (Gemini, OpenAI,
 * Mistral, Ollama, ...).
 */
public class OpenAiCompatEmbeddingService implements EmbeddingService {

	private final JsonHttp http;
	private final AppProperties.Embedding cfg;
	private final String url;

	public OpenAiCompatEmbeddingService(JsonHttp http, AppProperties.Embedding cfg) {
		this.http = http;
		this.cfg = cfg;
		String base = cfg.baseUrl().endsWith("/") ? cfg.baseUrl().substring(0, cfg.baseUrl().length() - 1)
				: cfg.baseUrl();
		this.url = base + "/embeddings";
	}

	@Override
	public float[] embed(String text) {
		ObjectNode body = http.mapper().createObjectNode();
		body.put("model", cfg.model());
		body.put("input", text);
		if (cfg.dimensions() > 0) {
			body.put("dimensions", cfg.dimensions());
		}
		try {
			JsonNode res = http.post(url, cfg.apiKey(), body, Duration.ofSeconds(20));
			JsonNode arr = res.path("data").path(0).path("embedding");
			if (!arr.isArray() || arr.isEmpty()) {
				throw new IllegalStateException("Embedding response had no vector");
			}
			float[] v = new float[arr.size()];
			for (int i = 0; i < v.length; i++) {
				v[i] = (float) arr.get(i).asDouble();
			}
			return Vectors.normalize(v);
		} catch (IOException e) {
			throw new IllegalStateException("Embedding call failed: " + e.getMessage(), e);
		}
	}

	@Override
	public int dimensions() {
		return cfg.dimensions();
	}

	@Override
	public String name() {
		return cfg.model();
	}
}
