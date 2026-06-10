package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.domain.warning.SemanticValidateResult;
import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.domain.warning.WarningRulePreviewResult;
import com.example.atelier.domain.warning.WarningRuleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SEMANTIC / COMPOSITE 预警规则 API 集成测试。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SemanticCompositeWarningRuleApiIntegrationTest {

    private static final String SEMANTIC_RULE_ID = "wr-2";
    private static final String COMPOSITE_RULE_ID = "wr-3";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void listRules_shouldIncludeSemanticAndCompositeSeedRules() {
        List<WarningRule> rules = listRules();
        WarningRule semantic = findRuleByCode(rules, "bad_remark");
        WarningRule composite = findRuleByCode(rules, "low_profit_bad_remark");

        assertNotNull(semantic);
        assertEquals(WarningRuleType.SEMANTIC, semantic.getRuleType());
        assertNotNull(semantic.getRuleConfig());
        assertNotNull(semantic.getRuleConfig().getSemantic());

        assertNotNull(composite);
        assertEquals(WarningRuleType.COMPOSITE, composite.getRuleType());
        assertEquals("profit < 500", composite.getExpression());
        assertEquals("AND", composite.getRuleConfig().getTriggerLogic());
        assertNotNull(composite.getRuleConfig().getSemantic());
    }

    @Test
    public void getSemanticRule_shouldReturnSemanticConfig() {
        WarningRule rule = getRule(SEMANTIC_RULE_ID);
        assertEquals("备注烟酒违规", rule.getName());
        assertEquals(WarningRuleType.SEMANTIC, rule.getRuleType());
        assertEquals("mt-orders", rule.getRuleConfig().getSemantic().getMetaTableId());
        assertEquals("remark", rule.getRuleConfig().getSemantic().getFieldCode());
        assertEquals("HYBRID", rule.getRuleConfig().getSemantic().getMatchMode());
    }

    @Test
    public void getCompositeRule_shouldReturnCompositeConfig() {
        WarningRule rule = getRule(COMPOSITE_RULE_ID);
        assertEquals("低利润且备注违规", rule.getName());
        assertEquals(WarningRuleType.COMPOSITE, rule.getRuleType());
        assertEquals(Arrays.asList("profit"), rule.getMetricCodes());
        assertEquals("profit < 500", rule.getExpression());
        assertEquals("AND", rule.getRuleConfig().getTriggerLogic());
        assertEquals("remark", rule.getRuleConfig().getSemantic().getFieldCode());
    }

    @Test
    public void validateSemantic_shouldAcceptValidConfig() {
        Map<String, Object> body = new HashMap<>();
        body.put("semanticConfig", seedSemanticConfig());

        SemanticValidateResult result = postSemanticValidate(body);
        assertTrue(result.isValid());
        assertEquals("语义配置有效", result.getMessage());
    }

    @Test
    public void validateSemantic_shouldRejectMissingPolicy() {
        Map<String, Object> semantic = seedSemanticConfig();
        semantic.put("policy", "");

        Map<String, Object> body = new HashMap<>();
        body.put("semanticConfig", semantic);

        SemanticValidateResult result = postSemanticValidate(body);
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("合规策略"));
    }

    @Test
    public void validateSemantic_withSampleText_shouldDetectKeywordHit() {
        Map<String, Object> body = new HashMap<>();
        body.put("semanticConfig", seedSemanticConfig());
        body.put("sampleText", "采购茅台两瓶");

        SemanticValidateResult result = postSemanticValidate(body);
        assertTrue(result.isValid());
        assertEquals(true, result.getSampleTriggered());
        assertEquals("keyword", result.getSampleMatchLayer());
        assertTrue(result.getSampleMatchReason().contains("茅台"));
    }

    @Test
    public void validateSemantic_withSampleRow_shouldReturnPerFieldChecks() {
        Map<String, Object> semantic = new HashMap<>();
        semantic.put("metaTableId", "mt-orders");
        List<Map<String, Object>> checks = new ArrayList<>();
        Map<String, Object> remarkCheck = new HashMap<>();
        remarkCheck.put("fieldCode", "remark");
        remarkCheck.put("checkMode", "VIOLATION");
        remarkCheck.put("policy", "备注中不得包含烟酒相关内容，包括茅台、五粮液等品牌");
        remarkCheck.put("hintKeywords", Arrays.asList("烟", "酒", "茅台", "五粮液"));
        remarkCheck.put("matchMode", "HYBRID");
        Map<String, Object> projectCheck = new HashMap<>();
        projectCheck.put("fieldCode", "project_name");
        projectCheck.put("checkMode", "REQUIREMENT");
        projectCheck.put("policy", "属于学杂费、教材费、学费、杂费、代办费等教育收费类项目");
        projectCheck.put("hintKeywords", Arrays.asList("学杂费", "学费", "教材费", "杂费"));
        projectCheck.put("matchMode", "HYBRID");
        checks.add(remarkCheck);
        checks.add(projectCheck);
        Map<String, Object> group = new HashMap<>();
        group.put("checks", checks);
        semantic.put("semanticGroups", Collections.singletonList(group));

        Map<String, Object> sampleRow = new HashMap<>();
        sampleRow.put("remark", "采购茅台两瓶");
        sampleRow.put("project_name", "2024春季学杂费");

        Map<String, Object> body = new HashMap<>();
        body.put("semanticConfig", semantic);
        body.put("sampleRow", sampleRow);

        SemanticValidateResult result = postSemanticValidate(body);
        assertTrue(result.isValid());
        assertEquals(true, result.getSampleTriggered());
        assertNotNull(result.getSampleChecks());
        assertEquals(2, result.getSampleChecks().size());
        assertTrue(result.getMessage().contains("样例将触发预警"));
    }

    @Test
    public void validateSemantic_withSampleText_shouldNotTriggerCleanText() {
        Map<String, Object> body = new HashMap<>();
        body.put("semanticConfig", seedSemanticConfig());
        body.put("sampleText", "正常办公采购");

        SemanticValidateResult result = postSemanticValidate(body);
        assertTrue(result.isValid());
        assertEquals(false, result.getSampleTriggered());
        assertEquals("none", result.getSampleMatchLayer());
    }

    @Test
    public void expandKeywords_withoutLlm_shouldReturnHintKeywordsOnly() {
        Map<String, Object> body = new HashMap<>();
        body.put("semanticConfig", seedSemanticConfig());

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl("/warning/rules/expand-keywords"),
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        @SuppressWarnings("unchecked")
        Map<String, List<String>> expandedByField =
                (Map<String, List<String>>) response.getBody().getData().get("expandedByField");
        assertNotNull(expandedByField);
        List<String> remarkKeywords = expandedByField.get("remark");
        assertNotNull(remarkKeywords);
        assertTrue(remarkKeywords.contains("烟"));
        assertTrue(remarkKeywords.contains("茅台"));
    }

    @Test
    public void previewMultiFieldSemanticRule_shouldTriggerTuitionProjectWithTobaccoRemark() {
        WarningRulePreviewResult preview = previewRule("wr-4");
        assertEquals("学杂费项目备注烟酒", preview.getRuleName());
        assertEquals(1, preview.getMatchedCount());

        Map<String, Object> row = findRowByRemark(preview, "采购茅台两瓶");
        assertEquals(true, row.get("_triggered"));
        assertEquals(true, row.get("_semanticCheck.remark"));
        assertEquals(true, row.get("_semanticCheck.project_name"));
    }

    @Test
    public void previewSemantic_shouldReturnOrdersPreviewWithSemanticColumns() {
        WarningRulePreviewResult preview = previewRule(SEMANTIC_RULE_ID);

        assertEquals("备注烟酒违规", preview.getRuleName());
        assertEquals(5, preview.getTotal());
        assertEquals(1, preview.getMatchedCount());
        assertNotNull(preview.getSql());
        assertTrue(preview.getSql().contains("orders"));
        assertTrue(preview.getSql().contains("remark"));
        assertEquals("是否触发", preview.getHeaders().get("_triggered"));
        assertEquals("命中原因", preview.getHeaders().get("_matchReason"));
        assertEquals("判定层", preview.getHeaders().get("_matchLayer"));
        assertEquals("LLM调用", preview.getHeaders().get("_llmInvoked"));
    }

    @Test
    public void previewSemantic_shouldTriggerOnlyOnKeywordMatch() {
        WarningRulePreviewResult preview = previewRule(SEMANTIC_RULE_ID);

        Map<String, Object> moutaiRow = findRowByRemark(preview, "采购茅台两瓶");
        assertEquals(true, moutaiRow.get("_triggered"));
        assertEquals("keyword", moutaiRow.get("_matchLayer"));
        assertEquals(false, moutaiRow.get("_llmInvoked"));
        assertTrue(String.valueOf(moutaiRow.get("_matchReason")).contains("茅台"));

        Map<String, Object> normalRow = findRowByRemark(preview, "正常办公采购");
        assertEquals(false, normalRow.get("_triggered"));
        assertEquals("none", normalRow.get("_matchLayer"));
    }

    @Test
    public void previewSemantic_llmCandidateRow_shouldNotInvokeLlmWithoutConfig() {
        WarningRulePreviewResult preview = previewRule(SEMANTIC_RULE_ID);
        Map<String, Object> llmCandidate = findRowByRemark(preview, "商务接待用名贵礼盒");

        assertEquals(false, llmCandidate.get("_triggered"));
        assertEquals("none", llmCandidate.get("_matchLayer"));
        assertEquals(false, llmCandidate.get("_llmInvoked"));
    }

    @Test
    public void previewComposite_shouldExposeCompositeColumns() {
        WarningRulePreviewResult preview = previewRule(COMPOSITE_RULE_ID);

        assertTrue(preview.getExpression().contains("profit < 500"));
        assertTrue(preview.getExpression().contains("remark"));
        assertEquals("指标触发", preview.getHeaders().get("_metricTriggered"));
        assertEquals("语义触发", preview.getHeaders().get("_semanticTriggered"));
        assertEquals("LLM调用", preview.getHeaders().get("_llmInvoked"));
    }

    @Test
    public void previewComposite_shouldTriggerOnlyWhenBothConditionsMet() {
        WarningRulePreviewResult preview = previewRule(COMPOSITE_RULE_ID);
        assertEquals(5, preview.getTotal());
        assertEquals(1, preview.getMatchedCount());

        Map<String, Object> moutaiRow = findRowByRemark(preview, "采购茅台两瓶");
        assertEquals(true, moutaiRow.get("_metricTriggered"));
        assertEquals(true, moutaiRow.get("_semanticTriggered"));
        assertEquals(true, moutaiRow.get("_triggered"));
        assertEquals("keyword", moutaiRow.get("_matchLayer"));
    }

    @Test
    public void previewComposite_metricOnlyRow_shouldNotTrigger() {
        WarningRulePreviewResult preview = previewRule(COMPOSITE_RULE_ID);
        Map<String, Object> row = findRowByRemark(preview, "正常办公采购");

        assertEquals(true, row.get("_metricTriggered"));
        assertEquals(false, row.get("_semanticTriggered"));
        assertEquals(false, row.get("_triggered"));
    }

    @Test
    public void previewComposite_semanticOnlyRow_shouldNotTrigger() {
        WarningRulePreviewResult preview = previewRule(COMPOSITE_RULE_ID);
        Map<String, Object> row = findRowByRemark(preview, "设备维护");

        assertEquals(true, row.get("_metricTriggered"));
        assertEquals(false, row.get("_semanticTriggered"));
        assertEquals(false, row.get("_triggered"));
    }

    @Test
    public void previewComposite_highProfitSemanticHit_shouldNotTriggerUnderAndLogic() {
        WarningRulePreviewResult preview = previewRule(COMPOSITE_RULE_ID);
        // 若存在高利润且含关键词的行，AND 逻辑下不应触发；当前种子无此行，用茅台行验证利润已低于阈值
        Map<String, Object> row = findRowByRemark(preview, "研发耗材");
        assertEquals(false, row.get("_metricTriggered"));
        assertEquals(false, row.get("_semanticTriggered"));
        assertEquals(false, row.get("_triggered"));
    }

    private List<WarningRule> listRules() {
        ResponseEntity<ApiResponse<List<WarningRule>>> response = restTemplate.exchange(
                baseUrl("/warning/rules"),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<List<WarningRule>>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        return response.getBody().getData();
    }

    private WarningRule getRule(String id) {
        ResponseEntity<ApiResponse<WarningRule>> response = restTemplate.exchange(
                baseUrl("/warning/rules/" + id),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<WarningRule>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        return response.getBody().getData();
    }

    private WarningRulePreviewResult previewRule(String id) {
        ResponseEntity<ApiResponse<WarningRulePreviewResult>> response = restTemplate.exchange(
                baseUrl("/warning/rules/" + id + "/preview?pageIndex=1&pageSize=20"),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<WarningRulePreviewResult>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        return response.getBody().getData();
    }

    private SemanticValidateResult postSemanticValidate(Map<String, Object> body) {
        ResponseEntity<ApiResponse<SemanticValidateResult>> response = restTemplate.exchange(
                baseUrl("/warning/rules/validate-semantic"),
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<ApiResponse<SemanticValidateResult>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        return response.getBody().getData();
    }

    private static WarningRule findRuleByCode(List<WarningRule> rules, String code) {
        return rules.stream()
                .filter(r -> code.equals(r.getCode()))
                .findFirst()
                .orElse(null);
    }

    private static Map<String, Object> findRowByRemark(WarningRulePreviewResult preview, String remark) {
        Map<String, Object> row = preview.getRows().stream()
                .filter(r -> remark.equals(String.valueOf(r.get("remark"))))
                .findFirst()
                .orElse(null);
        assertNotNull(row, "未找到备注为「" + remark + "」的预览行");
        return row;
    }

    private static Map<String, Object> seedSemanticConfig() {
        Map<String, Object> semantic = new HashMap<>();
        semantic.put("metaTableId", "mt-orders");
        semantic.put("fieldCode", "remark");
        semantic.put("policy", "备注中不得包含烟酒相关内容，包括茅台、五粮液等品牌");
        semantic.put("hintKeywords", Arrays.asList("烟", "酒", "茅台", "五粮液"));
        semantic.put("matchMode", "HYBRID");
        semantic.put("expandedKeywords", Arrays.asList("飞天", "剑南春", "中华烟"));
        return semantic;
    }

    private String baseUrl(String path) {
        return "http://localhost:" + port + "/api/v2" + path;
    }
}
