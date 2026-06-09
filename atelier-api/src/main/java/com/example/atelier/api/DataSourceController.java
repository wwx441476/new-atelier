package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.api.dto.DataSourceRequest;
import com.example.atelier.api.dto.DataSourceResponse;
import com.example.atelier.api.dto.DbBrowseQueryRequest;
import com.example.atelier.domain.datasource.DbColumnInfo;
import com.example.atelier.domain.metric.FilterCondition;
import com.example.atelier.domain.metric.FilterGroup;
import com.example.atelier.domain.metric.FilterOperator;
import com.example.atelier.domain.datasource.DbSchemaInfo;
import com.example.atelier.domain.datasource.DbTableInfo;
import com.example.atelier.domain.query.QueryResult;
import com.example.atelier.infra.datasource.ConnectionTester;
import com.example.atelier.infra.datasource.DataSourceConfig;
import com.example.atelier.infra.datasource.DataSourceRegistry;
import com.example.atelier.infra.datasource.DbType;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.jdbc.DatabaseBrowserService;
import com.example.atelier.infra.persistence.service.DataSourcePersistenceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据源管理 API — /api/v2/datasources。
 */
@RestController
@RequestMapping("/api/v2/datasources")
public class DataSourceController {

    private final DataSourcePersistenceService persistenceService;
    private final DataSourceRegistry registry;
    private final DatabaseBrowserService databaseBrowserService;

    public DataSourceController(DataSourcePersistenceService persistenceService,
                                DataSourceRegistry registry,
                                DatabaseBrowserService databaseBrowserService) {
        this.persistenceService = persistenceService;
        this.registry = registry;
        this.databaseBrowserService = databaseBrowserService;
    }

    @GetMapping
    public ApiResponse<List<DataSourceResponse>> list() {
        return ApiResponse.ok(persistenceService.findAllConfigs().stream()
                .map(this::toResponse)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ApiResponse<DataSourceResponse> get(@PathVariable String id) {
        return persistenceService.findConfigById(id)
                .map(config -> ApiResponse.ok(toResponse(config)))
                .orElseGet(() -> ApiResponse.fail("数据源不存在: " + id));
    }

    @PostMapping
    public ApiResponse<DataSourceResponse> save(@RequestBody DataSourceRequest request) {
        DataSourceConfig config = toConfig(request);
        DataSourceConfig saved = persistenceService.save(config);
        if (saved.isEnabled()) {
            registry.refresh(saved);
        } else {
            registry.unregister(saved.getId());
        }
        return ApiResponse.ok(toResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        persistenceService.deleteById(id);
        registry.unregister(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testConnection(@RequestBody DataSourceRequest request) {
        DataSourceConfig config = toConfig(request);
        boolean ok = ConnectionTester.test(config);
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        result.put("message", ok ? "连接成功" : "连接失败");
        return ApiResponse.ok(result);
    }

    @GetMapping("/{id}/browse/schemas")
    public ApiResponse<List<DbSchemaInfo>> browseSchemas(@PathVariable String id) {
        return ApiResponse.ok(databaseBrowserService.listSchemas(id));
    }

    @GetMapping("/{id}/browse/tables")
    public ApiResponse<List<DbTableInfo>> browseTables(
            @PathVariable String id,
            @RequestParam(value = "schema", required = false) String schema) {
        return ApiResponse.ok(databaseBrowserService.listTables(id, schema));
    }

    @GetMapping("/{id}/browse/tables/{table}/columns")
    public ApiResponse<List<DbColumnInfo>> browseColumns(
            @PathVariable String id,
            @PathVariable String table,
            @RequestParam(value = "schema", required = false) String schema) {
        return ApiResponse.ok(databaseBrowserService.listColumns(id, schema, table));
    }

    @GetMapping("/{id}/browse/tables/{table}/preview")
    public ApiResponse<QueryResult> browsePreview(
            @PathVariable String id,
            @PathVariable String table,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestParam(value = "pageIndex", defaultValue = "1") int pageIndex,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(databaseBrowserService.previewTableData(id, schema, table, pageIndex, pageSize));
    }

    @PostMapping("/{id}/browse/query")
    public ApiResponse<QueryResult> browseExecuteSql(
            @PathVariable String id,
            @RequestBody DbBrowseQueryRequest request) {
        return ApiResponse.ok(databaseBrowserService.executeSelectQuery(
                id, request.getSql(), request.getPageIndex(), request.getPageSize()));
    }

    @PostMapping("/{id}/browse/tables/{table}/query")
    public ApiResponse<QueryResult> browseTableQuery(
            @PathVariable String id,
            @PathVariable String table,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestBody DbBrowseQueryRequest request) {
        FilterConversion conversion = toFilterConversion(request);
        return ApiResponse.ok(databaseBrowserService.previewTableWithFilters(
                id, schema, table,
                conversion.filters, conversion.filterGroups,
                request.getPageIndex(), request.getPageSize()));
    }

    private DataSourceConfig toConfig(DataSourceRequest request) {
        if (request.getId() == null || request.getId().trim().isEmpty()) {
            throw new AtelierException("数据源 id 不能为空");
        }
        return DataSourceConfig.builder()
                .id(request.getId().trim())
                .name(request.getName())
                .jdbcUrl(request.getJdbcUrl())
                .username(request.getUsername())
                .password(request.getPassword() != null ? request.getPassword() : "")
                .dbType(DbType.fromString(request.getDbType()))
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build();
    }

    private DataSourceResponse toResponse(DataSourceConfig config) {
        return DataSourceResponse.builder()
                .id(config.getId())
                .name(config.getName())
                .jdbcUrl(config.getJdbcUrl())
                .username(config.getUsername())
                .dbType(config.getDbType() != null ? config.getDbType().name() : DbType.UNKNOWN.name())
                .enabled(config.isEnabled())
                .build();
    }

    private FilterConversion toFilterConversion(DbBrowseQueryRequest request) {
        List<FilterCondition> filters = null;
        if (request.getFilters() != null) {
            filters = request.getFilters().stream()
                    .map(this::toFilterCondition)
                    .collect(Collectors.toList());
        }
        List<FilterGroup> filterGroups = null;
        if (request.getFilterGroups() != null) {
            filterGroups = request.getFilterGroups().stream()
                    .map(group -> FilterGroup.builder()
                            .conditions(group.getConditions() == null ? null :
                                    group.getConditions().stream()
                                            .map(this::toFilterCondition)
                                            .collect(Collectors.toList()))
                            .build())
                    .collect(Collectors.toList());
        }
        return new FilterConversion(filters, filterGroups);
    }

    private FilterCondition toFilterCondition(DbBrowseQueryRequest.FilterDto filter) {
        return FilterCondition.builder()
                .field(filter.getField())
                .operator(parseOperator(filter.getOperator()))
                .values(filter.getValues())
                .build();
    }

    private FilterOperator parseOperator(String operator) {
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("过滤运算符不能为空");
        }
        String normalized = operator.toUpperCase().replace(" ", "_");
        if ("GTE".equals(normalized)) {
            normalized = "GE";
        } else if ("LTE".equals(normalized)) {
            normalized = "LE";
        }
        return FilterOperator.valueOf(normalized);
    }

    private static final class FilterConversion {
        private final List<FilterCondition> filters;
        private final List<FilterGroup> filterGroups;

        private FilterConversion(List<FilterCondition> filters, List<FilterGroup> filterGroups) {
            this.filters = filters;
            this.filterGroups = filterGroups;
        }
    }
}
