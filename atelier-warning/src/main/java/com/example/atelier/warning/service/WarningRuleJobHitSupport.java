package com.example.atelier.warning.service;

import com.example.atelier.domain.warning.WarningRulePreviewResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class WarningRuleJobHitSupport {

    private WarningRuleJobHitSupport() {
    }

    static List<Map<String, Object>> filterMatchedRows(WarningRulePreviewResult result) {
        if (result == null || result.getRows() == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> row : result.getRows()) {
            if (isTriggered(row)) {
                matched.add(row);
            }
        }
        return matched;
    }

    private static boolean isTriggered(Map<String, Object> row) {
        Object triggered = row.get(WarningRulePreviewService.TRIGGERED_FIELD);
        if (triggered instanceof Boolean) {
            return (Boolean) triggered;
        }
        return Boolean.TRUE.equals(triggered);
    }
}
