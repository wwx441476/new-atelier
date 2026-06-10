package com.example.atelier.domain.warning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 组合预警规则配置 — 将指标表达式与语义规则按逻辑组合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompositeRuleConfig {

    /** 触发逻辑：AND / OR */
    private String triggerLogic;

    private SemanticRuleConfig semantic;
}
