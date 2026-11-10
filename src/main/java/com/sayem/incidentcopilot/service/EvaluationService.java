package com.sayem.incidentcopilot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sayem.incidentcopilot.embedding.EmbeddingService;
import com.sayem.incidentcopilot.guardrails.Redactor;
import com.sayem.incidentcopilot.model.ScoredIncident;
import com.sayem.incidentcopilot.store.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Measures retrieval quality on a labelled set of alerts: hit@1, hit@3 and mean
 * reciprocal rank. Re-run it whenever you change the embedding model, chunking
 * or top-k, so changes are measured instead of guessed.
 */
@Service
public class EvaluationService {

	public record EvalCase(String alert, String service, String expectedIncidentId) {
	}

	/**
	 * Retrieval outcome for one labeled alert; rank is zero when the expected
	 * incident was not retrieved.
	 */
	public record CaseResult(String alert, String expected, List<String> retrieved, int rank) {
	}

	/**
	 * Aggregate retrieval metrics and the per-alert results used to calculate them.
	 */
	public record Report(int cases, double hitAt1, double hitAt3, double mrr, String embedding, String store,
			List<CaseResult> results) {
	}

	private final VectorStore store;
	private final EmbeddingService embeddings;
	private final Redactor redactor;
	private final ObjectMapper mapper;
	private final IncidentService incidents;

	public EvaluationService(VectorStore store, EmbeddingService embeddings, Redactor redactor, ObjectMapper mapper,
			IncidentService incidents) {
		this.store = store;
		this.embeddings = embeddings;
		this.redactor = redactor;
		this.mapper = mapper;
		this.incidents = incidents;
	}

	/**
	 * Embeds each labeled alert, searches the configured store, and calculates
	 * hit@1, hit@3, and MRR. Seeds the bundled incidents first only when the store
	 * is empty.
	 *
	 * @return aggregate metrics and individual retrieval rankings
	 */
	public Report run() {
		incidents.seedIfEmpty();
		List<EvalCase> cases = load();
		List<CaseResult> results = new ArrayList<>();
		int hit1 = 0;
		int hit3 = 0;
		double rr = 0;
		for (EvalCase c : cases) {
			float[] q = embeddings.embed(redactor.redact(c.alert()).text());
			List<String> ids = store.search(q, 5).stream().map(ScoredIncident::incident).map(i -> i.id()).toList();
			int idx = ids.indexOf(c.expectedIncidentId());
			int rank = idx < 0 ? 0 : idx + 1;
			if (rank == 1) {
				hit1++;
			}
			if (rank >= 1 && rank <= 3) {
				hit3++;
			}
			if (rank > 0) {
				rr += 1.0 / rank;
			}
			results.add(new CaseResult(c.alert(), c.expectedIncidentId(), ids, rank));
		}
		int n = Math.max(1, cases.size());
		return new Report(cases.size(), round(hit1 / (double) n), round(hit3 / (double) n), round(rr / n),
				embeddings.name(), store.name(), results);
	}

	private List<EvalCase> load() {
		try (InputStream in = new ClassPathResource("eval/cases.json").getInputStream()) {
			return mapper.readValue(in, new TypeReference<>() {
			});
		} catch (IOException e) {
			throw new IllegalStateException("Could not read eval cases", e);
		}
	}

	private static double round(double v) {
		return Math.round(v * 1000) / 1000.0;
	}
}
