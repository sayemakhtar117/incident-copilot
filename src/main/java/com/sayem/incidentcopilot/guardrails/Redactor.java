package com.sayem.incidentcopilot.guardrails;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strips secrets and PII before any text leaves the process towards an LLM or
 * embedding API.
 */
@Component
public class Redactor {

	public record Result(String text, int count) {
	}

	private record Rule(Pattern pattern, String replacement) {
	}

	private static final List<Rule> RULES = List.of(
			new Rule(Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"), "[REDACTED_EMAIL]"),
			new Rule(Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"),
					"[REDACTED_JWT]"),
			new Rule(Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{10,}"), "Bearer [REDACTED_TOKEN]"),
			new Rule(Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"), "[REDACTED_AWS_KEY]"),
			new Rule(Pattern.compile("(?i)\\b(password|passwd|pwd|secret|token|api[_-]?key)\\s*[=:]\\s*[^\\s,;\"']+"),
					"$1=[REDACTED]"),
			new Rule(Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"), "[REDACTED_IP]"));

	public Result redact(String input) {
		if (input == null || input.isBlank()) {
			return new Result("", 0);
		}
		String out = input;
		int total = 0;
		for (Rule rule : RULES) {
			Matcher m = rule.pattern().matcher(out);
			int c = 0;
			while (m.find()) {
				c++;
			}
			if (c > 0) {
				out = rule.pattern().matcher(out).replaceAll(rule.replacement());
				total += c;
			}
		}
		return new Result(out, total);
	}
}
