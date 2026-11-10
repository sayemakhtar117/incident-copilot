package com.sayem.incidentcopilot.llm;

import com.sayem.incidentcopilot.tools.ToolRegistry;

public interface LlmClient {

	/**
	 * Runs the analysis (possibly calling tools) and returns the raw JSON answer
	 * text.
	 */
	LlmResult analyze(AnalysisContext context, ToolRegistry tools);

	String name();
}
