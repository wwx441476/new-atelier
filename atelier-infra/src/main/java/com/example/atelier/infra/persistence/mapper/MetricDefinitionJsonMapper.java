package com.example.atelier.infra.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.atelier.domain.metric.MetricDefinition;
import com.example.atelier.infra.exception.AtelierException;

/**
 * 指标定义 JSON 序列化 — 整份声明式定义存入 DEFINITION_JSON 列。
 */
public final class MetricDefinitionJsonMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MetricDefinitionJsonMapper() {
    }

    public static String toJson(MetricDefinition definition) {
        try {
            return MAPPER.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new AtelierException("指标定义序列化失败", e);
        }
    }

    public static MetricDefinition fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, MetricDefinition.class);
        } catch (JsonProcessingException e) {
            throw new AtelierException("指标定义反序列化失败", e);
        }
    }
}
