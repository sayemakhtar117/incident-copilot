package com.sayem.incidentcopilot.llm;

import com.sayem.incidentcopilot.model.ScoredIncident;

import java.util.List;

/**
 * Everything the analyst gets: the redacted alert, the target service and the
 * retrieved similar incidents.
 */
public record AnalysisContext(String alert, String service, List<ScoredIncident> similar) {
}
