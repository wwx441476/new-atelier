package com.example.atelier.domain.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语义匹配 LLM 配置 — 存于 ATELIER_APP_SETTING。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticLlmConfig {

    private boolean enabled;

    private String provider;

    /**
     * 调用协议：openai | anthropic。
     * 为空时按 provider / baseUrl 自动推断（如 kimi-coding → anthropic）。
     */
    private String protocol;

    private String apiKey;

    private String model;

    private String baseUrl;

    private Integer timeoutSeconds;
}
