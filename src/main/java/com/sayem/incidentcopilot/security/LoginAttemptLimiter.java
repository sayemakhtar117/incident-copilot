package com.sayem.incidentcopilot.security;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Component
public class LoginAttemptLimiter {

	private static final int MAX_FAILURES = 5;
	private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();

	private final Cache<String, AttemptWindow> attempts = Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterAccess(2, TimeUnit.MINUTES)
			.build();

	public boolean isBlocked(String clientAddress) {
		AttemptWindow window = attempts.get(clientAddress, ignored -> new AttemptWindow());
		synchronized (window) {
			resetExpiredWindow(window);
			return window.failures >= MAX_FAILURES;
		}
	}

	public void recordFailure(String clientAddress) {
		AttemptWindow window = attempts.get(clientAddress, ignored -> new AttemptWindow());
		synchronized (window) {
			resetExpiredWindow(window);
			window.failures++;
		}
	}

	public void recordSuccess(String clientAddress) {
		attempts.invalidate(clientAddress);
	}

	private static void resetExpiredWindow(AttemptWindow window) {
		long now = System.currentTimeMillis();
		if (now - window.startedAtMillis >= WINDOW_MILLIS) {
			window.startedAtMillis = now;
			window.failures = 0;
		}
	}

	private static final class AttemptWindow {
		private long startedAtMillis = System.currentTimeMillis();
		private int failures;
	}
}
