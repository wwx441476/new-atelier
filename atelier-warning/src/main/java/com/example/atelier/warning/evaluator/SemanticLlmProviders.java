package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.settings.SemanticLlmConfig;

/**
 * 各 LLM 服务商默认配置。
 */
public final class SemanticLlmProviders {

    public static final String OPENAI = "openai";
    public static final String DASHSCOPE = "dashscope";
    public static final String KIMI = "kimi";
    public static final String CUSTOM = "custom";

    private SemanticLlmProviders() {
    }

    public static void applyProviderDefaults(SemanticLlmConfig config) {
        if (config == null || config.getProvider() == null) {
            return;
        }
        String provider = config.getProvider().trim().toLowerCase();
        switch (provider) {
            case OPENAI:
                defaultIfBlank(config, "https://api.openai.com/v1", "gpt-4o-mini");
                break;
            case DASHSCOPE:
                defaultIfBlank(config, "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-turbo");
                break;
            case KIMI:
            case "kimi-coding":
                if ("kimi-coding".equals(provider) || isKimiCodingEndpoint(config.getBaseUrl())) {
                    defaultIfBlank(config,
                            KimiEndpointSupport.CODING_ANTHROPIC_BASE_URL,
                            KimiEndpointSupport.CODING_ANTHROPIC_MODEL);
                } else {
                    defaultIfBlank(config,
                            KimiEndpointSupport.OPEN_PLATFORM_BASE_URL,
                            KimiEndpointSupport.OPEN_PLATFORM_MODEL);
                }
                break;
            default:
                break;
        }
    }

    public static boolean isKimiCodingEndpoint(String baseUrl) {
        return KimiEndpointSupport.isKimiCodingEndpoint(baseUrl);
    }

    private static void defaultIfBlank(SemanticLlmConfig config, String baseUrl, String model) {
        if (config.getBaseUrl() == null || config.getBaseUrl().trim().isEmpty()) {
            config.setBaseUrl(baseUrl);
        }
        if (config.getModel() == null || config.getModel().trim().isEmpty()) {
            config.setModel(model);
        }
    }
}
