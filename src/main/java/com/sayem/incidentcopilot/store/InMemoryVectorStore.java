package com.sayem.incidentcopilot.store;

import com.sayem.incidentcopilot.model.Incident;
import com.sayem.incidentcopilot.model.ScoredIncident;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force cosine search. Fine for thousands of incidents; zero setup. Data
 * is lost on restart.
 */
public class InMemoryVectorStore implements VectorStore {

	private record Entry(Incident incident, float[] vector) {
	}

	private final Map<String, Entry> entries = new ConcurrentHashMap<>();

	/**
	 * Stores the incident and its embedding in process memory, replacing any entry
	 * with the same ID.
	 */
	@Override
	public void upsert(Incident incident, float[] embedding) {
		entries.put(incident.id(), new Entry(incident, embedding));
	}

	/**
	 * Computes cosine similarity against every entry and returns the
	 * highest-scoring {@code k} incidents.
	 */
	@Override
	public List<ScoredIncident> search(float[] queryVector, int k) {
		return entries.values().stream()
				.map(e -> new ScoredIncident(e.incident(), Vectors.cosine(queryVector, e.vector())))
				.sorted(Comparator.comparingDouble(ScoredIncident::score).reversed()).limit(k).toList();
	}

	/** Returns stored incidents ordered by ID for stable display. */
	@Override
	public List<Incident> all() {
		return entries.values().stream().map(Entry::incident).sorted(Comparator.comparing(Incident::id)).toList();
	}

	/** Returns the current number of in-memory entries. */
	@Override
	public long count() {
		return entries.size();
	}

	/** Identifies this non-persistent implementation in API status responses. */
	@Override
	public String name() {
		return "in-memory";
	}
}
