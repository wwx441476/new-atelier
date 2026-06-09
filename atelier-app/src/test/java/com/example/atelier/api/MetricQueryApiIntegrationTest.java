package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.api.dto.MetricQueryApiRequest;
import com.example.atelier.domain.query.QueryResult;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MetricQueryApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void queryWithFilterGroups_shouldApplyAndConditions() {
        MetricQueryApiRequest request = new MetricQueryApiRequest();
        request.setMetricCodes(Collections.singletonList("revenue"));
        MetricQueryApiRequest.FilterGroupDto group = new MetricQueryApiRequest.FilterGroupDto();
        group.setConditions(Arrays.asList(
                filter("dept_code", "EQ", "001"),
                filter("fiscal_year", "EQ", "2024")
        ));
        request.setFilterGroups(Collections.singletonList(group));

        QueryResult result = postQuery(request);

        assertEquals(1, result.getTotal());
        Map<String, Object> row = result.getRows().get(0);
        assertEquals("001", String.valueOf(row.get("dept_code")));
        assertEquals("2024", String.valueOf(row.get("fiscal_year")));
    }

    @Test
    public void queryWithOrFilterGroups_shouldReturnMultipleRows() {
        MetricQueryApiRequest request = new MetricQueryApiRequest();
        request.setMetricCodes(Collections.singletonList("revenue"));
        request.setFilterGroups(Arrays.asList(
                group(
                        filter("dept_code", "EQ", "001"),
                        filter("fiscal_year", "EQ", "2024")
                ),
                group(
                        filter("dept_code", "EQ", "002"),
                        filter("fiscal_year", "EQ", "2025")
                )
        ));

        QueryResult result = postQuery(request);

        assertEquals(2, result.getTotal());
    }

    private QueryResult postQuery(MetricQueryApiRequest request) {
        ResponseEntity<ApiResponse<QueryResult>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metrics/query",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<QueryResult>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        return response.getBody().getData();
    }

    private static MetricQueryApiRequest.FilterGroupDto group(MetricQueryApiRequest.FilterDto... conditions) {
        MetricQueryApiRequest.FilterGroupDto group = new MetricQueryApiRequest.FilterGroupDto();
        group.setConditions(Arrays.asList(conditions));
        return group;
    }

    private static MetricQueryApiRequest.FilterDto filter(String field, String operator, String... values) {
        MetricQueryApiRequest.FilterDto dto = new MetricQueryApiRequest.FilterDto();
        dto.setField(field);
        dto.setOperator(operator);
        dto.setValues(Arrays.asList(values));
        return dto;
    }
}
