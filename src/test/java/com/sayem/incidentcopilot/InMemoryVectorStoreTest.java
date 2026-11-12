package com.sayem.incidentcopilot;

import com.sayem.incidentcopilot.model.Incident;
import com.sayem.incidentcopilot.store.InMemoryVectorStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryVectorStoreTest {

	private final InMemoryVectorStore store = new InMemoryVectorStore();

	@Test
	void retainsOnlyThe50MostRecentlyUpsertedIncidents() {
		for (int i = 1; i <= 51; i++) {
			store.upsert(incident(i), new float[] { 1.0f });
		}

		assertEquals(50, store.count());
		assertFalse(store.all().stream().anyMatch(incident -> incident.id().equals("INC-001")));
		assertTrue(store.all().stream().anyMatch(incident -> incident.id().equals("INC-002")));
		assertTrue(store.all().stream().anyMatch(incident -> incident.id().equals("INC-051")));
	}

	@Test
	void updatingAnIncidentMakesItMostRecent() {
		for (int i = 1; i <= 50; i++) {
			store.upsert(incident(i), new float[] { 1.0f });
		}
		store.upsert(incident(1), new float[] { 1.0f });
		store.upsert(incident(51), new float[] { 1.0f });

		assertEquals(50, store.count());
		assertTrue(store.all().stream().anyMatch(incident -> incident.id().equals("INC-001")));
		assertFalse(store.all().stream().anyMatch(incident -> incident.id().equals("INC-002")));
	}

	private static Incident incident(int number) {
		String id = "INC-%03d".formatted(number);
		return new Incident(id, "Incident " + number, "test-service", "SEV3", "symptoms", "root cause",
				"resolution", "2026-11-12");
	}
}
