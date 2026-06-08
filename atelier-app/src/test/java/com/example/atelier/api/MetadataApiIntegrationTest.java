package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.domain.metadata.MetaTable;
import com.example.atelier.domain.metadata.MetaTableField;
import com.example.atelier.domain.query.QueryResult;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MetadataApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void listTables_shouldReturnOrdersMetaTable() {
        ResponseEntity<ApiResponse<List<MetaTable>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<List<MetaTable>>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        assertFalse(response.getBody().getData().isEmpty());
    }

    @Test
    public void listFields_shouldReturnSeedFields() {
        ResponseEntity<ApiResponse<List<MetaTableField>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables/mt-orders/fields",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<List<MetaTableField>>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(4, response.getBody().getData().size());
    }

    @Test
    public void previewTable_shouldReturnOrdersData() {
        ResponseEntity<ApiResponse<QueryResult>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables/mt-orders/preview?pageIndex=1&pageSize=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<QueryResult>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        QueryResult result = response.getBody().getData();
        assertNotNull(result);
        assertEquals(4, result.getTotal());
        assertEquals(4, result.getRows().size());
        assertNotNull(result.getHeaders());
        assertTrue(result.getHeaders().containsKey("dept_code"));
        assertEquals("部门编码", result.getHeaders().get("dept_code"));
    }

    @Test
    public void previewTable_shouldSupportLegacyMlPrefix() {
        ResponseEntity<ApiResponse<QueryResult>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables/ml-orders/preview?pageIndex=1&pageSize=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<QueryResult>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        QueryResult result = response.getBody().getData();
        assertNotNull(result);
        assertEquals(4, result.getTotal());
    }
}
