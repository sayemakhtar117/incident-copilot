package com.sayem.incidentcopilot.embedding;

public interface EmbeddingService {

	/** Returns an L2-normalised embedding. */
	float[] embed(String text);

	int dimensions();

	String name();
}
