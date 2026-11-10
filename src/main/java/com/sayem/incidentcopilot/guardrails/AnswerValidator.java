package com.sayem.incidentcopilot.guardrails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates the model's JSON answer: required fields present, confidence
 * clamped to [0,1], and every cited incident id must be one that was actually
 * retrieved (so the model cannot invent sources).
 */
@Component
public class AnswerValidator {

	public record Validated(String hypothesis, double confidence, List<String> steps, List<String> citedIds,
			List<String> evidence) {
	}

	private final ObjectMapper mapper;

	public AnswerValidator(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public Validated validate(String raw, Set<String> retrievedIds) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("Empty model answer");
		}
		int start = raw.indexOf('{');
		int end = raw.lastIndexOf('}');
		if (start < 0 || end <= start) {
			throw new IllegalArgumentException("Model answer contained no JSON object");
		}
		JsonNode root;
		try {
			root = mapper.readTree(raw.substring(start, end + 1));
		} catch (Exception e) {
			throw new IllegalArgumentException("Model answer was not valid JSON: " + e.getMessage(), e);
		}

		String hypothesis = root.path("rootCauseHypothesis").asText("").trim();
		if (hypothesis.isEmpty()) {
			throw new IllegalArgumentException("Missing rootCauseHypothesis");
		}
		List<String> steps = strings(root.path("remediationSteps"), 8, 400);
		if (steps.isEmpty()) {
			throw new IllegalArgumentException("Missing remediationSteps");
		}
		double confidence = root.path("confidence").isNumber() ? root.path("confidence").asDouble() : 0.3;
		confidence = Math.max(0.0, Math.min(1.0, confidence));

		List<String> cited = new ArrayList<>();
		for (String id : strings(root.path("citedIncidentIds"), 10, 40)) {
			if (retrievedIds.contains(id)) {
				cited.add(id);
			}
		}
		List<String> evidence = strings(root.path("evidence"), 8, 400);
		return new Validated(truncate(hypothesis, 1000), confidence, steps, cited, evidence);
	}

	private static List<String> strings(JsonNode arr, int maxItems, int maxLen) {
		List<String> out = new ArrayList<>();
		if (arr.isArray()) {
			for (JsonNode n : arr) {
				String s = n.asText("").trim();
				if (!s.isEmpty() && out.size() < maxItems) {
					out.add(truncate(s, maxLen));
				}
			}
		}
		return out;
	}

	private static String truncate(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max);
	}
}
