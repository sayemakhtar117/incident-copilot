package com.sayem.incidentcopilot.model;

import java.util.List;

public record AnalysisResponse(String service, String rootCauseHypothesis, double confidence,
		List<String> remediationSteps, List<String> evidence, List<Source> sources, List<String> toolsUsed, Meta meta) {

	/**
	 * A retrieved past incident. {@code cited} is true when the model referenced it
	 * in its answer.
	 */
	public record Source(String id, String title, String service, double score, boolean cited) {
	}

	public record Meta(String llm, String embedding, String store, long latencyMs, int redactions, boolean cached,
			boolean fallbackUsed, int promptTokens, int completionTokens) {
	}

	public AnalysisResponse withMeta(Meta newMeta) {
		return new AnalysisResponse(service, rootCauseHypothesis, confidence, remediationSteps, evidence, sources,
				toolsUsed, newMeta);
	}
}
