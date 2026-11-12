package com.sayem.incidentcopilot.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sayem.incidentcopilot.config.AppProperties;
import com.sayem.incidentcopilot.embedding.EmbeddingService;
import com.sayem.incidentcopilot.guardrails.AnswerValidator;
import com.sayem.incidentcopilot.guardrails.Redactor;
import com.sayem.incidentcopilot.llm.AnalysisContext;
import com.sayem.incidentcopilot.llm.LlmClient;
import com.sayem.incidentcopilot.llm.LlmResult;
import com.sayem.incidentcopilot.llm.RuleBasedLlmClient;
import com.sayem.incidentcopilot.model.AnalysisResponse;
import com.sayem.incidentcopilot.model.AnalyzeRequest;
import com.sayem.incidentcopilot.model.ScoredIncident;
import com.sayem.incidentcopilot.store.VectorStore;
import com.sayem.incidentcopilot.tools.ToolRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The pipeline: redact -> embed -> retrieve similar incidents -> agent (LLM +
 * tools) -> validate -> respond. The primary LLM path sits behind a circuit
 * breaker; if it fails or its answer is invalid, the deterministic offline
 * analyst provides a fallback response.
 */
@Service
public class CopilotService {

	private static final Logger log = LoggerFactory.getLogger(CopilotService.class);

	private final Redactor redactor;
	private final EmbeddingService embeddings;
	private final VectorStore store;
	private final LlmClient primary;
	private final RuleBasedLlmClient fallback;
	private final ToolRegistry tools;
	private final AnswerValidator validator;
	private final AppProperties props;
	private final MeterRegistry meters;
	private final CircuitBreaker breaker;
	private final Cache<String, AnalysisResponse> cache = Caffeine.newBuilder().maximumSize(500)
			.expireAfterWrite(Duration.ofMinutes(30)).build();

	public CopilotService(Redactor redactor, EmbeddingService embeddings, VectorStore store,
			@Qualifier("primaryLlm") LlmClient primary, @Qualifier("ruleBasedLlmClient") RuleBasedLlmClient fallback, ToolRegistry tools,
			AnswerValidator validator, AppProperties props, MeterRegistry meters) {
		this.redactor = redactor;
		this.embeddings = embeddings;
		this.store = store;
		this.primary = primary;
		this.fallback = fallback;
		this.tools = tools;
		this.validator = validator;
		this.props = props;
		this.meters = meters;
		this.breaker = CircuitBreaker.of("llm",
				CircuitBreakerConfig.custom().slidingWindowSize(6).minimumNumberOfCalls(3).failureRateThreshold(60)
						.waitDurationInOpenState(Duration.ofSeconds(30)).build());
	}

	/**
	 * Analyzes an alert without reporting intermediate pipeline stages.
	 *
	 * @param request alert text and optional service name
	 * @return validated analysis response, served from cache when available
	 */
	public AnalysisResponse analyze(AnalyzeRequest request) {
		return analyze(request, stage -> {
		});
	}

	/**
	 * Runs the analysis pipeline: redact input, embed and retrieve incidents,
	 * investigate with the primary LLM, validate its answer, and fall back to the
	 * offline analyst if the primary path fails.
	 *
	 * @param request       alert text and optional service name
	 * @param stageListener receives human-readable progress updates; must not be
	 *                      {@code null}
	 * @return validated analysis response
	 */
	public AnalysisResponse analyze(AnalyzeRequest request, Consumer<String> stageListener) {
		long start = System.nanoTime();

		stageListener.accept("Redacting secrets and PII");
		Redactor.Result redacted = redactor.redact(request.alert());
		String requestedService = request.service() == null ? "" : request.service().trim();

		String cacheKey = sha256(requestedService + "|" + redacted.text());
		AnalysisResponse cached = cache.getIfPresent(cacheKey);
		if (cached != null) {
			meters.counter("copilot.cache.hits").increment();
			var m = cached.meta();
			return cached.withMeta(new AnalysisResponse.Meta(m.llm(), m.embedding(), m.store(), elapsedMs(start),
					m.redactions(), true, m.fallbackUsed(), 0, 0));
		}

		stageListener.accept("Embedding the alert and searching similar past incidents");
		float[] query = embeddings.embed(redacted.text());
		List<ScoredIncident> similar = store.search(query, props.rag().topK()).stream()
				.filter(s -> s.score() >= props.rag().minScore()).toList();
		String service = !requestedService.isEmpty() ? requestedService
				: similar.isEmpty() ? "unknown" : similar.get(0).incident().service();

		stageListener.accept("Agent investigating with tools (" + primary.name() + ")");
		AnalysisContext context = new AnalysisContext(redacted.text(), service, similar);
		Set<String> retrievedIds = similar.stream().map(s -> s.incident().id()).collect(Collectors.toSet());

		boolean fallbackUsed = false;
		LlmResult result;
		AnswerValidator.Validated answer;
		String llmName = primary.name();
		try {
			result = runPrimary(context);
			answer = validator.validate(result.content(), retrievedIds);
		} catch (RuntimeException e) {
			log.warn("Primary LLM path failed ({}), using offline analyst", e.toString());
			meters.counter("copilot.fallbacks").increment();
			fallbackUsed = true;
			llmName = fallback.name();
			stageListener.accept("LLM unavailable, falling back to the offline analyst");
			result = fallback.analyze(context, tools);
			answer = validator.validate(result.content(), retrievedIds);
		}

		meters.counter("copilot.llm.tokens", "type", "prompt").increment(result.promptTokens());
		meters.counter("copilot.llm.tokens", "type", "completion").increment(result.completionTokens());

		final AnswerValidator.Validated finalAnswer = answer;
		List<AnalysisResponse.Source> sources = similar.stream()
				.map(s -> new AnalysisResponse.Source(s.incident().id(), s.incident().title(), s.incident().service(),
						Math.round(s.score() * 1000) / 1000.0, finalAnswer.citedIds().contains(s.incident().id())))
				.toList();

		long latency = elapsedMs(start);
		meters.timer("copilot.analysis.latency").record(Duration.ofMillis(latency));
		AnalysisResponse response = new AnalysisResponse(service, answer.hypothesis(),
				Math.round(answer.confidence() * 100) / 100.0, answer.steps(), answer.evidence(), sources,
				result.toolsUsed(), new AnalysisResponse.Meta(llmName, embeddings.name(), store.name(), latency,
						redacted.count(), false, fallbackUsed, result.promptTokens(), result.completionTokens()));
		cache.put(cacheKey, response);
		return response;
	}

	private LlmResult runPrimary(AnalysisContext context) {
		if (primary == fallback) {
			return fallback.analyze(context, tools);
		}
		return breaker.executeSupplier(() -> primary.analyze(context, tools));
	}

	private static long elapsedMs(long startNanos) {
		return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
	}

	private static String sha256(String s) {
		try {
			return HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
