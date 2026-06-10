package com.example.atelier.warning.service;

import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.persistence.entity.AppSettingEntity;
import com.example.atelier.infra.persistence.jpa.AppSettingJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class SemanticLlmConfigLoader {

    public static final String SEMANTIC_LLM_KEY = "semantic.llm";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AppSettingJpaRepository repository;

    public SemanticLlmConfigLoader(AppSettingJpaRepository repository) {
        this.repository = repository;
    }

    public SemanticLlmConfig load() {
        return repository.findById(SEMANTIC_LLM_KEY)
                .map(entity -> fromJson(entity.getSettingValue()))
                .orElse(SemanticLlmConfig.builder()
                        .enabled(false)
                        .model("gpt-4o-mini")
                        .baseUrl("https://api.openai.com/v1")
                        .timeoutSeconds(30)
                        .build());
    }

    private SemanticLlmConfig fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return SemanticLlmConfig.builder().enabled(false).build();
        }
        try {
            return MAPPER.readValue(json, SemanticLlmConfig.class);
        } catch (JsonProcessingException e) {
            throw new AtelierException("LLM 配置反序列化失败", e);
        }
    }
}
