package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.domain.metadata.MetaTable;
import com.example.atelier.domain.metadata.MetaTableField;
import com.example.atelier.domain.query.QueryResult;
import com.example.atelier.metadata.spi.MetadataService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 元数据管理 API — /api/v2/metadata。
 */
@RestController
@RequestMapping("/api/v2/metadata")
public class MetadataController {

    private final MetadataService metadataService;

    public MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping("/tables")
    public ApiResponse<List<MetaTable>> listTables(
            @RequestParam(value = "datasourceId", required = false) String datasourceId) {
        if (datasourceId != null && !datasourceId.isEmpty()) {
            return ApiResponse.ok(metadataService.listTablesByDatasource(datasourceId));
        }
        return ApiResponse.ok(metadataService.listTables());
    }

    @GetMapping("/tables/{id}")
    public ApiResponse<MetaTable> getTable(@PathVariable String id) {
        return metadataService.getTable(id)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("元数据表不存在: " + id));
    }

    @PostMapping("/tables")
    public ApiResponse<MetaTable> saveTable(@RequestBody MetaTable table) {
        return ApiResponse.ok(metadataService.saveTable(table));
    }

    @DeleteMapping("/tables/{id}")
    public ApiResponse<Void> deleteTable(@PathVariable String id) {
        metadataService.deleteTable(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/tables/{id}/fields")
    public ApiResponse<List<MetaTableField>> listFields(@PathVariable String id) {
        return ApiResponse.ok(metadataService.listFields(id));
    }

    @PostMapping("/tables/{id}/fields")
    public ApiResponse<MetaTableField> saveField(@PathVariable String id, @RequestBody MetaTableField field) {
        field.setTableId(id);
        return ApiResponse.ok(metadataService.saveField(field));
    }

    @DeleteMapping("/fields/{fieldId}")
    public ApiResponse<Void> deleteField(@PathVariable String fieldId) {
        metadataService.deleteField(fieldId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/discover")
    public ApiResponse<List<MetaTable>> discover(@RequestParam String datasourceId) {
        return ApiResponse.ok(metadataService.discoverTables(datasourceId));
    }

    @GetMapping("/tables/{id}/preview")
    public ApiResponse<QueryResult> previewTable(
            @PathVariable String id,
            @RequestParam(value = "pageIndex", defaultValue = "1") int pageIndex,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(metadataService.previewTableData(id, pageIndex, pageSize));
    }
}
