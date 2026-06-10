package com.example.atelier.warning.spi;

import com.example.atelier.domain.metric.FilterCondition;
import com.example.atelier.domain.metric.FilterGroup;
import com.example.atelier.domain.warning.ExpressionValidateResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import com.example.atelier.domain.warning.SemanticValidateResult;
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

    boolean evaluateExpression(String expression, Map<String, Object> metricValues);

    ExpressionValidateResult validateExpression(String expression, List<String> metricCodes);

    SemanticValidateResult validateSemantic(SemanticRuleConfig config, String sampleText,
                                            Map<String, Object> sampleRow);

    Map<String, List<String>> expandKeywords(SemanticRuleConfig config);

    WarningRulePreviewResult previewRule(String id, int pageIndex, int pageSize,
                                       List<FilterCondition> filters, List<FilterGroup> filterGroups);

    WarningRulePreviewResult previewRule(String id, int pageIndex, int pageSize,
                                       List<FilterCondition> filters, List<FilterGroup> filterGroups,
                                       boolean keywordOnly);
}
