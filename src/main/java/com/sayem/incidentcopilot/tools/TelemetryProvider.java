package com.sayem.incidentcopilot.tools;

import java.util.List;
import java.util.Set;

/**
 * Source of live-system evidence for the agent. The bundled implementation is
 * simulated; implement this interface against Prometheus, Loki/Elasticsearch
 * and your CI/CD API to make it real.
 */
public interface TelemetryProvider {

	record Deployment(String service, String version, int minutesAgo, String change, String author) {
	}

	record Metric(String service, String name, double value, String unit, String status) {
	}

	record LogLine(String service, String level, int minutesAgo, String message) {
	}

	Set<String> knownServices();

	List<Deployment> recentDeployments(String service, int limit);

	List<Metric> metrics(String service);

	List<LogLine> logs(String service, int windowMinutes, String level, int limit);
}
