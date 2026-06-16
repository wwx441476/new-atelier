package com.example.atelier.infra.persistence.mapper;

import com.example.atelier.domain.copilot.CopilotPlaybook;
import com.example.atelier.infra.exception.AtelierException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class CopilotPlaybookJsonMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private CopilotPlaybookJsonMapper() {
    }

    public static String toJson(CopilotPlaybook playbook) {
        try {
            return MAPPER.writeValueAsString(playbook);
        } catch (JsonProcessingException e) {
            throw new AtelierException("技能定义序列化失败", e);
        }
    }

    public static CopilotPlaybook fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, CopilotPlaybook.class);
        } catch (JsonProcessingException e) {
            throw new AtelierException("技能定义反序列化失败", e);
        }
    }
}
