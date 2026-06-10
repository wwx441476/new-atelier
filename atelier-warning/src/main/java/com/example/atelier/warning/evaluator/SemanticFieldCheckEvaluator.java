package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.domain.warning.SemanticCheckMode;
import com.example.atelier.domain.warning.SemanticFieldCheck;
import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;

/**
 * 单字段语义子条件求值（支持 VIOLATION / REQUIREMENT）。
 */
public class SemanticFieldCheckEvaluator {

    private final KeywordSemanticMatcher keywordMatcher = new KeywordSemanticMatcher();
    private final SemanticLlmConfig llmConfig;

    public SemanticFieldCheckEvaluator(SemanticLlmConfig llmConfig) {
        this.llmConfig = llmConfig != null ? llmConfig : SemanticLlmConfig.builder().enabled(false).build();
    }

    /**
     * @return 子条件是否满足（VIOLATION=违规，REQUIREMENT=符合）
     */
    public boolean evaluateSubCondition(String text, SemanticFieldCheck check) {
        SemanticFieldCheckResult result = evaluateWithDetail(text, check);
        return result.isSubConditionMet();
    }

    public SemanticMatchResult evaluateMatchResult(String text, SemanticFieldCheck check) {
        return evaluateWithDetail(text, check).getMatchResult();
    }

    SemanticFieldCheckResult evaluateWithDetail(String text, SemanticFieldCheck check) {
        if (check == null) {
            return SemanticFieldCheckResult.empty();
        }
        if (text == null || text.trim().isEmpty()) {
            return SemanticFieldCheckResult.empty();
        }
        SemanticCheckMode mode = check.getCheckMode() != null
                ? check.getCheckMode()
                : SemanticCheckMode.VIOLATION;
        SemanticRuleConfig probe = SemanticRuleConfigSupport.toProbeConfig(check);
        String matchMode = probe.getMatchMode() != null ? probe.getMatchMode().trim().toUpperCase() : "HYBRID";

        if (mode == SemanticCheckMode.VIOLATION) {
            SemanticMatchResult raw = matchViolation(text, probe, matchMode);
            return SemanticFieldCheckResult.of(raw.isTriggered(), raw);
        }
        return evaluateRequirement(text, probe, matchMode);
    }

    private SemanticFieldCheckResult evaluateRequirement(String text, SemanticRuleConfig probe, String matchMode) {
        if ("KEYWORD".equals(matchMode)) {
            SemanticMatchResult raw = keywordMatcher.match(text, probe);
            return SemanticFieldCheckResult.of(raw.isTriggered(), raw);
        }
        if ("LLM".equals(matchMode)) {
            if (!isLlmAvailable()) {
                return SemanticFieldCheckResult.empty();
            }
            SemanticMatchResult raw = new RequirementLlmSemanticMatcher(llmConfig).match(text, probe);
            return SemanticFieldCheckResult.of(raw.isTriggered(), raw);
        }
        SemanticMatchResult keywordResult = keywordMatcher.match(text, probe);
        if (keywordResult.isTriggered()) {
            return SemanticFieldCheckResult.of(true, keywordResult);
        }
        if (!isLlmAvailable()) {
            return SemanticFieldCheckResult.of(false, keywordResult);
        }
        SemanticMatchResult llmResult = new RequirementLlmSemanticMatcher(llmConfig).match(text, probe);
        return SemanticFieldCheckResult.of(llmResult.isTriggered(), llmResult);
    }

    private SemanticMatchResult matchViolation(String text, SemanticRuleConfig probe, String matchMode) {
        if ("LLM".equals(matchMode)) {
            if (!isLlmAvailable()) {
                throw new com.example.atelier.infra.exception.AtelierException("LLM 未配置");
            }
            return new LlmSemanticMatcher(llmConfig).match(text, probe);
        }
        return new HybridSemanticMatcher(llmConfig).match(text, probe);
    }

    private boolean isLlmAvailable() {
        return llmConfig.isEnabled()
                && llmConfig.getApiKey() != null
                && !llmConfig.getApiKey().trim().isEmpty();
    }

    static final class SemanticFieldCheckResult {
        private final boolean subConditionMet;
        private final SemanticMatchResult matchResult;

        private SemanticFieldCheckResult(boolean subConditionMet, SemanticMatchResult matchResult) {
            this.subConditionMet = subConditionMet;
            this.matchResult = matchResult;
        }

        static SemanticFieldCheckResult of(boolean subConditionMet, SemanticMatchResult matchResult) {
            return new SemanticFieldCheckResult(subConditionMet, matchResult);
        }

        static SemanticFieldCheckResult empty() {
            return new SemanticFieldCheckResult(false, SemanticMatchResult.builder()
                    .triggered(false)
                    .layer("none")
                    .build());
        }

        boolean isSubConditionMet() {
            return subConditionMet;
        }

        SemanticMatchResult getMatchResult() {
            return matchResult;
        }
    }
}
