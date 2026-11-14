package com.sayem.incidentcopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the whole pipeline in offline mode (no API keys, in-memory
 * store).
 */
@SpringBootTest(properties = { "copilot.llm.api-key=", "copilot.embedding.api-key=", "copilot.store.type=mongo",
		"copilot.store.persistence-enabled=false", "copilot.cors.allowed-origins=https://incidentpilot.in" })
@AutoConfigureMockMvc
class CopilotApiTest {

	private static final String TEST_PASSWORD_HASH = new BCryptPasswordEncoder(12).encode("test-password");

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper mapper;

	private String accessToken;

	@DynamicPropertySource
	static void authProperties(DynamicPropertyRegistry registry) {
		registry.add("copilot.auth.admin-email", () -> "test@example.com");
		registry.add("copilot.auth.admin-password-hash", () -> TEST_PASSWORD_HASH);
		registry.add("copilot.auth.jwt-secret-base64", () -> java.util.Base64.getEncoder().encodeToString(
				"test-signing-key-with-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
	}

	@BeforeEach
	void authenticate() throws Exception {
		String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"test@example.com\",\"password\":\"test-password\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		accessToken = mapper.readTree(response).get("accessToken").asText();
	}

	@Test
	void statusReportsOfflineModeAndSeededIncidents() throws Exception {
		mvc.perform(authenticated(get("/api/status"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.incidents").value(12))
				.andExpect(jsonPath("$.store").value("in-memory"));
	}

	@Test
	void incidentApiRejectsRequestsWithoutAuthentication() throws Exception {
		mvc.perform(get("/api/incidents")).andExpect(status().isUnauthorized());
	}

	@Test
	void browserCanPreflightBearerApiRequestsFromTheFrontendOrigin() throws Exception {
		mvc.perform(options("/api/status")
				.header("Origin", "https://incidentpilot.in")
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "authorization,content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "https://incidentpilot.in"));
	}

	@Test
	void loginRejectsIncorrectCredentials() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"test@example.com\",\"password\":\"incorrect-password\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void loginRejectsPasswordsBeyondTheBcryptByteLimit() throws Exception {
		String password = "é".repeat(37);
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(java.util.Map.of("email", "test@example.com", "password", password))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void loginTokenExpiresAfterFifteenMinutes() throws Exception {
		String claims = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"test@example.com\",\"password\":\"test-password\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(mapper.readTree(claims).get("expiresInSeconds").asLong())
				.isEqualTo(900);
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
		mvc.perform(authenticated(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content(body)))
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
		mvc.perform(authenticated(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content(body)))
				.andExpect(jsonPath("$.meta.cached").value(false));
		mvc.perform(authenticated(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content(body)))
				.andExpect(jsonPath("$.meta.cached").value(true));
	}

	@Test
	void blankAlertIsRejected() throws Exception {
		mvc.perform(authenticated(post("/api/analyze").contentType(MediaType.APPLICATION_JSON)
				.content("{\"alert\":\"  \"}")))
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
		mvc.perform(authenticated(post("/api/incidents").contentType(MediaType.APPLICATION_JSON).content(incident)))
				.andExpect(status().isCreated());
		mvc.perform(authenticated(post("/api/analyze").contentType(MediaType.APPLICATION_JSON)
				.content("{\"alert\":\"thumbnail queue stalls, worker stuck decoding corrupt PNG\"}")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sources[0].title").value("Thumbnail worker stuck on corrupt PNG"));
	}

	@Test
	void evaluationEndpointReportsRetrievalQuality() throws Exception {
		mvc.perform(authenticated(get("/api/eval"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.cases").value(12))
				.andExpect(jsonPath("$.hitAt3", greaterThanOrEqualTo(0.8)));
	}

	private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
		return request.header("Authorization", "Bearer " + accessToken);
	}
}
