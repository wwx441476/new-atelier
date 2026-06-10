package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import com.example.atelier.infra.exception.AtelierException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REQUIREMENT 模式 LLM 判定：triggered=true 表示文本符合策略。
 */
public class RequirementLlmSemanticMatcher implements SemanticMatcher {

    private static final Logger log = LoggerFactory.getLogger(RequirementLlmSemanticMatcher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SYSTEM_PROMPT =
            "你是文本分类助手。仅返回 JSON，格式为 {\"triggered\":boolean,\"reason\":\"...\"}。"
                    + "triggered=true 表示待检测文本符合策略描述。";

    private final LlmChatClient chatClient = new LlmChatClient();
    private final SemanticLlmConfig llmConfig;

    public RequirementLlmSemanticMatcher(SemanticLlmConfig llmConfig) {
        this.llmConfig = llmConfig;
    }

    @Override
    public SemanticMatchResult match(String text, SemanticRuleConfig config) {
        if (text == null || text.trim().isEmpty() || config == null) {
            return noMatch();
        }
        String policy = config.getPolicy() != null ? config.getPolicy().trim() : "";
        if (policy.isEmpty()) {
            throw new AtelierException("语义策略不能为空");
        }
        String userPrompt = "符合策略：" + policy + "\n待检测文本：" + text
                + "\n请判断文本是否符合策略。";
        log.debug("LLM REQUIREMENT 判定: policy=\"{}\", text=\"{}\"",
                SemanticLogSupport.previewText(policy), SemanticLogSupport.previewText(text));
        String content = chatClient.chat(llmConfig, SYSTEM_PROMPT, userPrompt);
        return parseResult(content);
    }

    private SemanticMatchResult parseResult(String content) {
        try {
            JsonNode root = MAPPER.readTree(LlmChatClient.extractJsonObject(content));
            boolean matched = root.path("triggered").asBoolean(false);
            String reason = root.path("reason").asText("");
            if (matched) {
                return SemanticMatchResult.builder()
                        .triggered(true)
                        .layer("llm")
                        .reason(reason.isEmpty() ? "符合策略" : reason)
                        .llmInvoked(true)
                        .build();
            }
            return SemanticMatchResult.builder()
                    .triggered(false)
                    .layer("none")
                    .reason(reason.isEmpty() ? null : reason)
                    .llmInvoked(true)
                    .build();
        } catch (Exception e) {
            throw new AtelierException("LLM 语义响应解析失败: " + e.getMessage(), e);
        }
    }

    private SemanticMatchResult noMatch() {
        return SemanticMatchResult.builder()
                .triggered(false)
                .layer("none")
                .build();
    }
}
