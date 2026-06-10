package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.domain.warning.SemanticCheckGroup;
import com.example.atelier.domain.warning.SemanticCheckMode;
import com.example.atelier.domain.warning.SemanticFieldCheck;
import com.example.atelier.domain.warning.SemanticGroupMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SemanticGroupEvaluatorTest {

    @Test
    public void shouldTriggerWhenViolationAndRequirementBothMet() {
        SemanticRuleConfig config = SemanticRuleConfig.builder()
                .semanticGroups(Collections.singletonList(SemanticCheckGroup.builder()
                        .checks(Arrays.asList(
                                SemanticFieldCheck.builder()
                                        .fieldCode("remark")
                                        .checkMode(SemanticCheckMode.VIOLATION)
                                        .policy("不得含烟酒")
                                        .hintKeywords(Arrays.asList("茅台"))
                                        .matchMode("KEYWORD")
                                        .build(),
                                SemanticFieldCheck.builder()
                                        .fieldCode("project_name")
                                        .checkMode(SemanticCheckMode.REQUIREMENT)
                                        .policy("学杂费类项目")
                                        .hintKeywords(Arrays.asList("学杂费"))
                                        .matchMode("KEYWORD")
                                        .build()))
                        .build()))
                .build();

        Map<String, Object> row = new HashMap<>();
        row.put("remark", "采购茅台两瓶");
        row.put("project_name", "2024春季学杂费");

        SemanticGroupMatchResult result = new SemanticGroupEvaluator(disabledLlm()).evaluate(row, config);
        Assert.assertTrue(result.isTriggered());
        Assert.assertEquals(true, result.getCheckTriggered().get("remark"));
        Assert.assertEquals(true, result.getCheckTriggered().get("project_name"));
    }

    @Test
    public void shouldNotTriggerWhenRequirementNotMet() {
        SemanticRuleConfig config = SemanticRuleConfig.builder()
                .semanticGroups(Collections.singletonList(SemanticCheckGroup.builder()
                        .checks(Arrays.asList(
                                SemanticFieldCheck.builder()
                                        .fieldCode("remark")
                                        .checkMode(SemanticCheckMode.VIOLATION)
                                        .policy("不得含烟酒")
                                        .hintKeywords(Arrays.asList("茅台"))
                                        .matchMode("KEYWORD")
                                        .build(),
                                SemanticFieldCheck.builder()
                                        .fieldCode("project_name")
                                        .checkMode(SemanticCheckMode.REQUIREMENT)
                                        .policy("学杂费类项目")
                                        .hintKeywords(Arrays.asList("学杂费"))
                                        .matchMode("KEYWORD")
                                        .build()))
                        .build()))
                .build();

        Map<String, Object> row = new HashMap<>();
        row.put("remark", "采购茅台两瓶");
        row.put("project_name", "设备采购项目");

        SemanticGroupMatchResult result = new SemanticGroupEvaluator(disabledLlm()).evaluate(row, config);
        Assert.assertFalse(result.isTriggered());
        Assert.assertEquals(true, result.getCheckTriggered().get("remark"));
        Assert.assertEquals(false, result.getCheckTriggered().get("project_name"));
    }

    @Test
    public void shouldMigrateLegacySingleFieldConfig() {
        SemanticRuleConfig legacy = SemanticRuleConfig.builder()
                .fieldCode("remark")
                .policy("不得含烟酒")
                .hintKeywords(Arrays.asList("茅台"))
                .matchMode("KEYWORD")
                .build();

        Map<String, Object> row = Collections.singletonMap("remark", "采购茅台两瓶");
        SemanticGroupMatchResult result = new SemanticGroupEvaluator(disabledLlm()).evaluate(row, legacy);
        Assert.assertTrue(result.isTriggered());
    }

    private static SemanticLlmConfig disabledLlm() {
        return SemanticLlmConfig.builder().enabled(false).build();
    }
}
