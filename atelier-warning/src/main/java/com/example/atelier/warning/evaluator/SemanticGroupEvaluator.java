package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.domain.warning.SemanticCheckGroup;
import com.example.atelier.domain.warning.SemanticFieldCheck;
import com.example.atelier.domain.warning.SemanticGroupMatchResult;
import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 多字段语义条件组求值。
 */
public class SemanticGroupEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SemanticGroupEvaluator.class);

    private final SemanticFieldCheckEvaluator fieldCheckEvaluator;
    private final MergedFieldLlmEvaluator mergedLlmEvaluator;
    private final SemanticLlmConfig llmConfig;
    private final SemanticEvaluationOptions options;

    public SemanticGroupEvaluator(SemanticLlmConfig llmConfig) {
        this(llmConfig, SemanticEvaluationOptions.defaults());
    }

    public SemanticGroupEvaluator(SemanticLlmConfig llmConfig, SemanticEvaluationOptions options) {
        this.llmConfig = llmConfig != null ? llmConfig : SemanticLlmConfig.builder().enabled(false).build();
        this.options = options != null ? options : SemanticEvaluationOptions.defaults();
        this.fieldCheckEvaluator = new SemanticFieldCheckEvaluator(this.llmConfig, this.options);
        this.mergedLlmEvaluator = new MergedFieldLlmEvaluator();
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
            boolean groupMatched = evaluateGroup(row, group, checkTriggered, checkResults);
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

    private boolean evaluateGroup(Map<String, Object> row,
                                  SemanticCheckGroup group,
                                  Map<String, Boolean> checkTriggered,
                                  Map<String, SemanticMatchResult> checkResults) {
        List<MergedFieldLlmEvaluator.PendingFieldCheck> pendingLlm = new ArrayList<>();
        boolean groupMatched = true;

        for (SemanticFieldCheck check : group.getChecks()) {
            String fieldCode = check.getFieldCode();
            Object raw = row != null ? row.get(fieldCode) : null;
            String text = raw != null ? String.valueOf(raw) : "";

            Optional<SemanticFieldCheckEvaluator.SemanticFieldCheckResult> keywordPhase =
                    fieldCheckEvaluator.tryKeywordPhase(text, check);
            if (keywordPhase.isPresent()) {
                SemanticFieldCheckEvaluator.SemanticFieldCheckResult detail = keywordPhase.get();
                recordCheck(fieldCode, detail, checkTriggered, checkResults);
                if (!detail.isSubConditionMet()) {
                    groupMatched = false;
                    break;
                }
                continue;
            }

            pendingLlm.add(new MergedFieldLlmEvaluator.PendingFieldCheck(fieldCode, text, check));
        }

        if (!groupMatched || pendingLlm.isEmpty()) {
            return groupMatched;
        }

        Map<String, SemanticFieldCheckEvaluator.SemanticFieldCheckResult> llmResults =
                mergedLlmEvaluator.evaluate(pendingLlm, llmConfig);
        for (MergedFieldLlmEvaluator.PendingFieldCheck pending : pendingLlm) {
            String fieldCode = pending.getFieldCode();
            SemanticFieldCheckEvaluator.SemanticFieldCheckResult detail =
                    llmResults.getOrDefault(fieldCode, SemanticFieldCheckEvaluator.SemanticFieldCheckResult.empty());
            recordCheck(fieldCode, detail, checkTriggered, checkResults);
            log.debug("语义子条件(LLM): field={}, subMet={}, layer={}",
                    fieldCode, detail.isSubConditionMet(),
                    detail.getMatchResult() != null ? detail.getMatchResult().getLayer() : "none");
            if (!detail.isSubConditionMet()) {
                return false;
            }
        }
        return true;
    }

    private void recordCheck(String fieldCode,
                             SemanticFieldCheckEvaluator.SemanticFieldCheckResult detail,
                             Map<String, Boolean> checkTriggered,
                             Map<String, SemanticMatchResult> checkResults) {
        boolean subMet = detail.isSubConditionMet();
        checkTriggered.put(fieldCode, subMet);
        checkResults.put(fieldCode, detail.getMatchResult());
        log.debug("语义子条件: field={}, subMet={}, layer={}",
                fieldCode, subMet,
                detail.getMatchResult() != null ? detail.getMatchResult().getLayer() : "none");
    }

    public SemanticGroupMatchResult evaluateSampleRow(Map<String, Object> sampleRow, SemanticRuleConfig config) {
        return evaluate(sampleRow, config);
    }
}
