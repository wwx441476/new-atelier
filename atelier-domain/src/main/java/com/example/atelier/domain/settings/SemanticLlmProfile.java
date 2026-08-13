package com.example.atelier.domain.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可命名的 LLM 配置档案，支持同时保存多套并在 Copilot 中切换。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticLlmProfile {

    private String id;

    private String name;

    private boolean enabled;

    private String provider;

    /** openai | anthropic；空则自动推断 */
    private String protocol;

    private String apiKey;

    private String model;

    private String baseUrl;

    private Integer timeoutSeconds;

    public SemanticLlmConfig toConfig() {
        return SemanticLlmConfig.builder()
                .enabled(enabled)
                .provider(provider)
                .protocol(protocol)
                .apiKey(apiKey)
                .model(model)
                .baseUrl(baseUrl)
                .timeoutSeconds(timeoutSeconds)
                .build();
    }

    public static SemanticLlmProfile fromConfig(String id, String name, SemanticLlmConfig config) {
        if (config == null) {
            return SemanticLlmProfile.builder().id(id).name(name).enabled(false).build();
        }
        return SemanticLlmProfile.builder()
                .id(id)
                .name(name)
                .enabled(config.isEnabled())
                .provider(config.getProvider())
                .protocol(config.getProtocol())
                .apiKey(config.getApiKey())
                .model(config.getModel())
                .baseUrl(config.getBaseUrl())
                .timeoutSeconds(config.getTimeoutSeconds())
                .build();
    }
}
