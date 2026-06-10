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

    private String apiKey;

    private String model;

    private String baseUrl;

    private Integer timeoutSeconds;
}
