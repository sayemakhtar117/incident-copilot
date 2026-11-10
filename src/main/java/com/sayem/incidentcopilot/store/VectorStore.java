package com.sayem.incidentcopilot.store;

import com.sayem.incidentcopilot.model.Incident;
import com.sayem.incidentcopilot.model.ScoredIncident;

import java.util.List;

/**
 * Persistence contract for incidents and their embeddings. Implementations
 * provide nearest-neighbor search while allowing the application to switch
 * between in-memory and MongoDB storage.
 */
public interface VectorStore {

	/**
	 * Inserts or replaces an incident and its embedding, using the incident ID as
	 * its key.
	 *
	 * @param incident  incident document to persist
	 * @param embedding vector generated from the incident text
	 */
	void upsert(Incident incident, float[] embedding);

	/**
	 * Returns up to {@code k} incidents ranked by similarity to a query vector.
	 *
	 * @param queryVector embedding to search with
	 * @param k           maximum number of matches
	 * @return matching incidents ordered from most to least similar
	 */
	List<ScoredIncident> search(float[] queryVector, int k);

	/**
	 * Returns all stored incident records without embedding vectors.
	 *
	 * @return all stored incidents
	 */
	List<Incident> all();

	/**
	 * Returns the number of stored incidents.
	 *
	 * @return incident count
	 */
	long count();

	/**
	 * Identifies the active storage implementation for status reporting.
	 *
	 * @return implementation name
	 */
	String name();
}
