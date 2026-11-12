package com.sayem.incidentcopilot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the whole pipeline in offline mode (no API keys, in-memory
 * store).
 */
@SpringBootTest(properties = { "copilot.llm.api-key=", "copilot.embedding.api-key=", "copilot.store.type=mongo",
		"copilot.store.persistence-enabled=false" })
@AutoConfigureMockMvc
class CopilotApiTest {

	@Autowired
	MockMvc mvc;

	@Test
	void statusReportsOfflineModeAndSeededIncidents() throws Exception {
		mvc.perform(get("/api/status")).andExpect(status().isOk()).andExpect(jsonPath("$.incidents").value(12))
				.andExpect(jsonPath("$.store").value("in-memory"));
	}

	@Test
	void openApiDocumentListsIncidentEndpoints() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Incident Copilot API"))
				.andExpect(jsonPath("$.paths['/api/analyze']").exists())
				.andExpect(jsonPath("$.paths['/api/incidents']").exists());
	}

	@Test
	void analyzeFindsSimilarIncidentUsesToolsAndRedactsSecrets() throws Exception {
		String body = """
				{"service":"payment-service",
				 "alert":"Checkout failing for jane@example.com: HikariPool-1 Connection is not available, request timed out after 30000ms, token=abc123secret"}
				""";
		mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andExpect(jsonPath("$.sources[0].id").value("INC-001"))
				.andExpect(jsonPath("$.sources[0].cited").value(true))
				.andExpect(jsonPath("$.toolsUsed", hasItem("queryLogs")))
				.andExpect(jsonPath("$.meta.redactions").value(2))
				.andExpect(jsonPath("$.confidence", greaterThanOrEqualTo(0.0)))
				.andExpect(jsonPath("$.remediationSteps[0]", not(containsString("jane@example.com"))))
				.andExpect(jsonPath("$.evidence", hasItem(containsString("v2.14.0"))));
	}

	@Test
	void secondIdenticalRequestIsServedFromCache() throws Exception {
		String body = "{\"alert\":\"OutOfMemoryError Java heap space, pod restarted repeatedly\",\"service\":\"order-service\"}";
		mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(jsonPath("$.meta.cached").value(false));
		mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(jsonPath("$.meta.cached").value(true));
	}

	@Test
	void blankAlertIsRejected() throws Exception {
		mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content("{\"alert\":\"  \"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void ingestedIncidentBecomesRetrievable() throws Exception {
		String incident = """
				{"title":"Thumbnail worker stuck on corrupt PNG","service":"media-service","severity":"SEV3",
				 "symptoms":"Thumbnail queue stalls, worker loops forever decoding a corrupt PNG file",
				 "rootCause":"Image decoder had no timeout and spun on a malformed PNG",
				 "resolution":"Added decode timeout. Quarantined the bad file."}
				""";
		mvc.perform(post("/api/incidents").contentType(MediaType.APPLICATION_JSON).content(incident))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON)
				.content("{\"alert\":\"thumbnail queue stalls, worker stuck decoding corrupt PNG\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sources[0].title").value("Thumbnail worker stuck on corrupt PNG"));
	}

	@Test
	void evaluationEndpointReportsRetrievalQuality() throws Exception {
		mvc.perform(get("/api/eval")).andExpect(status().isOk()).andExpect(jsonPath("$.cases").value(12))
				.andExpect(jsonPath("$.hitAt3", greaterThanOrEqualTo(0.8)));
	}
}
