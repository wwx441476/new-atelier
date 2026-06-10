package com.example.atelier.infra.persistence.mapper;

import com.example.atelier.domain.warning.CompositeRuleConfig;
import com.example.atelier.infra.exception.AtelierException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 预警规则扩展配置 JSON 序列化。
 */
public final class RuleConfigMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RuleConfigMapper() {
    }

    public static String toJson(CompositeRuleConfig config) {
        if (config == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new AtelierException("规则配置序列化失败", e);
        }
    }

    public static CompositeRuleConfig fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, CompositeRuleConfig.class);
        } catch (JsonProcessingException e) {
            throw new AtelierException("规则配置反序列化失败", e);
        }
    }
}
