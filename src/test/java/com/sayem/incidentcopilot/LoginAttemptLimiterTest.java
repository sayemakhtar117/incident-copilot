package com.sayem.incidentcopilot;

import com.sayem.incidentcopilot.security.LoginAttemptLimiter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptLimiterTest {

	private final LoginAttemptLimiter limiter = new LoginAttemptLimiter();

	@Test
	void blocksAfterFiveFailedAttemptsFromAnAddress() {
		for (int i = 0; i < 5; i++) {
			limiter.recordFailure("192.0.2.1");
		}

		assertTrue(limiter.isBlocked("192.0.2.1"));
	}

	@Test
	void successfulLoginClearsFailuresForTheAddress() {
		for (int i = 0; i < 5; i++) {
			limiter.recordFailure("192.0.2.2");
		}

		limiter.recordSuccess("192.0.2.2");

		assertFalse(limiter.isBlocked("192.0.2.2"));
	}
}
