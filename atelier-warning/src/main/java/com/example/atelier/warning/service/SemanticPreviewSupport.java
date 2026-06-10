package com.example.atelier.warning.service;

import com.example.atelier.domain.warning.SemanticFieldCheck;
import com.example.atelier.domain.warning.SemanticGroupMatchResult;
import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import com.example.atelier.warning.evaluator.SemanticRuleConfigSupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class SemanticPreviewSupport {

    static final String SEMANTIC_CHECK_PREFIX = "_semanticCheck.";
    static final String MATCH_REASON_PREFIX = "_matchReason.";
    static final String MATCH_LAYER_PREFIX = "_matchLayer.";
    static final String LLM_INVOKED_PREFIX = "_llmInvoked.";

    private SemanticPreviewSupport() {
    }

    static void enrichRow(Map<String, Object> enriched, SemanticGroupMatchResult groupResult) {
        enriched.put(WarningRulePreviewService.SEMANTIC_TRIGGERED_FIELD, groupResult.isTriggered());
        for (Map.Entry<String, Boolean> entry : groupResult.getCheckTriggered().entrySet()) {
            String field = entry.getKey();
            enriched.put(SEMANTIC_CHECK_PREFIX + field, entry.getValue());
        }
        for (Map.Entry<String, SemanticMatchResult> entry : groupResult.getCheckResults().entrySet()) {
            String field = entry.getKey();
            SemanticMatchResult result = entry.getValue();
            if (result.getReason() != null) {
                enriched.put(MATCH_REASON_PREFIX + field, result.getReason());
            }
            enriched.put(MATCH_LAYER_PREFIX + field, result.getLayer());
            enriched.put(LLM_INVOKED_PREFIX + field, result.isLlmInvoked());
        }
        SemanticMatchResult summary = summarize(groupResult);
        enriched.put(WarningRulePreviewService.MATCH_REASON_FIELD, summary.getReason());
        enriched.put(WarningRulePreviewService.MATCH_LAYER_FIELD, summary.getLayer());
        enriched.put(WarningRulePreviewService.LLM_INVOKED_FIELD, summary.isLlmInvoked());
    }

    static void putHeaders(Map<String, String> headers, SemanticRuleConfig semantic) {
        headers.put(WarningRulePreviewService.SEMANTIC_TRIGGERED_FIELD, "语义触发");
        for (SemanticFieldCheck check : SemanticRuleConfigSupport.flattenChecks(semantic)) {
            String field = check.getFieldCode();
            String label = field;
            headers.put(SEMANTIC_CHECK_PREFIX + field, "语义·" + label);
            headers.put(MATCH_REASON_PREFIX + field, "原因·" + label);
            headers.put(MATCH_LAYER_PREFIX + field, "层·" + label);
            headers.put(LLM_INVOKED_PREFIX + field, "LLM·" + label);
        }
        headers.put(WarningRulePreviewService.MATCH_REASON_FIELD, "语义原因");
        headers.put(WarningRulePreviewService.MATCH_LAYER_FIELD, "语义层");
        headers.put(WarningRulePreviewService.LLM_INVOKED_FIELD, "LLM调用");
    }

    static String summarizeSemantic(SemanticRuleConfig semantic) {
        List<SemanticFieldCheck> checks = SemanticRuleConfigSupport.flattenChecks(semantic);
        if (checks.isEmpty()) {
            return "语义合规";
        }
        return checks.stream()
                .map(check -> check.getFieldCode() + "·语义")
                .collect(Collectors.joining(" 且 "));
    }

    private static SemanticMatchResult summarize(SemanticGroupMatchResult groupResult) {
        if (!groupResult.isTriggered()) {
            return SemanticMatchResult.builder().triggered(false).layer("none").build();
        }
        for (Map.Entry<String, SemanticMatchResult> entry : groupResult.getCheckResults().entrySet()) {
            Boolean sub = groupResult.getCheckTriggered().get(entry.getKey());
            if (Boolean.TRUE.equals(sub) && entry.getValue() != null && entry.getValue().getReason() != null) {
                return entry.getValue();
            }
        }
        return SemanticMatchResult.builder().triggered(true).layer("composite").reason("语义条件满足").build();
    }
}
