package com.example.atelier.infra.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.atelier.infra.exception.AtelierException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源连接属性 JSON 序列化。
 */
public final class DataSourcePropsMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<Map<String, String>>() {
    };

    private DataSourcePropsMapper() {
    }

    public static String toJson(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            throw new AtelierException("连接属性序列化失败", e);
        }
    }

    public static Map<String, String> fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, String> map = MAPPER.readValue(json, MAP_TYPE);
            return map != null ? new LinkedHashMap<>(map) : Collections.emptyMap();
        } catch (JsonProcessingException e) {
            throw new AtelierException("连接属性反序列化失败", e);
        }
    }
}
