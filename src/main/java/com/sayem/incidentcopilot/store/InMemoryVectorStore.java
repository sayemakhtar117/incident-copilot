package com.sayem.incidentcopilot.store;

import com.sayem.incidentcopilot.model.Incident;
import com.sayem.incidentcopilot.model.ScoredIncident;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Brute-force cosine search with a bounded in-memory history. Data is lost on
 * restart.
 */
public class InMemoryVectorStore implements VectorStore {

	private static final int MAX_ENTRIES = 50;

	private record Entry(Incident incident, float[] vector) {
	}

	private final Map<String, Entry> entries = new LinkedHashMap<>();

	/**
	 * Stores the incident and its embedding in process memory, replacing any entry
	 * with the same ID.
	 */
	@Override
	public synchronized void upsert(Incident incident, float[] embedding) {
		entries.remove(incident.id());
		entries.put(incident.id(), new Entry(incident, embedding));
		if (entries.size() > MAX_ENTRIES) {
			String oldestId = entries.keySet().iterator().next();
			entries.remove(oldestId);
		}
	}

	/**
	 * Computes cosine similarity against every entry and returns the
	 * highest-scoring {@code k} incidents.
	 */
	@Override
	public synchronized List<ScoredIncident> search(float[] queryVector, int k) {
		return entries.values().stream()
				.map(e -> new ScoredIncident(e.incident(), Vectors.cosine(queryVector, e.vector())))
				.sorted(Comparator.comparingDouble(ScoredIncident::score).reversed()).limit(k).toList();
	}

	/** Returns stored incidents ordered by ID for stable display. */
	@Override
	public synchronized List<Incident> all() {
		return entries.values().stream().map(Entry::incident).sorted(Comparator.comparing(Incident::id)).toList();
	}

	/** Returns the current number of in-memory entries. */
	@Override
	public synchronized long count() {
		return entries.size();
	}

	/** Identifies this non-persistent implementation in API status responses. */
	@Override
	public String name() {
		return "in-memory";
	}
}
