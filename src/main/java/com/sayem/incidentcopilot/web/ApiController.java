package com.sayem.incidentcopilot.web;

import com.sayem.incidentcopilot.config.AppProperties;
import com.sayem.incidentcopilot.embedding.EmbeddingService;
import com.sayem.incidentcopilot.model.AnalysisResponse;
import com.sayem.incidentcopilot.model.AnalyzeRequest;
import com.sayem.incidentcopilot.model.Incident;
import com.sayem.incidentcopilot.model.NewIncidentRequest;
import com.sayem.incidentcopilot.service.CopilotService;
import com.sayem.incidentcopilot.service.EvaluationService;
import com.sayem.incidentcopilot.service.IncidentService;
import com.sayem.incidentcopilot.store.VectorStore;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Exposes incident analysis, knowledge-base, evaluation, and service-status
 * endpoints. Long-running analysis can also be requested as a server-sent event
 * stream.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

	private static final Logger log = LoggerFactory.getLogger(ApiController.class);

	private final CopilotService copilot;
	private final IncidentService incidents;
	private final EvaluationService evaluation;
	private final VectorStore store;
	private final EmbeddingService embeddings;
	private final AppProperties props;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	/**
	 * Creates the API controller with the application services used by its
	 * endpoints.
	 *
	 * @param copilot    incident analysis service
	 * @param incidents  incident knowledge-base service
	 * @param evaluation retrieval evaluation service
	 * @param store      configured incident vector store
	 * @param embeddings configured embedding service
	 * @param props      application configuration
	 */
	public ApiController(CopilotService copilot, IncidentService incidents, EvaluationService evaluation,
			VectorStore store, EmbeddingService embeddings, AppProperties props) {
		this.copilot = copilot;
		this.incidents = incidents;
		this.evaluation = evaluation;
		this.store = store;
		this.embeddings = embeddings;
		this.props = props;
	}

	/**
	 * Reports the active LLM, embedding, and vector-store implementations and
	 * incident count.
	 *
	 * @return service status fields for the UI and health checks
	 */
	@GetMapping("/status")
	public Map<String, Object> status() {
		return Map.of("llm", props.llm().enabled() ? props.llm().model() : "rule-based-offline (no LLM_API_KEY set)",
				"embedding", embeddings.name(), "store", store.name(), "incidents", store.count());
	}

	/**
	 * Lists incidents currently stored in the knowledge base.
	 *
	 * @return stored incidents
	 */
	@GetMapping("/incidents")
	public List<Incident> listIncidents() {
		return incidents.list();
	}

	/**
	 * Validates, redacts, embeds, and stores a resolved incident.
	 *
	 * @param request incident fields to add
	 * @return the created incident with HTTP status {@code 201 Created}
	 */
	@PostMapping("/incidents")
	public ResponseEntity<Incident> addIncident(@Valid @RequestBody NewIncidentRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(incidents.add(request));
	}

	/**
	 * Loads the bundled sample incidents into the configured vector store.
	 *
	 * @return the number of sample incidents loaded
	 */
	@PostMapping("/seed")
	public Map<String, Object> seed() {
		return Map.of("loaded", incidents.seed());
	}

	/**
	 * Analyzes an alert synchronously and returns its hypothesis, evidence, and
	 * remediation steps.
	 *
	 * @param request alert text and optional service name
	 * @return validated analysis response
	 */
	@PostMapping("/analyze")
	public AnalysisResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
		return copilot.analyze(request);
	}

	/**
	 * Streams analysis progress and the final result using Server-Sent Events.
	 *
	 * <p>
	 * Emits {@code stage} events while the pipeline runs, followed by one
	 * {@code result} event, or an {@code error} event if analysis fails. The stream
	 * is closed after completion and has a two-minute timeout.
	 * </p>
	 *
	 * @param alert   alert text to analyze; must contain 1 to 4000 characters
	 * @param service optional name of the affected service
	 * @return the asynchronous event stream
	 * @throws IllegalArgumentException if the alert is blank or exceeds 4000
	 *                                  characters
	 */
	@GetMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter analyzeStream(@RequestParam String alert, @RequestParam(required = false) String service) {
		if (alert.isBlank() || alert.length() > 4000) {
			throw new IllegalArgumentException("alert must be between 1 and 4000 characters");
		}
		SseEmitter emitter = new SseEmitter(120_000L);
		executor.submit(() -> {
			try {
				AnalysisResponse result = copilot.analyze(new AnalyzeRequest(alert, service),
						stage -> send(emitter, "stage", Map.of("message", stage)));
				send(emitter, "result", result);
			} catch (RuntimeException e) {
				log.warn("Streaming analysis failed", e);
				send(emitter, "error", Map.of("message", String.valueOf(e.getMessage())));
			} finally {
				emitter.complete();
			}
		});
		return emitter;
	}

	/**
	 * Evaluates retrieval quality against the bundled labeled alert set.
	 *
	 * @return hit-at-1, hit-at-3, mean reciprocal rank, and per-case results
	 */
	@GetMapping("/eval")
	public EvaluationService.Report eval() {
		return evaluation.run();
	}

	private static void send(SseEmitter emitter, String event, Object data) {
		try {
			emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
		} catch (IOException | IllegalStateException ignored) {
			// client disconnected
		}
	}
}
