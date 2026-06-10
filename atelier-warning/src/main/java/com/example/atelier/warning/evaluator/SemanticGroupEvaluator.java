package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.domain.warning.SemanticCheckGroup;
import com.example.atelier.domain.warning.SemanticFieldCheck;
import com.example.atelier.domain.warning.SemanticGroupMatchResult;
import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多字段语义条件组求值。
 */
public class SemanticGroupEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SemanticGroupEvaluator.class);

    private final SemanticFieldCheckEvaluator fieldCheckEvaluator;

    public SemanticGroupEvaluator(SemanticLlmConfig llmConfig) {
        this.fieldCheckEvaluator = new SemanticFieldCheckEvaluator(llmConfig);
    }

    public SemanticGroupMatchResult evaluate(Map<String, Object> row, SemanticRuleConfig config) {
        List<SemanticCheckGroup> groups = SemanticRuleConfigSupport.normalizeGroups(config);
        Map<String, Boolean> checkTriggered = new LinkedHashMap<>();
        Map<String, SemanticMatchResult> checkResults = new LinkedHashMap<>();

        boolean anyGroupMatched = false;
        for (SemanticCheckGroup group : groups) {
            if (group.getChecks() == null || group.getChecks().isEmpty()) {
                continue;
            }
            boolean groupMatched = true;
            for (SemanticFieldCheck check : group.getChecks()) {
                String fieldCode = check.getFieldCode();
                Object raw = row != null ? row.get(fieldCode) : null;
                String text = raw != null ? String.valueOf(raw) : "";
                SemanticFieldCheckEvaluator.SemanticFieldCheckResult detail =
                        fieldCheckEvaluator.evaluateWithDetail(text, check);
                boolean subMet = detail.isSubConditionMet();
                checkTriggered.put(fieldCode, subMet);
                checkResults.put(fieldCode, detail.getMatchResult());
                log.debug("语义子条件: field={}, checkMode={}, subMet={}, layer={}",
                        fieldCode, check.getCheckMode(), subMet,
                        detail.getMatchResult() != null ? detail.getMatchResult().getLayer() : "none");
                if (!subMet) {
                    groupMatched = false;
                }
            }
            if (groupMatched) {
                anyGroupMatched = true;
                break;
            }
        }

        return SemanticGroupMatchResult.builder()
                .triggered(anyGroupMatched)
                .checkTriggered(checkTriggered)
                .checkResults(checkResults)
                .build();
    }

    public SemanticGroupMatchResult evaluateSampleRow(Map<String, Object> sampleRow, SemanticRuleConfig config) {
        return evaluate(sampleRow, config);
    }
}
