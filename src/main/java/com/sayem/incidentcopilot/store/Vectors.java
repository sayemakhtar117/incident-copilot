package com.sayem.incidentcopilot.store;

/** Utility methods for normalizing embeddings and comparing vectors. */
public final class Vectors {

	private Vectors() {
	}

	/**
	 * Scales a vector to unit length using its L2 norm.
	 *
	 * @param v vector to normalize
	 * @return a normalized copy, or the original vector when its norm is zero
	 */
	public static float[] normalize(float[] v) {
		double sum = 0;
		for (float x : v) {
			sum += (double) x * x;
		}
		double norm = Math.sqrt(sum);
		if (norm == 0) {
			return v;
		}
		float[] out = new float[v.length];
		for (int i = 0; i < v.length; i++) {
			out[i] = (float) (v[i] / norm);
		}
		return out;
	}

	/**
	 * Computes cosine similarity using the shared dimensions of two vectors.
	 *
	 * @param a first vector
	 * @param b second vector
	 * @return cosine similarity, or {@code 0} if either vector has zero magnitude
	 */
	public static double cosine(float[] a, float[] b) {
		int n = Math.min(a.length, b.length);
		double dot = 0;
		double na = 0;
		double nb = 0;
		for (int i = 0; i < n; i++) {
			dot += (double) a[i] * b[i];
			na += (double) a[i] * a[i];
			nb += (double) b[i] * b[i];
		}
		if (na == 0 || nb == 0) {
			return 0;
		}
		return dot / (Math.sqrt(na) * Math.sqrt(nb));
	}
}
