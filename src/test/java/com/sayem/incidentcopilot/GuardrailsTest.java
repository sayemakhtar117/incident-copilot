package com.sayem.incidentcopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sayem.incidentcopilot.guardrails.AnswerValidator;
import com.sayem.incidentcopilot.guardrails.Redactor;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardrailsTest {

	private final Redactor redactor = new Redactor();
	private final AnswerValidator validator = new AnswerValidator(new ObjectMapper());

	@Test
	void redactsEmailsIpsTokensAndSecrets() {
		String input = "user jane@example.com from 10.1.2.3 sent Bearer abcdefghij1234567890 with password=hunter2 "
				+ "and key AKIAABCDEFGHIJKLMNOP";
		Redactor.Result r = redactor.redact(input);
		assertFalse(r.text().contains("jane@example.com"));
		assertFalse(r.text().contains("10.1.2.3"));
		assertFalse(r.text().contains("abcdefghij1234567890"));
		assertFalse(r.text().contains("hunter2"));
		assertFalse(r.text().contains("AKIAABCDEFGHIJKLMNOP"));
		assertEquals(5, r.count());
	}

	@Test
	void leavesCleanTextUntouched() {
		Redactor.Result r = redactor.redact("HikariPool-1 Connection is not available");
		assertEquals("HikariPool-1 Connection is not available", r.text());
		assertEquals(0, r.count());
	}

	@Test
	void validatorDropsInventedCitationsAndClampsConfidence() {
		String raw = "Here you go: {\"rootCauseHypothesis\":\"pool leak\",\"confidence\":7,"
				+ "\"remediationSteps\":[\"rollback\"],\"citedIncidentIds\":[\"INC-001\",\"INC-999\"],"
				+ "\"evidence\":[\"pending=47\"]} thanks";
		AnswerValidator.Validated v = validator.validate(raw, Set.of("INC-001", "INC-002"));
		assertEquals(1.0, v.confidence());
		assertEquals(1, v.citedIds().size());
		assertTrue(v.citedIds().contains("INC-001"));
	}

	@Test
	void validatorRejectsMalformedAnswers() {
		assertThrows(IllegalArgumentException.class, () -> validator.validate("no json here", Set.of()));
		assertThrows(IllegalArgumentException.class, () -> validator.validate("{\"confidence\":0.5}", Set.of()));
		assertThrows(IllegalArgumentException.class,
				() -> validator.validate("{\"rootCauseHypothesis\":\"x\",\"remediationSteps\":[]}", Set.of()));
	}
}
