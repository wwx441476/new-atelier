package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.domain.warning.SemanticCheckMode;
import com.example.atelier.domain.warning.SemanticFieldCheck;
import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;

import java.util.Optional;

/**
 * 单字段语义子条件求值（支持 VIOLATION / REQUIREMENT）。
 */
public class SemanticFieldCheckEvaluator {

    private final KeywordSemanticMatcher keywordMatcher = new KeywordSemanticMatcher();
    private final SemanticLlmConfig llmConfig;
    private final SemanticEvaluationOptions options;

    public SemanticFieldCheckEvaluator(SemanticLlmConfig llmConfig) {
        this(llmConfig, SemanticEvaluationOptions.defaults());
    }

    public SemanticFieldCheckEvaluator(SemanticLlmConfig llmConfig, SemanticEvaluationOptions options) {
        this.llmConfig = llmConfig != null ? llmConfig : SemanticLlmConfig.builder().enabled(false).build();
        this.options = options != null ? options : SemanticEvaluationOptions.defaults();
    }

    /**
     * @return 子条件是否满足（VIOLATION=违规，REQUIREMENT=符合）
     */
    public boolean evaluateSubCondition(String text, SemanticFieldCheck check) {
        return evaluateWithDetail(text, check).isSubConditionMet();
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
        Optional<SemanticFieldCheckResult> keywordPhase = tryKeywordPhase(text, check);
        if (keywordPhase.isPresent()) {
            return keywordPhase.get();
        }
        return evaluateWithLlm(text, check);
    }

    /**
     * 词库阶段：若已能判定则返回结果，否则 empty 表示需要 LLM。
     */
    Optional<SemanticFieldCheckResult> tryKeywordPhase(String text, SemanticFieldCheck check) {
        if (check == null || text == null || text.trim().isEmpty()) {
            return Optional.of(SemanticFieldCheckResult.empty());
        }
        SemanticCheckMode mode = check.getCheckMode() != null
                ? check.getCheckMode()
                : SemanticCheckMode.VIOLATION;
        SemanticRuleConfig probe = SemanticRuleConfigSupport.toProbeConfig(check);
        String matchMode = resolveMatchMode(probe.getMatchMode());

        if ("LLM".equals(matchMode)) {
            if (options.isKeywordOnly() || !isLlmAvailable()) {
                return Optional.of(SemanticFieldCheckResult.empty());
            }
            return Optional.empty();
        }

        SemanticMatchResult keywordResult = keywordMatcher.match(text, probe);
        if (mode == SemanticCheckMode.VIOLATION) {
            if (keywordResult.isTriggered()) {
                return Optional.of(SemanticFieldCheckResult.of(true, keywordResult));
            }
            if ("KEYWORD".equals(matchMode) || options.isKeywordOnly() || !isLlmAvailable()) {
                return Optional.of(SemanticFieldCheckResult.of(false, keywordResult));
            }
            return Optional.empty();
        }

        if (keywordResult.isTriggered()) {
            return Optional.of(SemanticFieldCheckResult.of(true, keywordResult));
        }
        if ("KEYWORD".equals(matchMode) || options.isKeywordOnly() || !isLlmAvailable()) {
            return Optional.of(SemanticFieldCheckResult.of(false, keywordResult));
        }
        return Optional.empty();
    }

    SemanticFieldCheckResult evaluateWithLlm(String text, SemanticFieldCheck check) {
        SemanticCheckMode mode = check.getCheckMode() != null
                ? check.getCheckMode()
                : SemanticCheckMode.VIOLATION;
        SemanticRuleConfig probe = SemanticRuleConfigSupport.toProbeConfig(check);
        String matchMode = resolveMatchMode(probe.getMatchMode());

        if (mode == SemanticCheckMode.VIOLATION) {
            if ("LLM".equals(matchMode) || "HYBRID".equals(matchMode)) {
                if (!isLlmAvailable()) {
                    throw new com.example.atelier.infra.exception.AtelierException("LLM 未配置");
                }
                SemanticMatchResult raw = new LlmSemanticMatcher(llmConfig).match(text, probe);
                return SemanticFieldCheckResult.of(raw.isTriggered(), raw);
            }
            SemanticMatchResult raw = keywordMatcher.match(text, probe);
            return SemanticFieldCheckResult.of(raw.isTriggered(), raw);
        }

        if ("LLM".equals(matchMode) || "HYBRID".equals(matchMode)) {
            if (!isLlmAvailable()) {
                return SemanticFieldCheckResult.empty();
            }
            SemanticMatchResult raw = new RequirementLlmSemanticMatcher(llmConfig).match(text, probe);
            return SemanticFieldCheckResult.of(raw.isTriggered(), raw);
        }
        SemanticMatchResult raw = keywordMatcher.match(text, probe);
        return SemanticFieldCheckResult.of(raw.isTriggered(), raw);
    }

    private String resolveMatchMode(String matchMode) {
        if (options.isKeywordOnly()) {
            return "KEYWORD";
        }
        return matchMode != null ? matchMode.trim().toUpperCase() : "HYBRID";
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
