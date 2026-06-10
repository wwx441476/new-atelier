package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.warning.SemanticCheckGroup;
import com.example.atelier.domain.warning.SemanticCheckMode;
import com.example.atelier.domain.warning.SemanticFieldCheck;
import com.example.atelier.domain.warning.SemanticRuleConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 语义配置归一化 — 兼容单字段旧配置。
 */
public final class SemanticRuleConfigSupport {

    private SemanticRuleConfigSupport() {
    }

    public static List<SemanticCheckGroup> normalizeGroups(SemanticRuleConfig config) {
        if (config == null) {
            return Collections.emptyList();
        }
        if (config.getSemanticGroups() != null && !config.getSemanticGroups().isEmpty()) {
            return config.getSemanticGroups();
        }
        if (config.getFieldCode() != null && !config.getFieldCode().trim().isEmpty()) {
            SemanticFieldCheck check = SemanticFieldCheck.builder()
                    .fieldCode(config.getFieldCode().trim())
                    .checkMode(SemanticCheckMode.VIOLATION)
                    .policy(config.getPolicy())
                    .hintKeywords(config.getHintKeywords())
                    .matchMode(defaultMatchMode(config.getMatchMode()))
                    .expandedKeywords(config.getExpandedKeywords())
                    .build();
            return Collections.singletonList(
                    SemanticCheckGroup.builder().checks(Collections.singletonList(check)).build());
        }
        return Collections.emptyList();
    }

    public static SemanticRuleConfig toProbeConfig(SemanticFieldCheck check) {
        return SemanticRuleConfig.builder()
                .policy(check.getPolicy())
                .hintKeywords(check.getHintKeywords())
                .matchMode(defaultMatchMode(check.getMatchMode()))
                .expandedKeywords(check.getExpandedKeywords())
                .build();
    }

    public static List<SemanticFieldCheck> flattenChecks(SemanticRuleConfig config) {
        List<SemanticFieldCheck> checks = new ArrayList<>();
        for (SemanticCheckGroup group : normalizeGroups(config)) {
            if (group.getChecks() != null) {
                checks.addAll(group.getChecks());
            }
        }
        return checks;
    }

    private static String defaultMatchMode(String matchMode) {
        return matchMode == null || matchMode.trim().isEmpty() ? "HYBRID" : matchMode.trim().toUpperCase();
    }
}
