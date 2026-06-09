package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.domain.dimension.Dimension;
import com.example.atelier.domain.dimension.DimensionValue;
import com.example.atelier.domain.dimension.TimeGranularity;
import com.example.atelier.domain.dimension.TimeValueGenerateRequest;
import com.example.atelier.domain.dimension.TimeValueGenerateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
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

    @Test
    public void listValues_fromTable_shouldReadDeptPhysicalTable() {
        ResponseEntity<ApiResponse<List<DimensionValue>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/dimensions/dim-dept-db/values",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<List<DimensionValue>>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        List<DimensionValue> values = response.getBody().getData();
        assertEquals(2, values.size());
        assertTrue(values.stream().anyMatch(v -> "d1".equals(v.getCode()) && "销售部".equals(v.getName())));
        assertTrue(values.stream().anyMatch(v -> "d2".equals(v.getCode()) && "研发部".equals(v.getName())));
    }

    @Test
    public void generateTimeValues_shouldCreateFormattedYearValues() {
        TimeValueGenerateRequest request = TimeValueGenerateRequest.builder()
                .granularity(TimeGranularity.YEAR)
                .startYear(2020)
                .endYear(2022)
                .codeFormat("YYYY")
                .nameFormat("YYYY年")
                .skipExisting(true)
                .build();
        ResponseEntity<ApiResponse<TimeValueGenerateResult>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/dimensions/dim-year/values/generate-time",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<TimeValueGenerateResult>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().getData().getGenerated());
        assertEquals("2020", response.getBody().getData().getValues().get(0).getCode());
        assertEquals("2020年", response.getBody().getData().getValues().get(0).getName());
    }
}
