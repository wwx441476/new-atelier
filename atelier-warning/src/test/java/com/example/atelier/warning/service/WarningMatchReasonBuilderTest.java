package com.example.atelier.warning.service;

import com.example.atelier.domain.warning.SemanticGroupMatchResult;
import com.example.atelier.domain.warning.SemanticMatchResult;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class WarningMatchReasonBuilderTest {

    @Test
    public void buildMetricReason_shouldIncludeExpressionAndValues() {
        Map<String, Object> context = new HashMap<>();
        context.put("profit", 200);

        String reason = WarningMatchReasonBuilder.buildMetricReason("profit < 500", context);

        Assert.assertEquals("满足指标条件：profit < 500（profit=200）", reason);
    }

    @Test
    public void buildSemanticSummaryReason_shouldJoinAllTriggeredFields() {
        Map<String, Boolean> triggered = new LinkedHashMap<>();
        triggered.put("remark", true);
        triggered.put("project_name", true);

        Map<String, SemanticMatchResult> results = new LinkedHashMap<>();
        results.put("remark", SemanticMatchResult.builder()
                .triggered(true)
                .reason("匹配关键词：茅台")
                .layer("keyword")
                .build());
        results.put("project_name", SemanticMatchResult.builder()
                .triggered(true)
                .reason("匹配关键词：学杂费")
                .layer("keyword")
                .build());

        SemanticGroupMatchResult groupResult = SemanticGroupMatchResult.builder()
                .triggered(true)
                .checkTriggered(triggered)
                .checkResults(results)
                .build();

        String reason = WarningMatchReasonBuilder.buildSemanticSummaryReason(groupResult);

        Assert.assertEquals("匹配关键词：茅台；匹配关键词：学杂费", reason);
    }

    @Test
    public void applyCompositeMatchReason_shouldCombineMetricAndSemanticParts() {
        Map<String, Object> enriched = new LinkedHashMap<>();
        Map<String, Object> metricContext = Collections.singletonMap("profit", 200);
        SemanticGroupMatchResult groupResult = SemanticGroupMatchResult.builder()
                .triggered(true)
                .checkTriggered(Collections.singletonMap("remark", true))
                .checkResults(Collections.singletonMap("remark", SemanticMatchResult.builder()
                        .triggered(true)
                        .reason("匹配关键词：茅台")
                        .layer("keyword")
                        .build()))
                .build();

        WarningMatchReasonBuilder.applyCompositeMatchReason(
                enriched,
                true,
                "profit < 500",
                metricContext,
                groupResult);

        Assert.assertTrue(String.valueOf(enriched.get("_matchReason")).contains("满足指标条件：profit < 500"));
        Assert.assertTrue(String.valueOf(enriched.get("_matchReason")).contains("匹配关键词：茅台"));
    }

    @Test
    public void applyCompositeMatchReason_metricOnly_shouldKeepMetricReason() {
        Map<String, Object> enriched = new LinkedHashMap<>();
        enriched.put("_matchReason", "匹配关键词：茅台");

        WarningMatchReasonBuilder.applyCompositeMatchReason(
                enriched,
                true,
                "profit < 500",
                Collections.singletonMap("profit", 200),
                SemanticGroupMatchResult.builder()
                        .triggered(false)
                        .checkTriggered(Collections.emptyMap())
                        .checkResults(Collections.emptyMap())
                        .build());

        Assert.assertEquals("满足指标条件：profit < 500（profit=200）", enriched.get("_matchReason"));
    }
}
