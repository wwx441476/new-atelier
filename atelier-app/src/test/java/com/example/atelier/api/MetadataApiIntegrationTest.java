package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.domain.metadata.MetaTable;
import com.example.atelier.domain.metadata.MetaTableDdlResult;
import com.example.atelier.domain.metadata.MetaTableField;
import com.example.atelier.domain.query.QueryResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
    public void saveTable_withSchema_shouldUseQualifiedNameInPreviewSql() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        MetaTable table = MetaTable.builder()
                .tableCode("orders")
                .tableName("订单事实表")
                .catalogCode("finance")
                .datasourceId("ds-demo")
                .schemaCode("PUBLIC")
                .comments("演示订单表")
                .build();
        ResponseEntity<ApiResponse<MetaTable>> saveResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables",
                HttpMethod.POST,
                new HttpEntity<>(table, headers),
                new ParameterizedTypeReference<ApiResponse<MetaTable>>() {
                });
        assertEquals(0, saveResponse.getBody().getCode());
        assertEquals("PUBLIC", saveResponse.getBody().getData().getSchemaCode());

        ResponseEntity<ApiResponse<QueryResult>> previewResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables/mt-orders/preview?pageIndex=1&pageSize=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<QueryResult>>() {
                });
        assertEquals(0, previewResponse.getBody().getCode());
        assertTrue(previewResponse.getBody().getData().getSql().contains("FROM PUBLIC.orders"));

        table.setId("mt-orders");
        table.setSchemaCode(null);
        restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables",
                HttpMethod.POST,
                new HttpEntity<>(table, headers),
                new ParameterizedTypeReference<ApiResponse<MetaTable>>() {
                });
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
        assertEquals("SELECT dept_code, fiscal_year, amount, cost_amount FROM orders", result.getSql());
        assertFalse(result.getRows().get(0).containsKey("id"));
        assertFalse(result.getRows().get(0).containsKey("dept_id"));
        assertEquals(4, result.getRows().get(0).size());
    }

    @Test
    public void getCreateTableDdl_shouldReturnOrdersDdl() {
        ResponseEntity<ApiResponse<MetaTableDdlResult>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables/mt-orders/ddl",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<MetaTableDdlResult>>() {
                });
        assertEquals(200, response.getStatusCodeValue());
        MetaTableDdlResult ddlResult = response.getBody().getData();
        assertNotNull(ddlResult);
        assertTrue(ddlResult.getDdl().contains("CREATE TABLE"));
        assertTrue(ddlResult.getDdl().contains("dept_code"));
        assertTrue(ddlResult.getDdl().contains("cost_amount"));
        assertEquals("orders", ddlResult.getTableCode());
        assertTrue(ddlResult.isTableExists());
    }

    @Test
    public void executeCreateTable_shouldCreatePhysicalTableFromMetadata() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        MetaTable table = MetaTable.builder()
                .tableCode("ddl_test_army")
                .tableName("DDL测试部队表")
                .catalogCode("test")
                .datasourceId("ds-demo")
                .build();
        ResponseEntity<ApiResponse<MetaTable>> saveTableResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables",
                HttpMethod.POST,
                new HttpEntity<>(table, headers),
                new ParameterizedTypeReference<ApiResponse<MetaTable>>() {
                });
        assertEquals(0, saveTableResponse.getBody().getCode());
        String tableId = saveTableResponse.getBody().getData().getId();
        assertNotNull(tableId);

        saveField(tableId, MetaTableField.builder().fieldCode("id").fieldName("唯一标识").fieldType("VARCHAR").sort(1).build());
        saveField(tableId, MetaTableField.builder().fieldCode("code").fieldName("编码").fieldType("VARCHAR").sort(2).build());

        ResponseEntity<ApiResponse<MetaTableDdlResult>> ddlResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables/" + tableId + "/ddl",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<MetaTableDdlResult>>() {
                });
        MetaTableDdlResult ddlResult = ddlResponse.getBody().getData();
        assertFalse(ddlResult.isTableExists());
        assertTrue(ddlResult.getDdl().contains("ddl_test_army"));
        assertTrue(ddlResult.getDdl().contains("id VARCHAR"));
        assertTrue(ddlResult.getDdl().contains("PRIMARY KEY"));

        ResponseEntity<ApiResponse<Void>> executeResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables/" + tableId + "/ddl/execute",
                HttpMethod.POST,
                null,
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        assertEquals(0, executeResponse.getBody().getCode());

        ResponseEntity<ApiResponse<QueryResult>> previewResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables/" + tableId + "/preview?pageIndex=1&pageSize=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<QueryResult>>() {
                });
        assertEquals(0, previewResponse.getBody().getCode());
        assertEquals(0, previewResponse.getBody().getData().getTotal());

        metadataServiceCleanup(tableId);
    }

    private void saveField(String tableId, MetaTableField field) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<ApiResponse<MetaTableField>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables/" + tableId + "/fields",
                HttpMethod.POST,
                new HttpEntity<>(field, headers),
                new ParameterizedTypeReference<ApiResponse<MetaTableField>>() {
                });
        assertEquals(0, response.getBody().getCode());
    }

    private void metadataServiceCleanup(String tableId) {
        restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables/" + tableId,
                HttpMethod.DELETE,
                null,
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
    }

    @Test
    public void executeSyncTable_shouldAddMissingColumns() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        MetaTableField remarkField = MetaTableField.builder()
                .fieldCode("remark")
                .fieldName("备注")
                .fieldType("VARCHAR")
                .fieldLength(255)
                .sort(5)
                .build();
        ResponseEntity<ApiResponse<MetaTableField>> saveFieldResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables/mt-orders/fields",
                HttpMethod.POST,
                new HttpEntity<>(remarkField, headers),
                new ParameterizedTypeReference<ApiResponse<MetaTableField>>() {
                });
        assertEquals(0, saveFieldResponse.getBody().getCode());

        ResponseEntity<ApiResponse<MetaTableDdlResult>> ddlResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables/mt-orders/ddl",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<MetaTableDdlResult>>() {
                });
        MetaTableDdlResult ddlResult = ddlResponse.getBody().getData();
        assertTrue(ddlResult.isSyncNeeded());
        assertTrue(ddlResult.getAlterDdl().contains("ADD COLUMN remark"));
        assertTrue(ddlResult.getMissingFieldCodes().contains("remark"));

        ResponseEntity<ApiResponse<Void>> syncResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables/mt-orders/ddl/sync",
                HttpMethod.POST,
                null,
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        assertEquals(0, syncResponse.getBody().getCode());

        ResponseEntity<ApiResponse<QueryResult>> previewResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/tables/mt-orders/preview?pageIndex=1&pageSize=20",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<QueryResult>>() {
                });
        assertEquals(0, previewResponse.getBody().getCode());
        assertTrue(previewResponse.getBody().getData().getSql().contains("remark"));

        String fieldId = saveFieldResponse.getBody().getData().getId();
        restTemplate.exchange(
                "http://localhost:" + port + "/api/v2/metadata/fields/" + fieldId,
                HttpMethod.DELETE,
                null,
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
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
