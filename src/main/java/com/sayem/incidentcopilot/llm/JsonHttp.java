package com.sayem.incidentcopilot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Minimal JSON-over-HTTPS client used for OpenAI-compatible endpoints. */
@Component
public class JsonHttp {

	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final ObjectMapper mapper;

	public JsonHttp(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public ObjectMapper mapper() {
		return mapper;
	}

	public JsonNode post(String url, String apiKey, JsonNode body, Duration timeout) throws IOException {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
					.header("Content-Type", "application/json").header("Authorization", "Bearer " + apiKey)
					.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2) {
				String b = response.body() == null ? "" : response.body();
				throw new IOException("HTTP " + response.statusCode() + " from " + url + ": "
						+ (b.length() > 300 ? b.substring(0, 300) : b));
			}
			return mapper.readTree(response.body());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while calling " + url, e);
		}
	}
}
