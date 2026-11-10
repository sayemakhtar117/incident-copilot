package com.sayem.incidentcopilot.model;

/**
 * A resolved historical incident: the knowledge base the copilot retrieves
 * from.
 */
public record Incident(String id, String title, String service, String severity, String symptoms, String rootCause,
		String resolution, String occurredOn) {

	/**
	 * Text that gets embedded. Symptoms and root cause carry most of the signal.
	 */
	public String embeddingText() {
		return title + ". " + service + ". " + symptoms + ". " + rootCause;
	}
}
