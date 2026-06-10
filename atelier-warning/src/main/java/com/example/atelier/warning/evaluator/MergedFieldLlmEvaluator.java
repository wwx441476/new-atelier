package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.domain.warning.SemanticCheckMode;
import com.example.atelier.domain.warning.SemanticFieldCheck;
import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import com.example.atelier.infra.exception.AtelierException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 将同一行内多个待 LLM 判定的字段合并为一次请求。
 */
public class MergedFieldLlmEvaluator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SYSTEM_PROMPT =
            "你是文本合规检测助手。用户会给出多个字段的检测任务。"
                    + "仅返回 JSON，格式为 {\"results\":[{\"field\":\"字段名\",\"triggered\":boolean,\"reason\":\"...\"},...]}。"
                    + "VIOLATION 任务：triggered=true 表示文本违反策略。"
                    + "REQUIREMENT 任务：triggered=true 表示文本符合策略。";

    private final LlmChatClient chatClient = new LlmChatClient();
    private final SemanticLlmResultCache cache = SemanticLlmResultCache.getInstance();

    Map<String, SemanticFieldCheckEvaluator.SemanticFieldCheckResult> evaluate(
            List<PendingFieldCheck> pending,
            SemanticLlmConfig llmConfig) {
        Map<String, SemanticFieldCheckEvaluator.SemanticFieldCheckResult> results = new LinkedHashMap<>();
        if (pending == null || pending.isEmpty()) {
            return results;
        }

        List<PendingFieldCheck> uncached = new ArrayList<>();
        for (PendingFieldCheck item : pending) {
            Optional<SemanticMatchResult> cached = cache.get(
                    item.getCheckMode().name(), item.getPolicy(), item.getText());
            if (cached.isPresent()) {
                SemanticMatchResult match = cached.get();
                results.put(item.getFieldCode(),
                        toFieldResult(item.getCheckMode(), match));
            } else {
                uncached.add(item);
            }
        }

        if (uncached.isEmpty()) {
            return results;
        }

        if (uncached.size() == 1) {
            PendingFieldCheck item = uncached.get(0);
            SemanticFieldCheckEvaluator evaluator = new SemanticFieldCheckEvaluator(llmConfig);
            SemanticFieldCheckEvaluator.SemanticFieldCheckResult detail =
                    evaluator.evaluateWithLlm(item.getText(), item.getCheck());
            cache.put(item.getCheckMode().name(), item.getPolicy(), item.getText(), detail.getMatchResult());
            results.put(item.getFieldCode(), detail);
            return results;
        }

        Map<String, SemanticMatchResult> batchResults = invokeBatch(uncached, llmConfig);
        for (PendingFieldCheck item : uncached) {
            SemanticMatchResult match = batchResults.get(item.getFieldCode());
            if (match == null) {
                match = SemanticMatchResult.builder()
                        .triggered(false)
                        .layer("llm")
                        .llmInvoked(true)
                        .reason("LLM 未返回该字段结果")
                        .build();
            }
            cache.put(item.getCheckMode().name(), item.getPolicy(), item.getText(), match);
            results.put(item.getFieldCode(), toFieldResult(item.getCheckMode(), match));
        }
        return results;
    }

    private Map<String, SemanticMatchResult> invokeBatch(List<PendingFieldCheck> pending,
                                                         SemanticLlmConfig llmConfig) {
        StringBuilder userPrompt = new StringBuilder("请依次判定以下字段：\n");
        for (int i = 0; i < pending.size(); i++) {
            PendingFieldCheck item = pending.get(i);
            userPrompt.append(i + 1).append(". field=").append(item.getFieldCode())
                    .append(", mode=").append(item.getCheckMode().name())
                    .append(", policy=").append(item.getPolicy())
                    .append(", text=").append(item.getText())
                    .append('\n');
        }
        String content = chatClient.chat(llmConfig, SYSTEM_PROMPT, userPrompt.toString());
        return parseBatchResponse(content, pending);
    }

    private Map<String, SemanticMatchResult> parseBatchResponse(String content, List<PendingFieldCheck> pending) {
        try {
            JsonNode root = MAPPER.readTree(LlmChatClient.extractJsonObject(content));
            JsonNode resultsNode = root.path("results");
            Map<String, SemanticMatchResult> parsed = new LinkedHashMap<>();
            if (resultsNode.isArray()) {
                for (JsonNode node : resultsNode) {
                    String field = node.path("field").asText("");
                    if (field.isEmpty()) {
                        continue;
                    }
                    boolean triggered = node.path("triggered").asBoolean(false);
                    String reason = node.path("reason").asText("");
                    parsed.put(field, SemanticMatchResult.builder()
                            .triggered(triggered)
                            .layer("llm")
                            .reason(reason.isEmpty() ? null : reason)
                            .llmInvoked(true)
                            .build());
                }
            }
            if (parsed.isEmpty()) {
                throw new AtelierException("LLM 批量响应无 results");
            }
            return parsed;
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("LLM 批量响应解析失败: " + e.getMessage(), e);
        }
    }

    private static SemanticFieldCheckEvaluator.SemanticFieldCheckResult toFieldResult(
            SemanticCheckMode mode,
            SemanticMatchResult match) {
        boolean subMet = match.isTriggered();
        return SemanticFieldCheckEvaluator.SemanticFieldCheckResult.of(subMet, match);
    }

    static final class PendingFieldCheck {
        private final String fieldCode;
        private final String text;
        private final SemanticFieldCheck check;
        private final SemanticCheckMode checkMode;
        private final String policy;

        PendingFieldCheck(String fieldCode, String text, SemanticFieldCheck check) {
            this.fieldCode = fieldCode;
            this.text = text;
            this.check = check;
            this.checkMode = check.getCheckMode() != null
                    ? check.getCheckMode()
                    : SemanticCheckMode.VIOLATION;
            SemanticRuleConfig probe = SemanticRuleConfigSupport.toProbeConfig(check);
            this.policy = probe.getPolicy() != null ? probe.getPolicy().trim() : "";
        }

        String getFieldCode() {
            return fieldCode;
        }

        String getText() {
            return text;
        }

        SemanticFieldCheck getCheck() {
            return check;
        }

        SemanticCheckMode getCheckMode() {
            return checkMode;
        }

        String getPolicy() {
            return policy;
        }
    }
}
