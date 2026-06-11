package com.example.atelier.warning.service;

import com.example.atelier.domain.warning.SemanticGroupMatchResult;
import com.example.atelier.domain.warning.SemanticMatchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class WarningMatchReasonBuilder {

    private WarningMatchReasonBuilder() {
    }

    static String buildMetricReason(String expression, Map<String, Object> metricContext) {
        String expr = expression != null ? expression.trim() : "";
        if (expr.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder("满足指标条件：").append(expr);
        if (metricContext != null && !metricContext.isEmpty()) {
            String values = metricContext.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + formatValue(entry.getValue()))
                    .collect(Collectors.joining(", "));
            if (!values.isEmpty()) {
                builder.append("（").append(values).append("）");
            }
        }
        return builder.toString();
    }

    static String buildSemanticSummaryReason(SemanticGroupMatchResult groupResult) {
        if (groupResult == null || !groupResult.isTriggered()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (groupResult.getCheckResults() != null) {
            for (Map.Entry<String, SemanticMatchResult> entry : groupResult.getCheckResults().entrySet()) {
                String field = entry.getKey();
                if (!Boolean.TRUE.equals(groupResult.getCheckTriggered().get(field))) {
                    continue;
                }
                SemanticMatchResult result = entry.getValue();
                if (result == null || result.getReason() == null || result.getReason().trim().isEmpty()) {
                    continue;
                }
                parts.add(result.getReason().trim());
            }
        }
        if (parts.isEmpty()) {
            return "语义条件满足";
        }
        return String.join("；", parts);
    }

    static void applyCompositeMatchReason(Map<String, Object> enriched,
                                          boolean metricTriggered,
                                          String expression,
                                          Map<String, Object> metricContext,
                                          SemanticGroupMatchResult groupResult) {
        List<String> parts = new ArrayList<>();
        if (metricTriggered) {
            String metricReason = buildMetricReason(expression, metricContext);
            if (metricReason != null) {
                parts.add(metricReason);
            }
        }
        String semanticReason = buildSemanticSummaryReason(groupResult);
        if (semanticReason != null) {
            parts.add(semanticReason);
        }
        if (parts.isEmpty()) {
            enriched.remove(WarningRulePreviewService.MATCH_REASON_FIELD);
            return;
        }
        enriched.put(WarningRulePreviewService.MATCH_REASON_FIELD, String.join("；", parts));
    }

    private static String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number) {
            double numeric = ((Number) value).doubleValue();
            if (numeric == Math.rint(numeric)) {
                return String.valueOf((long) numeric);
            }
            return String.valueOf(numeric);
        }
        return String.valueOf(value);
    }
}
