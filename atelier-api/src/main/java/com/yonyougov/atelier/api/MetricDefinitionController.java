package com.yonyougov.atelier.api;

import com.yonyougov.atelier.api.dto.ApiResponse;
import com.yonyougov.atelier.domain.metric.MetricDefinition;
import com.yonyougov.atelier.infra.persistence.service.MetricDefinitionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 指标定义 CRUD API — /api/v2/metrics/definitions。
 */
@RestController
@RequestMapping("/api/v2/metrics/definitions")
public class MetricDefinitionController {

    private final MetricDefinitionService metricDefinitionService;

    public MetricDefinitionController(MetricDefinitionService metricDefinitionService) {
        this.metricDefinitionService = metricDefinitionService;
    }

    @GetMapping
    public ApiResponse<List<MetricDefinition>> list() {
        return ApiResponse.ok(metricDefinitionService.listAll());
    }

    @GetMapping("/{code}")
    public ApiResponse<MetricDefinition> get(@PathVariable String code) {
        return metricDefinitionService.getByCode(code)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("指标不存在: " + code));
    }

    @PostMapping
    public ApiResponse<MetricDefinition> save(@RequestBody MetricDefinition definition) {
        return ApiResponse.ok(metricDefinitionService.save(definition));
    }

    @DeleteMapping("/{code}")
    public ApiResponse<Void> delete(@PathVariable String code) {
        metricDefinitionService.deleteByCode(code);
        return ApiResponse.ok(null);
    }
}
