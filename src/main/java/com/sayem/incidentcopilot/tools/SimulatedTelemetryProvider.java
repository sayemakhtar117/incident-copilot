package com.sayem.incidentcopilot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Serves canned telemetry from {@code seed/telemetry.json} so the agent has
 * something to investigate.
 */
@Component
public class SimulatedTelemetryProvider implements TelemetryProvider {

	record Data(List<Deployment> deployments, List<Metric> metrics, List<LogLine> logs) {
	}

	private final Data data;
	private final Set<String> services = new TreeSet<>();

	public SimulatedTelemetryProvider(ObjectMapper mapper) throws IOException {
		try (InputStream in = new ClassPathResource("seed/telemetry.json").getInputStream()) {
			this.data = mapper.readValue(in, Data.class);
		}
		data.deployments().forEach(d -> services.add(d.service()));
		data.metrics().forEach(m -> services.add(m.service()));
		data.logs().forEach(l -> services.add(l.service()));
	}

	@Override
	public Set<String> knownServices() {
		return services;
	}

	@Override
	public List<Deployment> recentDeployments(String service, int limit) {
		return data.deployments().stream().filter(d -> d.service().equals(service))
				.sorted(Comparator.comparingInt(Deployment::minutesAgo)).limit(limit).toList();
	}

	@Override
	public List<Metric> metrics(String service) {
		return data.metrics().stream().filter(m -> m.service().equals(service)).toList();
	}

	@Override
	public List<LogLine> logs(String service, int windowMinutes, String level, int limit) {
		return data.logs().stream().filter(l -> l.service().equals(service))
				.filter(l -> l.minutesAgo() <= windowMinutes)
				.filter(l -> level == null || level.isBlank() || l.level().equalsIgnoreCase(level))
				.sorted(Comparator.comparingInt(LogLine::minutesAgo)).limit(limit).toList();
	}
}
