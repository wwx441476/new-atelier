package com.yonyougov.atelier.api;

import com.yonyougov.atelier.api.dto.ApiResponse;
import com.yonyougov.atelier.domain.metric.MetricDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MetricDefinitionApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void listDefinitions_shouldReturnSeedMetrics() {
        ResponseEntity<ApiResponse<List<MetricDefinition>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metrics/definitions",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<List<MetricDefinition>>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getData().size() >= 3);
    }

    @Test
    public void getRevenue_shouldReturnDefinition() {
        ResponseEntity<ApiResponse<MetricDefinition>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metrics/definitions/revenue",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<MetricDefinition>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("revenue", response.getBody().getData().getCode());
    }
}
