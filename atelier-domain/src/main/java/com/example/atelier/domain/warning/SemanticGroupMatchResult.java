package com.example.atelier.domain.warning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多字段语义分组求值结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticGroupMatchResult {

    private boolean triggered;

    /** 各字段子条件是否满足 */
    @Builder.Default
    private Map<String, Boolean> checkTriggered = new LinkedHashMap<>();

    /** 各字段原始匹配结果 */
    @Builder.Default
    private Map<String, SemanticMatchResult> checkResults = new LinkedHashMap<>();
}
