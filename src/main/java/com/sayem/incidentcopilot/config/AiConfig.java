package com.sayem.incidentcopilot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sayem.incidentcopilot.embedding.EmbeddingService;
import com.sayem.incidentcopilot.embedding.HashingEmbeddingService;
import com.sayem.incidentcopilot.embedding.OpenAiCompatEmbeddingService;
import com.sayem.incidentcopilot.llm.JsonHttp;
import com.sayem.incidentcopilot.llm.LlmClient;
import com.sayem.incidentcopilot.llm.OpenAiCompatLlmClient;
import com.sayem.incidentcopilot.llm.RuleBasedLlmClient;
import com.sayem.incidentcopilot.store.InMemoryVectorStore;
import com.sayem.incidentcopilot.store.MongoVectorStore;
import com.sayem.incidentcopilot.store.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Picks real or offline implementations purely from configuration; no code
 * change needed to switch.
 */
@Configuration
public class AiConfig {

	@Bean
	EmbeddingService embeddingService(AppProperties props, JsonHttp http) {
		AppProperties.Embedding cfg = props.embedding();
		return cfg.enabled() ? new OpenAiCompatEmbeddingService(http, cfg)
				: new HashingEmbeddingService(cfg.dimensions());
	}

	@Bean
	VectorStore vectorStore(AppProperties props, ObjectProvider<MongoTemplate> template) {
		AppProperties.Store cfg = props.store();
		if (cfg.persistenceEnabled() && "mongo".equalsIgnoreCase(cfg.type())) {
			return new MongoVectorStore(template.getObject(), cfg.collection(), cfg.indexName());
		}
		return new InMemoryVectorStore();
	}

	@Bean("primaryLlm")
	LlmClient primaryLlm(AppProperties props, JsonHttp http, ObjectMapper mapper, RuleBasedLlmClient rules) {
		AppProperties.Llm cfg = props.llm();
		return cfg.enabled() ? new OpenAiCompatLlmClient(http, mapper, cfg) : rules;
	}
}
