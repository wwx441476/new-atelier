package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
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
}
