package com.example.atelier.domain.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置导入结果统计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigImportResult {

    @Builder.Default
    private Map<String, Integer> imported = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Integer> skipped = new LinkedHashMap<>();

    private String message;
}
