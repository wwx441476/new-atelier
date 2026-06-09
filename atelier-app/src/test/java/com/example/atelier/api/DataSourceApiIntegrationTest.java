package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.api.dto.DataSourceResponse;
import com.example.atelier.domain.datasource.DbColumnInfo;
import com.example.atelier.domain.datasource.DbSchemaInfo;
import com.example.atelier.domain.datasource.DbTableInfo;
import com.example.atelier.domain.query.QueryResult;
import com.example.atelier.infra.datasource.DataSourceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.sql.Connection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DataSourceApiIntegrationTest {

    private static final String H2_JDBC_URL = "jdbc:h2:mem:atelier;DB_CLOSE_DELAY=-1;MODE=MySQL";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DataSourceRegistry registry;

    private String lastCreatedId;

    @AfterEach
    void cleanupCreatedDatasource() {
        if (lastCreatedId == null) {
            return;
        }
        restTemplate.exchange(
                baseUrl() + "/datasources/" + lastCreatedId,
                HttpMethod.DELETE,
                null,
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        registry.unregister(lastCreatedId);
        lastCreatedId = null;
    }

    @Test
    public void listDatasources_shouldReturnSeedData() {
        ResponseEntity<ApiResponse<List<DataSourceResponse>>> response = restTemplate.exchange(
                baseUrl() + "/datasources",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<List<DataSourceResponse>>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCode());
        assertTrue(response.getBody().getData().stream().anyMatch(ds -> "ds-demo".equals(ds.getId())));
    }

    @Test
    public void addDatasource_shouldPersistRefreshRegistryAndTestConnection() throws Exception {
        lastCreatedId = "ds-it-add-" + System.currentTimeMillis();

        ResponseEntity<ApiResponse<DataSourceResponse>> createResponse = restTemplate.exchange(
                baseUrl() + "/datasources",
                HttpMethod.POST,
                new HttpEntity<>(newDatasourceBody(lastCreatedId)),
                new ParameterizedTypeReference<ApiResponse<DataSourceResponse>>() {
                });

        assertEquals(200, createResponse.getStatusCodeValue());
        assertNotNull(createResponse.getBody());
        assertEquals(0, createResponse.getBody().getCode());
        assertNotNull(createResponse.getBody().getData());
        assertEquals(lastCreatedId, createResponse.getBody().getData().getId());
        assertEquals("Integration Test H2", createResponse.getBody().getData().getName());
        assertEquals("H2", createResponse.getBody().getData().getDbType());
        assertTrue(createResponse.getBody().getData().isEnabled());

        ResponseEntity<ApiResponse<List<DataSourceResponse>>> listResponse = restTemplate.exchange(
                baseUrl() + "/datasources",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<List<DataSourceResponse>>>() {
                });
        assertEquals(0, listResponse.getBody().getCode());
        assertTrue(listResponse.getBody().getData().stream()
                .anyMatch(ds -> lastCreatedId.equals(ds.getId())));

        ResponseEntity<ApiResponse<DataSourceResponse>> getResponse = restTemplate.exchange(
                baseUrl() + "/datasources/" + lastCreatedId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<DataSourceResponse>>() {
                });
        assertEquals(0, getResponse.getBody().getCode());
        assertEquals(lastCreatedId, getResponse.getBody().getData().getId());

        assertNotNull(registry.getConfig(lastCreatedId));
        try (Connection connection = registry.getConnection(lastCreatedId)) {
            assertTrue(connection.isValid(3));
        }

        ResponseEntity<ApiResponse<Map<String, Object>>> testResponse = restTemplate.exchange(
                baseUrl() + "/datasources/test",
                HttpMethod.POST,
                new HttpEntity<>(newDatasourceBody(lastCreatedId)),
                new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {
                });
        assertEquals(0, testResponse.getBody().getCode());
        assertEquals(true, testResponse.getBody().getData().get("success"));
    }

    @Test
    public void browseSchemas_shouldReturnPublicForDemoH2() {
        ResponseEntity<ApiResponse<List<DbSchemaInfo>>> response = restTemplate.exchange(
                baseUrl() + "/datasources/ds-demo/browse/schemas",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<List<DbSchemaInfo>>>() {
                });
        assertEquals(0, response.getBody().getCode());
        assertTrue(response.getBody().getData().stream()
                .anyMatch(schema -> "PUBLIC".equalsIgnoreCase(schema.getName())));
    }

    @Test
    public void browseTables_shouldReturnOrdersInPublicSchema() {
        ResponseEntity<ApiResponse<List<DbTableInfo>>> response = restTemplate.exchange(
                baseUrl() + "/datasources/ds-demo/browse/tables?schema=PUBLIC",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<List<DbTableInfo>>>() {
                });
        assertEquals(0, response.getBody().getCode());
        assertTrue(response.getBody().getData().stream()
                .anyMatch(table -> "orders".equalsIgnoreCase(table.getName())));
    }

    @Test
    public void browseColumns_shouldReturnOrdersColumns() {
        ResponseEntity<ApiResponse<List<DbColumnInfo>>> response = restTemplate.exchange(
                baseUrl() + "/datasources/ds-demo/browse/tables/orders/columns?schema=PUBLIC",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<List<DbColumnInfo>>>() {
                });
        assertEquals(0, response.getBody().getCode());
        assertTrue(response.getBody().getData().stream()
                .anyMatch(column -> "dept_code".equalsIgnoreCase(column.getName())));
    }

    @Test
    public void browsePreview_shouldReturnOrdersData() {
        ResponseEntity<ApiResponse<QueryResult>> response = restTemplate.exchange(
                baseUrl() + "/datasources/ds-demo/browse/tables/orders/preview?schema=PUBLIC&pageIndex=1&pageSize=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<QueryResult>>() {
                });
        assertEquals(0, response.getBody().getCode());
        QueryResult result = response.getBody().getData();
        assertNotNull(result);
        assertTrue(result.getTotal() > 0);
        assertNotNull(result.getSql());
        assertTrue(result.getSql().contains("orders"));
    }

    @Test
    public void browseTableQuery_shouldFilterOrdersByDeptCode() {
        Map<String, Object> body = new HashMap<>();
        body.put("pageIndex", 1);
        body.put("pageSize", 20);
        Map<String, Object> filter = new HashMap<>();
        filter.put("field", "dept_code");
        filter.put("operator", "IN");
        filter.put("values", Collections.singletonList("001"));
        body.put("filters", Collections.singletonList(filter));

        ResponseEntity<ApiResponse<QueryResult>> response = restTemplate.exchange(
                baseUrl() + "/datasources/ds-demo/browse/tables/orders/query?schema=PUBLIC",
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<ApiResponse<QueryResult>>() {
                });
        assertEquals(0, response.getBody().getCode());
        QueryResult result = response.getBody().getData();
        assertEquals(2, result.getTotal());
        assertTrue(result.getSql().contains("dept_code IN ('001')"));
    }

    @Test
    public void browseExecuteSql_shouldRunCustomSelect() {
        Map<String, Object> body = new HashMap<>();
        body.put("sql", "SELECT dept_code, amount FROM PUBLIC.orders WHERE dept_code = '002'");
        body.put("pageIndex", 1);
        body.put("pageSize", 20);

        ResponseEntity<ApiResponse<QueryResult>> response = restTemplate.exchange(
                baseUrl() + "/datasources/ds-demo/browse/query",
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<ApiResponse<QueryResult>>() {
                });
        assertEquals(0, response.getBody().getCode());
        QueryResult result = response.getBody().getData();
        assertEquals(2, result.getTotal());
        assertEquals(2, result.getRows().get(0).size());
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v2";
    }

    private Map<String, Object> newDatasourceBody(String id) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("name", "Integration Test H2");
        body.put("jdbcUrl", H2_JDBC_URL);
        body.put("username", "sa");
        body.put("password", "");
        body.put("dbType", "H2");
        body.put("enabled", true);
        return body;
    }
}
