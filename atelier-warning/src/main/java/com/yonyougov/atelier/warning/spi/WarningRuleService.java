package com.yonyougov.atelier.warning.spi;

import com.yonyougov.atelier.domain.warning.WarningRule;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 预警规则管理 SPI。
 */
public interface WarningRuleService {

    List<WarningRule> listRules();

    Optional<WarningRule> getRule(String id);

    Optional<WarningRule> getByCode(String code);

    WarningRule saveRule(WarningRule rule);

    void deleteRule(String id);

    /** 表达式评估桩 — 传入指标值上下文 */
    boolean evaluateExpression(String expression, Map<String, Object> metricValues);
}
