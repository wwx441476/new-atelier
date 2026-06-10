package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 混合语义匹配：词库优先，未命中时可选 LLM。
 */
public class HybridSemanticMatcher implements SemanticMatcher {

    private static final Logger log = LoggerFactory.getLogger(HybridSemanticMatcher.class);

    private final KeywordSemanticMatcher keywordMatcher = new KeywordSemanticMatcher();
    private final SemanticLlmConfig llmConfig;

    public HybridSemanticMatcher(SemanticLlmConfig llmConfig) {
        this.llmConfig = llmConfig != null ? llmConfig : SemanticLlmConfig.builder().enabled(false).build();
    }

    @Override
    public SemanticMatchResult match(String text, SemanticRuleConfig config) {
        String textPreview = SemanticLogSupport.previewText(text);
        SemanticMatchResult keywordResult = keywordMatcher.match(text, config);
        if (keywordResult.isTriggered()) {
            log.info("语义判定: 词库命中, 跳过 LLM, layer=keyword, reason={}, text=\"{}\"",
                    keywordResult.getReason(), textPreview);
            return keywordResult;
        }
        String mode = config != null && config.getMatchMode() != null
                ? config.getMatchMode().trim().toUpperCase()
                : "HYBRID";
        if ("KEYWORD".equals(mode)) {
            log.info("语义判定: 词库未命中, matchMode=KEYWORD, 未调用 LLM, text=\"{}\"", textPreview);
            return keywordResult;
        }
        if (!isLlmAvailable()) {
            log.info("语义判定: 词库未命中, LLM 未启用或未配置 API Key, matchMode={}, 未调用 LLM, text=\"{}\"",
                    mode, textPreview);
            return keywordResult;
        }
        if ("LLM".equals(mode) || "HYBRID".equals(mode)) {
            log.info("语义判定: 词库未命中, 调用 LLM, matchMode={}, provider={}, text=\"{}\"",
                    mode, llmConfig.getProvider(), textPreview);
            SemanticMatchResult llmResult = new LlmSemanticMatcher(llmConfig).match(text, config);
            log.info("语义判定: LLM 返回, triggered={}, layer={}, llmInvoked={}, reason={}, text=\"{}\"",
                    llmResult.isTriggered(), llmResult.getLayer(), llmResult.isLlmInvoked(),
                    llmResult.getReason(), textPreview);
            return llmResult;
        }
        log.info("语义判定: 词库未命中, 未知 matchMode={}, 未调用 LLM, text=\"{}\"", mode, textPreview);
        return keywordResult;
    }

    private boolean isLlmAvailable() {
        return llmConfig.isEnabled()
                && llmConfig.getApiKey() != null
                && !llmConfig.getApiKey().trim().isEmpty();
    }
}
