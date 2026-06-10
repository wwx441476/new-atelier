package com.example.atelier.warning.service;

import com.example.atelier.domain.warning.SemanticFieldCheck;
import com.example.atelier.domain.warning.SemanticGroupMatchResult;
import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import com.example.atelier.warning.evaluator.SemanticEvaluationOptions;
import com.example.atelier.warning.evaluator.SemanticFieldCheckEvaluator;
import com.example.atelier.warning.evaluator.SemanticGroupEvaluator;
import com.example.atelier.warning.evaluator.SemanticRuleConfigSupport;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class SemanticRuleEvaluator {

    private final SemanticLlmConfigLoader llmConfigLoader;

    public SemanticRuleEvaluator(SemanticLlmConfigLoader llmConfigLoader) {
        this.llmConfigLoader = llmConfigLoader;
    }

    public SemanticGroupMatchResult evaluateRow(Map<String, Object> row, SemanticRuleConfig config) {
        return evaluateRow(row, config, SemanticEvaluationOptions.defaults());
    }

    public SemanticGroupMatchResult evaluateRow(Map<String, Object> row, SemanticRuleConfig config,
                                                SemanticEvaluationOptions options) {
        return new SemanticGroupEvaluator(llmConfigLoader.load(), options).evaluate(row, config);
    }

    /** 兼容单文本样例校验 */
    public SemanticMatchResult evaluate(String text, SemanticRuleConfig config) {
        List<SemanticFieldCheck> checks = SemanticRuleConfigSupport.flattenChecks(config);
        if (checks.isEmpty()) {
            return SemanticMatchResult.builder().triggered(false).layer("none").build();
        }
        SemanticFieldCheck first = checks.get(0);
        return fieldEvaluator().evaluateMatchResult(text, first);
    }

    public SemanticGroupMatchResult evaluateSampleRow(Map<String, Object> sampleRow, SemanticRuleConfig config) {
        if (sampleRow == null || sampleRow.isEmpty()) {
            String fieldCode = config != null ? config.getFieldCode() : null;
            if (fieldCode == null && config != null && config.getSemanticGroups() != null
                    && !config.getSemanticGroups().isEmpty()
                    && config.getSemanticGroups().get(0).getChecks() != null
                    && !config.getSemanticGroups().get(0).getChecks().isEmpty()) {
                fieldCode = config.getSemanticGroups().get(0).getChecks().get(0).getFieldCode();
            }
            return evaluateRow(Collections.singletonMap(fieldCode != null ? fieldCode : "text", ""), config);
        }
        return evaluateRow(sampleRow, config);
    }

    private SemanticFieldCheckEvaluator fieldEvaluator() {
        return new SemanticFieldCheckEvaluator(llmConfigLoader.load());
    }
}
