package com.example.atelier.domain.warning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语义规则样例试跑 — 单字段子条件结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticSampleCheckResult {

    private String fieldCode;

    private SemanticCheckMode checkMode;

    /** 子条件是否满足 */
    private boolean subConditionMet;

    private String reason;

    private String layer;

    private boolean llmInvoked;
}
