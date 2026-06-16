package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.domain.warning.ExpressionValidateResult;
import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.domain.warning.WarningRulePreviewResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WarningRuleApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void listRules_shouldReturnSeedRule() {
        ResponseEntity<ApiResponse<List<WarningRule>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/warning/rules",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<List<WarningRule>>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getData().stream().anyMatch(r -> "low_profit".equals(r.getCode())));
    }

    @Test
    public void validateExpression_shouldAcceptChineseOrOperator() {
        Map<String, Object> body = new HashMap<>();
        body.put("expression", "profit < 500 或 cost > 100");
        body.put("metricCodes", Arrays.asList("profit", "cost"));

        ResponseEntity<ApiResponse<ExpressionValidateResult>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/warning/rules/validate-expression",
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<ApiResponse<ExpressionValidateResult>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        ExpressionValidateResult result = response.getBody().getData();
        assertTrue(result.isValid());
        assertTrue(result.getNormalizedExpression().contains("||"));
    }

    @Test
    public void validateExpression_shouldAcceptGroupedExpression() {
        Map<String, Object> body = new HashMap<>();
        body.put("expression", "(profit < 500) 或 (cost > 100)");
        body.put("metricCodes", Arrays.asList("profit", "cost"));

        ResponseEntity<ApiResponse<ExpressionValidateResult>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/warning/rules/validate-expression",
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<ApiResponse<ExpressionValidateResult>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().getData().isValid());
    }

    @Test
    public void validateExpression_shouldRejectUnbalancedParentheses() {
        Map<String, Object> body = new HashMap<>();
        body.put("expression", "(profit < 500");
        body.put("metricCodes", Arrays.asList("profit"));

        ResponseEntity<ApiResponse<ExpressionValidateResult>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/warning/rules/validate-expression",
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<ApiResponse<ExpressionValidateResult>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(false, response.getBody().getData().isValid());
    }

    @Test
    public void validateExpression_shouldRejectUnknownMetric() {
        Map<String, Object> body = new HashMap<>();
        body.put("expression", "profit < 500");
        body.put("metricCodes", Arrays.asList("revenue"));

        ResponseEntity<ApiResponse<ExpressionValidateResult>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/warning/rules/validate-expression",
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<ApiResponse<ExpressionValidateResult>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(false, response.getBody().getData().isValid());
    }

    @Test
    public void evaluateExpression_shouldTrigger() {
        Map<String, Object> body = new HashMap<>();
        body.put("expression", "profit < 500");
        Map<String, Object> values = new HashMap<>();
        values.put("profit", 300);
        body.put("metricValues", values);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/warning/rules/evaluate",
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(true, response.getBody().getData().get("triggered"));
    }

    @Test
    public void previewRule_shouldReturnRowsWithTriggeredFlag() {
        ResponseEntity<ApiResponse<WarningRulePreviewResult>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/warning/rules/wr-1/preview?pageIndex=1&pageSize=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<WarningRulePreviewResult>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        WarningRulePreviewResult preview = response.getBody().getData();
        assertNotNull(preview);
        assertEquals("利润过低预警", preview.getRuleName());
        assertEquals("profit < 500", preview.getExpression());
        assertNotNull(preview.getSql());
        assertTrue(preview.getSql().contains("profit"));
        assertTrue(preview.getTotal() > 0);
        assertNotNull(preview.getRows());
        assertTrue(preview.getRows().stream().anyMatch(r -> Boolean.TRUE.equals(r.get("_triggered"))));
    }

    @Test
    public void previewRule_withDimensionFilter_shouldNarrowRows() {
        Map<String, Object> filter = new HashMap<>();
        filter.put("field", "dept_code");
        filter.put("operator", "IN");
        filter.put("values", Collections.singletonList("002"));

        Map<String, Object> body = new HashMap<>();
        body.put("pageIndex", 1);
        body.put("pageSize", 20);
        body.put("filters", Collections.singletonList(filter));

        ResponseEntity<ApiResponse<WarningRulePreviewResult>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/warning/rules/wr-1/preview",
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<ApiResponse<WarningRulePreviewResult>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        WarningRulePreviewResult preview = response.getBody().getData();
        assertNotNull(preview);
        assertTrue(preview.getSql().contains("dept_code IN ('002')"));
        assertEquals(2, preview.getTotal());
        assertTrue(preview.getRows().stream().allMatch(r -> "002".equals(String.valueOf(r.get("dept_code")))));
    }

    @Test
    public void previewRule_withOrFilterGroups_shouldCombineConditions() {
        Map<String, Object> group1Cond1 = new HashMap<>();
        group1Cond1.put("field", "dept_code");
        group1Cond1.put("operator", "IN");
        group1Cond1.put("values", Collections.singletonList("001"));
        Map<String, Object> group1Cond2 = new HashMap<>();
        group1Cond2.put("field", "fiscal_year");
        group1Cond2.put("operator", "IN");
        group1Cond2.put("values", Collections.singletonList("2024"));
        Map<String, Object> group1 = new HashMap<>();
        group1.put("conditions", Arrays.asList(group1Cond1, group1Cond2));

        Map<String, Object> group2Cond1 = new HashMap<>();
        group2Cond1.put("field", "dept_code");
        group2Cond1.put("operator", "IN");
        group2Cond1.put("values", Collections.singletonList("002"));
        Map<String, Object> group2Cond2 = new HashMap<>();
        group2Cond2.put("field", "fiscal_year");
        group2Cond2.put("operator", "IN");
        group2Cond2.put("values", Collections.singletonList("2025"));
        Map<String, Object> group2 = new HashMap<>();
        group2.put("conditions", Arrays.asList(group2Cond1, group2Cond2));

        Map<String, Object> body = new HashMap<>();
        body.put("pageIndex", 1);
        body.put("pageSize", 20);
        body.put("filterGroups", Arrays.asList(group1, group2));

        ResponseEntity<ApiResponse<WarningRulePreviewResult>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/warning/rules/wr-1/preview",
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<ApiResponse<WarningRulePreviewResult>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        WarningRulePreviewResult preview = response.getBody().getData();
        assertNotNull(preview);
        assertTrue(preview.getSql().contains(
                "(dept_code IN ('001') AND fiscal_year IN ('2024')) OR (dept_code IN ('002') AND fiscal_year IN ('2025'))"));
        assertEquals(2, preview.getTotal());
    }
}
