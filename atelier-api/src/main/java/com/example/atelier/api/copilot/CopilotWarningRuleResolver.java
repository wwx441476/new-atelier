package com.example.atelier.api.copilot;

import com.example.atelier.domain.copilot.CopilotChatMessage;
import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.warning.spi.WarningRuleService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class CopilotWarningRuleResolver {

    private final WarningRuleService warningRuleService;

    public CopilotWarningRuleResolver(WarningRuleService warningRuleService) {
        this.warningRuleService = warningRuleService;
    }

    public boolean hasIdentifier(JsonNode params) {
        return firstNonBlank(
                text(params, "ruleId"),
                text(params, "ruleCode"),
                text(params, "ruleName"),
                text(params, "name")) != null;
    }

    public WarningRule resolve(JsonNode params) {
        String ruleId = text(params, "ruleId");
        if (ruleId != null) {
            return warningRuleService.getRule(ruleId)
                    .orElseThrow(() -> new AtelierException("预警规则不存在: " + ruleId));
        }
        String ruleCode = text(params, "ruleCode");
        if (ruleCode != null) {
            Optional<WarningRule> byCode = warningRuleService.getByCode(ruleCode);
            if (byCode.isPresent()) {
                return byCode.get();
            }
            return findByName(ruleCode)
                    .orElseThrow(() -> new AtelierException("预警规则不存在: " + ruleCode));
        }
        String ruleName = firstNonBlank(text(params, "ruleName"), text(params, "name"));
        if (ruleName != null) {
            return findByName(ruleName)
                    .orElseThrow(() -> new AtelierException("未找到名称匹配的预警规则: " + ruleName));
        }
        String conversationHint = text(params, "_conversationHint");
        if (conversationHint != null) {
            return findFromConversation(conversationHint)
                    .orElseThrow(() -> new AtelierException(
                            "无法从对话中确定要执行的预警规则，请提供 ruleName 或 ruleCode。"
                                    + summarizeAvailableRules()));
        }
        throw new AtelierException(
                "run_warning_rule 需要 ruleId、ruleCode 或 ruleName。"
                        + summarizeAvailableRules());
    }

    public String buildConversationHint(List<CopilotChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (CopilotChatMessage message : messages) {
            if (message.getContent() == null || message.getContent().trim().isEmpty()) {
                continue;
            }
            builder.append(message.getRole()).append(": ")
                    .append(message.getContent().trim()).append('\n');
        }
        return builder.toString().trim();
    }

    private Optional<WarningRule> findByName(String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return Optional.empty();
        }
        List<WarningRule> rules = warningRuleService.listRules();
        for (WarningRule rule : rules) {
            if (normalize(rule.getName()).equals(normalizedQuery)) {
                return Optional.of(rule);
            }
        }
        for (WarningRule rule : rules) {
            if (normalize(rule.getCode()).equals(normalizedQuery)) {
                return Optional.of(rule);
            }
        }
        List<ScoredRule> partialMatches = new ArrayList<>();
        for (WarningRule rule : rules) {
            int score = scoreNameMatch(normalizedQuery, rule);
            if (score > 0) {
                partialMatches.add(new ScoredRule(rule, score));
            }
        }
        partialMatches.sort(Comparator.comparingInt((ScoredRule item) -> item.score).reversed());
        if (partialMatches.isEmpty()) {
            return Optional.empty();
        }
        if (partialMatches.size() == 1 || partialMatches.get(0).score > partialMatches.get(1).score) {
            return Optional.of(partialMatches.get(0).rule);
        }
        return Optional.empty();
    }

    private Optional<WarningRule> findFromConversation(String conversation) {
        String normalizedConversation = normalize(conversation);
        if (normalizedConversation.isEmpty()) {
            return Optional.empty();
        }
        List<ScoredRule> matches = new ArrayList<>();
        for (WarningRule rule : warningRuleService.listRules()) {
            int score = 0;
            String name = normalize(rule.getName());
            String code = normalize(rule.getCode());
            if (!name.isEmpty() && normalizedConversation.contains(name)) {
                score = Math.max(score, name.length() * 2);
            }
            if (!code.isEmpty() && normalizedConversation.contains(code)) {
                score = Math.max(score, code.length());
            }
            if (score > 0) {
                matches.add(new ScoredRule(rule, score));
            }
        }
        matches.sort(Comparator.comparingInt((ScoredRule item) -> item.score).reversed());
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() == 1 || matches.get(0).score > matches.get(1).score) {
            return Optional.of(matches.get(0).rule);
        }
        return Optional.empty();
    }

    private int scoreNameMatch(String normalizedQuery, WarningRule rule) {
        String name = normalize(rule.getName());
        if (name.isEmpty()) {
            return 0;
        }
        if (name.equals(normalizedQuery)) {
            return normalizedQuery.length() * 4;
        }
        if (name.contains(normalizedQuery) || normalizedQuery.contains(name)) {
            return Math.min(name.length(), normalizedQuery.length()) * 2;
        }
        return 0;
    }

    private String summarizeAvailableRules() {
        List<WarningRule> rules = warningRuleService.listRules();
        if (rules.isEmpty()) {
            return " 当前工作区暂无预警规则。";
        }
        String summary = rules.stream()
                .map(rule -> rule.getName() + "(" + rule.getCode() + ")")
                .collect(Collectors.joining("、"));
        return " 可选规则：" + summary;
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText("").trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static final class ScoredRule {
        private final WarningRule rule;
        private final int score;

        private ScoredRule(WarningRule rule, int score) {
            this.rule = rule;
            this.score = score;
        }
    }
}
