package com.yonyougov.atelier.api;

import com.yonyougov.atelier.api.dto.ApiResponse;
import com.yonyougov.atelier.domain.metadata.MetaTable;
import com.yonyougov.atelier.domain.metadata.MetaTableField;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
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
}
