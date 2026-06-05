package com.yonyougov.atelier.api;

import com.yonyougov.atelier.api.dto.ApiResponse;
import com.yonyougov.atelier.domain.dimension.Dimension;
import com.yonyougov.atelier.domain.dimension.DimensionValue;
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
public class DimensionApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void listDimensions_shouldReturnSeedDimensions() {
        ResponseEntity<ApiResponse<List<Dimension>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/dimensions",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<List<Dimension>>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getData().size() >= 2);
    }

    @Test
    public void listValues_shouldReturnDeptValues() {
        ResponseEntity<ApiResponse<List<DimensionValue>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/dimensions/dim-dept/values",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<List<DimensionValue>>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(2, response.getBody().getData().size());
    }
}
