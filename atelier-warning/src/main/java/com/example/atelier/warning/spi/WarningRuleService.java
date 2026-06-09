package com.example.atelier.warning.spi;

import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.domain.warning.WarningRulePreviewResult;

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

    /** 预览规则关联指标数据，并标记每行是否触发预警 */
    WarningRulePreviewResult previewRule(String id, int pageIndex, int pageSize);
}
