package com.sayem.incidentcopilot.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sayem.incidentcopilot.guardrails.Redactor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Java methods the LLM agent may call. Arguments are validated and clamped, and
 * every tool output is redacted before it is returned to the model.
 */
@Component
public class ToolRegistry {

	public record Tool(String name, String description, JsonNode parameters, Function<JsonNode, String> handler) {
	}

	private final Map<String, Tool> tools = new LinkedHashMap<>();
	private final ObjectMapper mapper;
	private final Redactor redactor;
	private final TelemetryProvider telemetry;

	public ToolRegistry(TelemetryProvider telemetry, ObjectMapper mapper, Redactor redactor) {
		this.telemetry = telemetry;
		this.mapper = mapper;
		this.redactor = redactor;

		register("getRecentDeployments",
				"Get the most recent deployments (version, change summary, minutes ago) of a service. "
						+ "Use it to check whether a release happened shortly before the incident.",
				"{\"type\":\"object\",\"properties\":{\"service\":{\"type\":\"string\"},"
						+ "\"limit\":{\"type\":\"integer\",\"description\":\"max results, default 3\"}},"
						+ "\"required\":[\"service\"]}",
				a -> withService(a, svc -> telemetry.recentDeployments(svc, clamp(a.path("limit").asInt(3), 1, 10))));

		register("fetchServiceMetrics",
				"Get current key metrics of a service (latency, error rate, CPU, memory, connection pool, queue lag) "
						+ "with a status of ok/warn/critical.",
				"{\"type\":\"object\",\"properties\":{\"service\":{\"type\":\"string\"}},\"required\":[\"service\"]}",
				a -> withService(a, telemetry::metrics));

		register("queryLogs",
				"Query recent log lines of a service. Filter by level (ERROR, WARN, INFO) and look-back window.",
				"{\"type\":\"object\",\"properties\":{\"service\":{\"type\":\"string\"},"
						+ "\"windowMinutes\":{\"type\":\"integer\",\"description\":\"look-back window, default 60, max 240\"},"
						+ "\"level\":{\"type\":\"string\",\"enum\":[\"ERROR\",\"WARN\",\"INFO\"]},"
						+ "\"limit\":{\"type\":\"integer\",\"description\":\"max lines, default 15, max 30\"}},"
						+ "\"required\":[\"service\"]}",
				a -> withService(a, svc -> telemetry.logs(svc, clamp(a.path("windowMinutes").asInt(60), 1, 240),
						a.path("level").asText(""), clamp(a.path("limit").asInt(15), 1, 30))));
	}

	public ArrayNode openAiSpec() {
		ArrayNode arr = mapper.createArrayNode();
		for (Tool t : tools.values()) {
			ObjectNode fn = mapper.createObjectNode();
			fn.put("name", t.name());
			fn.put("description", t.description());
			fn.set("parameters", t.parameters());
			ObjectNode wrapper = mapper.createObjectNode();
			wrapper.put("type", "function");
			wrapper.set("function", fn);
			arr.add(wrapper);
		}
		return arr;
	}

	/**
	 * Executes a tool by name. Never throws: errors are returned as JSON so the
	 * model can react to them.
	 */
	public String call(String name, JsonNode args) {
		Tool tool = tools.get(name);
		if (tool == null) {
			return json(Map.of("error", "unknown tool", "availableTools", tools.keySet()));
		}
		try {
			JsonNode safeArgs = args == null ? mapper.createObjectNode() : args;
			return redactor.redact(tool.handler().apply(safeArgs)).text();
		} catch (RuntimeException e) {
			return json(Map.of("error", String.valueOf(e.getMessage())));
		}
	}

	private String withService(JsonNode args, Function<String, Object> fn) {
		String svc = args.path("service").asText("");
		if (!telemetry.knownServices().contains(svc)) {
			return json(Map.of("error", "no telemetry for service", "knownServices", telemetry.knownServices()));
		}
		return json(fn.apply(svc));
	}

	private void register(String name, String description, String schemaJson, Function<JsonNode, String> handler) {
		try {
			tools.put(name, new Tool(name, description, mapper.readTree(schemaJson), handler));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Bad schema for tool " + name, e);
		}
	}

	private String json(Object o) {
		try {
			return mapper.writeValueAsString(o);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
