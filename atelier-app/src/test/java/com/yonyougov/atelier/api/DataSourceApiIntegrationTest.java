package com.yonyougov.atelier.api;

import com.yonyougov.atelier.api.dto.ApiResponse;
import com.yonyougov.atelier.api.dto.DataSourceResponse;
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
public class DataSourceApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void listDatasources_shouldReturnSeedData() {
        ResponseEntity<ApiResponse<List<DataSourceResponse>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/datasources",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<List<DataSourceResponse>>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        assertTrue(response.getBody().getData().stream().anyMatch(ds -> "ds-demo".equals(ds.getId())));
    }
}
