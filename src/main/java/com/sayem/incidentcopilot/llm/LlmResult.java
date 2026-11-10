package com.sayem.incidentcopilot.llm;

import java.util.List;

public record LlmResult(String content, List<String> toolsUsed, int promptTokens, int completionTokens) {
}
