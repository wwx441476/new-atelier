package com.example.atelier.api.dto;

import lombok.Data;

@Data
public class SemanticLlmProfileRequest {

    private String id;

    private String name;

    private Boolean enabled;

    private String provider;

    /** openai | anthropic */
    private String protocol;

    /** 留空表示不修改已保存的 Key */
    private String apiKey;

    private String model;

    private String baseUrl;

    private Integer timeoutSeconds;
}
