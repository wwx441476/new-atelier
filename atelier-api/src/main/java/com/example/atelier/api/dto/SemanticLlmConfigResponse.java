package com.example.atelier.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticLlmConfigResponse {

    private boolean enabled;

    private String provider;

    private String model;

    private String baseUrl;

    private Integer timeoutSeconds;

    private boolean apiKeyConfigured;
}
