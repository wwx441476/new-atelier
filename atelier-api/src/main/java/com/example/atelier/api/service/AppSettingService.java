package com.example.atelier.api.service;

import com.example.atelier.api.dto.SemanticLlmConfigRequest;
import com.example.atelier.api.dto.SemanticLlmConfigResponse;
import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.persistence.entity.AppSettingEntity;
import com.example.atelier.infra.persistence.jpa.AppSettingJpaRepository;
import com.example.atelier.warning.evaluator.LlmChatClient;
import com.example.atelier.warning.evaluator.SemanticLlmProviders;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AppSettingService {

    private static final String SEMANTIC_LLM_KEY = "semantic.llm";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AppSettingJpaRepository repository;
    private final LlmChatClient chatClient = new LlmChatClient();

    public AppSettingService(AppSettingJpaRepository repository) {
        this.repository = repository;
    }

    public SemanticLlmConfig getSemanticLlmConfig() {
        return repository.findById(SEMANTIC_LLM_KEY)
                .map(entity -> fromJson(entity.getSettingValue()))
                .orElse(defaultConfig());
    }

    public SemanticLlmConfigResponse getSemanticLlmConfigForResponse() {
        SemanticLlmConfig config = getSemanticLlmConfig();
        return SemanticLlmConfigResponse.builder()
                .enabled(config.isEnabled())
                .provider(config.getProvider())
                .model(config.getModel())
                .baseUrl(config.getBaseUrl())
                .timeoutSeconds(config.getTimeoutSeconds())
                .apiKeyConfigured(config.getApiKey() != null && !config.getApiKey().trim().isEmpty())
                .build();
    }

    @Transactional
    public void saveSemanticLlmConfig(SemanticLlmConfigRequest request) {
        SemanticLlmConfig existing = getSemanticLlmConfig();
        String apiKey = request.getApiKey() != null && !request.getApiKey().trim().isEmpty()
                ? request.getApiKey().trim()
                : existing.getApiKey();

        SemanticLlmConfig config = SemanticLlmConfig.builder()
                .enabled(request.getEnabled() != null ? request.getEnabled() : existing.isEnabled())
                .provider(firstNonBlank(request.getProvider(), existing.getProvider()))
                .apiKey(apiKey)
                .model(firstNonBlank(request.getModel(), existing.getModel()))
                .baseUrl(firstNonBlank(request.getBaseUrl(), existing.getBaseUrl()))
                .timeoutSeconds(request.getTimeoutSeconds() != null
                        ? request.getTimeoutSeconds()
                        : existing.getTimeoutSeconds())
                .build();
        SemanticLlmProviders.applyProviderDefaults(config);

        AppSettingEntity entity = repository.findById(SEMANTIC_LLM_KEY)
                .orElse(AppSettingEntity.builder().settingKey(SEMANTIC_LLM_KEY).build());
        entity.setSettingValue(toJson(config));
        entity.setModifyTime(LocalDateTime.now());
        repository.save(entity);
    }

    public boolean testConnection(SemanticLlmConfigRequest request) {
        SemanticLlmConfig config = buildConfigForTest(request);
        chatClient.chat(config, "Reply with OK only.", "ping");
        return true;
    }

    private SemanticLlmConfig buildConfigForTest(SemanticLlmConfigRequest request) {
        SemanticLlmConfig existing = getSemanticLlmConfig();
        String apiKey = request.getApiKey() != null && !request.getApiKey().trim().isEmpty()
                ? request.getApiKey().trim()
                : existing.getApiKey();
        SemanticLlmConfig config = SemanticLlmConfig.builder()
                .enabled(true)
                .provider(firstNonBlank(request.getProvider(), existing.getProvider()))
                .apiKey(apiKey)
                .model(firstNonBlank(request.getModel(), existing.getModel()))
                .baseUrl(firstNonBlank(request.getBaseUrl(), existing.getBaseUrl()))
                .timeoutSeconds(request.getTimeoutSeconds() != null
                        ? request.getTimeoutSeconds()
                        : Optional.ofNullable(existing.getTimeoutSeconds()).orElse(30))
                .build();
        SemanticLlmProviders.applyProviderDefaults(config);
        if (config.getModel() == null || config.getModel().trim().isEmpty()) {
            config.setModel("gpt-4o-mini");
        }
        return config;
    }

    private SemanticLlmConfig defaultConfig() {
        SemanticLlmConfig config = SemanticLlmConfig.builder()
                .enabled(false)
                .provider(SemanticLlmProviders.OPENAI)
                .model("gpt-4o-mini")
                .baseUrl("https://api.openai.com/v1")
                .timeoutSeconds(30)
                .build();
        SemanticLlmProviders.applyProviderDefaults(config);
        return config;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String toJson(SemanticLlmConfig config) {
        try {
            return MAPPER.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new AtelierException("LLM 配置序列化失败", e);
        }
    }

    private static SemanticLlmConfig fromJson(String json) {
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
