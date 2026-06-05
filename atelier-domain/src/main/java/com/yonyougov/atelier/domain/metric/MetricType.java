package com.yonyougov.atelier.domain.metric;

/**
 * 指标类型 — 对应旧版 IndexType，但语义更清晰。
 */
public enum MetricType {

    /** 基于物理表/模型 */
    TABLE,

    /** 基于自定义 SQL 数据集 */
    SQL,

    /** 复合指标，引用其他指标 code */
    COMPOSITE
}
