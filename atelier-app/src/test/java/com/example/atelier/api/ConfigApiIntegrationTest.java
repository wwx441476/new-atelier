package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.domain.config.AtelierConfigBundle;
import com.example.atelier.domain.config.ConfigImportOptions;
import com.example.atelier.domain.config.ConfigImportResult;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ConfigApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void export_shouldReturnFullBundle() {
        ResponseEntity<ApiResponse<AtelierConfigBundle>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/config/export?includeSecrets=false",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<AtelierConfigBundle>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        AtelierConfigBundle bundle = response.getBody().getData();
        assertNotNull(bundle);
        assertEquals("1.0", bundle.getVersion());
        assertTrue(bundle.getDatasources().stream().anyMatch(ds -> "ds-demo".equals(ds.getId())));
        assertTrue(bundle.getMetrics().stream().anyMatch(m -> "profit".equals(m.getCode())));
        assertTrue(bundle.getWarningRules().stream().anyMatch(r -> "low_profit".equals(r.getCode())));
    }

    @Test
    public void import_shouldUpsertConfiguration() {
        ResponseEntity<ApiResponse<AtelierConfigBundle>> exportResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/config/export?includeSecrets=true",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<AtelierConfigBundle>>() {
                });
        AtelierConfigBundle bundle = exportResponse.getBody().getData();

        Map<String, Object> body = new HashMap<>();
        body.put("bundle", bundle);
        body.put("options", ConfigImportOptions.builder().build());

        ResponseEntity<ApiResponse<ConfigImportResult>> importResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/config/import",
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<ApiResponse<ConfigImportResult>>() {
                });
        assertEquals(200, importResponse.getStatusCodeValue());
        ConfigImportResult result = importResponse.getBody().getData();
        assertNotNull(result);
        assertTrue(result.getImported().get("datasources") >= 1);
        assertTrue(result.getImported().get("metrics") >= 1);
    }
}
