package com.yonyougov.atelier.api;

import com.yonyougov.atelier.api.dto.ApiResponse;
import com.yonyougov.atelier.api.dto.DataSourceRequest;
import com.yonyougov.atelier.api.dto.DataSourceResponse;
import com.yonyougov.atelier.infra.datasource.ConnectionTester;
import com.yonyougov.atelier.infra.datasource.DataSourceConfig;
import com.yonyougov.atelier.infra.datasource.DataSourceRegistry;
import com.yonyougov.atelier.infra.datasource.DbType;
import com.yonyougov.atelier.infra.exception.AtelierException;
import com.yonyougov.atelier.infra.persistence.service.DataSourcePersistenceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public DataSourceController(DataSourcePersistenceService persistenceService,
                                DataSourceRegistry registry) {
        this.persistenceService = persistenceService;
        this.registry = registry;
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
}
