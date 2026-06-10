package com.example.atelier.domain.warning;

/**
 * 预警规则类型。
 */
public enum WarningRuleType {

    /** 基于指标表达式的传统规则 */
    METRIC,

    /** 基于语义匹配的规则 */
    SEMANTIC,

    /** 指标表达式与语义规则组合 */
    COMPOSITE
}
