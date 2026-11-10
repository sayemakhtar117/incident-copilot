package com.sayem.incidentcopilot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sayem.incidentcopilot.model.Incident;
import com.sayem.incidentcopilot.model.ScoredIncident;
import com.sayem.incidentcopilot.tools.ToolRegistry;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic offline analyst. It calls the same tools as the LLM agent, then
 * builds the answer from the best retrieved incident plus the live evidence.
 * Used when no LLM key is configured and as the fallback when the LLM is slow,
 * failing or the circuit breaker is open.
 */
@Component
public class RuleBasedLlmClient implements LlmClient {

	private final ObjectMapper mapper;

	public RuleBasedLlmClient(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public LlmResult analyze(AnalysisContext ctx, ToolRegistry tools) {
		List<String> used = new ArrayList<>();
		List<String> evidence = new ArrayList<>();
		int latestDeployMinutes = Integer.MAX_VALUE;
		String latestDeployVersion = null;

		ObjectNode svcArgs = mapper.createObjectNode().put("service", ctx.service());

		JsonNode deployments = parse(tools.call("getRecentDeployments", svcArgs));
		used.add("getRecentDeployments");
		if (deployments.isArray()) {
			for (JsonNode d : deployments) {
				int ago = d.path("minutesAgo").asInt();
				evidence.add(
						"Deployment " + d.path("version").asText() + " " + ago + "m ago: " + d.path("change").asText());
				if (ago < latestDeployMinutes) {
					latestDeployMinutes = ago;
					latestDeployVersion = d.path("version").asText();
				}
			}
		}

		JsonNode metrics = parse(tools.call("fetchServiceMetrics", svcArgs));
		used.add("fetchServiceMetrics");
		if (metrics.isArray()) {
			for (JsonNode m : metrics) {
				String status = m.path("status").asText("ok");
				if (!"ok".equals(status)) {
					evidence.add("Metric " + m.path("name").asText() + "=" + m.path("value").asText()
							+ m.path("unit").asText() + " (" + status + ")");
				}
			}
		}

		ObjectNode logArgs = mapper.createObjectNode().put("service", ctx.service()).put("level", "ERROR").put("limit",
				3);
		JsonNode logs = parse(tools.call("queryLogs", logArgs));
		used.add("queryLogs");
		if (logs.isArray()) {
			for (JsonNode l : logs) {
				evidence.add("Log [" + l.path("level").asText() + "] " + l.path("message").asText() + " ("
						+ l.path("minutesAgo").asInt() + "m ago)");
			}
		}

		ObjectNode answer = mapper.createObjectNode();
		ArrayNode steps = mapper.createArrayNode();
		ArrayNode cited = mapper.createArrayNode();

		if (ctx.similar().isEmpty()) {
			answer.put("rootCauseHypothesis", "No similar past incident found. Start from the live evidence below.");
			answer.put("confidence", 0.15);
			steps.add("Check the most recent deployment and consider a rollback if it correlates with the alert.");
			steps.add("Inspect the ERROR logs and saturated metrics listed in the evidence.");
			steps.add("Escalate to the owning team and record the outcome as a new incident for future retrieval.");
		} else {
			ScoredIncident top = ctx.similar().get(0);
			Incident inc = top.incident();
			String hypothesis = "Likely recurrence of \"" + inc.title() + "\" (" + inc.id() + "): " + inc.rootCause();
			if (latestDeployMinutes <= 90 && latestDeployVersion != null) {
				hypothesis += " Deployment " + latestDeployVersion + " went out " + latestDeployMinutes
						+ " minutes earlier, so check it first.";
			}
			answer.put("rootCauseHypothesis", hypothesis);
			answer.put("confidence", Math.round(Math.min(0.9, Math.max(0.2, top.score() * 1.2)) * 100) / 100.0);
			for (String s : inc.resolution().split("(?<=\\.)\\s+")) {
				if (!s.isBlank()) {
					steps.add(s.trim());
				}
			}
			cited.add(inc.id());
		}
		answer.set("remediationSteps", steps);
		answer.set("citedIncidentIds", cited);
		ArrayNode ev = mapper.createArrayNode();
		evidence.forEach(ev::add);
		answer.set("evidence", ev);

		return new LlmResult(answer.toString(), used, 0, 0);
	}

	@Override
	public String name() {
		return "rule-based-offline";
	}

	private JsonNode parse(String json) {
		try {
			return mapper.readTree(json);
		} catch (IOException e) {
			return mapper.createObjectNode();
		}
	}
}
