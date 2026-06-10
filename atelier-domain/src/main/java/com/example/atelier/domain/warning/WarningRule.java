package com.example.atelier.domain.warning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 预警规则 — 对应旧版 DMP_ATELIER_WARNING_RULE。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarningRule {

    private String id;

    private String catalogCode;

    private String code;

    private String name;

    /** 关联指标 code 列表 */
    private List<String> metricCodes;

    /** 预警表达式，如 revenue > 1000 */
    private String expression;

    private Boolean enabled;

    /** 默认预警级别 */
    private Integer warningLevel;

    /** 通知配置 JSON 桩 */
    private String notifyConfig;

    @Builder.Default
    private WarningRuleType ruleType = WarningRuleType.METRIC;

    /** SEMANTIC / COMPOSITE 规则的扩展配置；语义配置位于 ruleConfig.semantic */
    private CompositeRuleConfig ruleConfig;

    private String comments;
}
