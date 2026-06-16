package com.example.atelier.infra.persistence.mapper;

import com.example.atelier.domain.dashboard.DashboardScreen;
import com.example.atelier.infra.exception.AtelierException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class DashboardScreenJsonMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private DashboardScreenJsonMapper() {
    }

    public static String toJson(DashboardScreen screen) {
        try {
            return MAPPER.writeValueAsString(screen);
        } catch (JsonProcessingException e) {
            throw new AtelierException("大屏定义序列化失败", e);
        }
    }

    public static DashboardScreen fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, DashboardScreen.class);
        } catch (JsonProcessingException e) {
            throw new AtelierException("大屏定义反序列化失败", e);
        }
    }
}
