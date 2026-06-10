package com.example.atelier.warning.service;

import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import com.example.atelier.warning.evaluator.LlmChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 LLM 扩展语义合规关键词。
 */
@Service
public class KeywordExpansionService {

    private static final String SYSTEM_PROMPT =
            "你是关键词扩展助手。根据合规策略和示例词，生成相关违禁关键词列表。"
                    + "仅返回 JSON：{\"keywords\":[\"词1\",\"词2\"]}。";

    private final LlmChatClient chatClient = new LlmChatClient();

    public List<String> expandKeywords(SemanticRuleConfig config, SemanticLlmConfig llmConfig) {
        List<String> hints = config != null && config.getHintKeywords() != null
                ? config.getHintKeywords()
                : new ArrayList<>();
        if (llmConfig == null || !llmConfig.isEnabled()
                || llmConfig.getApiKey() == null || llmConfig.getApiKey().trim().isEmpty()) {
            return distinct(hints);
        }
        String policy = config != null && config.getPolicy() != null ? config.getPolicy().trim() : "";
        String hintText = hints.isEmpty() ? "无" : String.join("、", hints);
        String userPrompt = "合规策略：" + policy + "\n示例词：" + hintText
                + "\n请扩展 20 个以内相关关键词，包含品牌、俗称、常见变体。";
        String content = chatClient.chat(llmConfig, SYSTEM_PROMPT, userPrompt);
        List<String> expanded = LlmChatClient.parseKeywordList(content);
        Set<String> merged = new LinkedHashSet<>(hints);
        merged.addAll(expanded);
        return new ArrayList<>(merged);
    }

    private List<String> distinct(List<String> keywords) {
        return keywords.stream()
                .filter(k -> k != null && !k.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }
}
