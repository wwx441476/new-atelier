package com.example.atelier.infra.persistence.mapper;

import com.example.atelier.domain.warning.WarningRuleJobParams;
import com.example.atelier.domain.warning.WarningRulePreviewResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class WarningRuleJobMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WarningRuleJobMapper() {
    }

    public static String paramsToJson(WarningRuleJobParams params) {
        if (params == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("任务参数序列化失败", e);
        }
    }

    public static WarningRuleJobParams paramsFromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return WarningRuleJobParams.builder().build();
        }
        try {
            return MAPPER.readValue(json, WarningRuleJobParams.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("任务参数解析失败", e);
        }
    }

    public static String resultToJson(WarningRulePreviewResult result) {
        if (result == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("任务结果序列化失败", e);
        }
    }

    public static WarningRulePreviewResult resultFromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, WarningRulePreviewResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("任务结果解析失败", e);
        }
    }
}
