package com.sayem.incidentcopilot.embedding;

import com.sayem.incidentcopilot.store.Vectors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Local, dependency-free embedder (feature hashing of unigrams + bigrams). It
 * needs no API key and no network, so the whole project runs offline. It is
 * lexical, not semantic: swap in a real embedding model (see
 * OpenAiCompatEmbeddingService) for paraphrase-level retrieval.
 */
public class HashingEmbeddingService implements EmbeddingService {

	private static final Set<String> STOPWORDS = Set.of("the", "and", "for", "with", "from", "that", "this", "was",
			"were", "are", "has", "have", "had", "not", "but", "into", "after", "before", "during", "all", "any", "its",
			"our", "out", "too", "very", "than", "then", "also", "can", "could", "would", "should", "seeing", "see",
			"seen", "started", "start", "now", "just", "while", "when", "where", "which", "what", "who", "how",
			"service", "services", "error", "errors", "high", "alert", "pod", "pods");

	private final int dims;

	public HashingEmbeddingService(int dims) {
		this.dims = Math.max(64, dims);
	}

	@Override
	public float[] embed(String text) {
		float[] v = new float[dims];
		List<String> tokens = tokenize(text == null ? "" : text);
		for (int i = 0; i < tokens.size(); i++) {
			add(v, tokens.get(i), 1.0f);
			if (i + 1 < tokens.size()) {
				add(v, tokens.get(i) + "_" + tokens.get(i + 1), 0.6f);
			}
		}
		return Vectors.normalize(v);
	}

	@Override
	public int dimensions() {
		return dims;
	}

	@Override
	public String name() {
		return "local-hashing-" + dims;
	}

	private void add(float[] v, String token, float weight) {
		int h = fnv1a(token);
		int idx = Math.floorMod(h, dims);
		float sign = ((h >>> 16) & 1) == 0 ? 1f : -1f;
		v[idx] += sign * weight;
	}

	static List<String> tokenize(String text) {
		String[] raw = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
		List<String> out = new ArrayList<>();
		for (String t : raw) {
			if (t.length() < 2 || STOPWORDS.contains(t)) {
				continue;
			}
			out.add(stem(t));
		}
		return out;
	}

	private static String stem(String t) {
		if (t.length() > 6 && t.endsWith("ing")) {
			return t.substring(0, t.length() - 3);
		}
		if (t.length() > 5 && t.endsWith("ed")) {
			return t.substring(0, t.length() - 2);
		}
		if (t.length() > 4 && t.endsWith("s") && !t.endsWith("ss")) {
			return t.substring(0, t.length() - 1);
		}
		return t;
	}

	private static int fnv1a(String s) {
		int h = 0x811c9dc5;
		for (int i = 0; i < s.length(); i++) {
			h ^= s.charAt(i);
			h *= 0x01000193;
		}
		return h;
	}
}
