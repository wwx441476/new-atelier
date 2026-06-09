package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.api.dto.MetricQueryApiRequest;
import com.example.atelier.domain.metric.FilterCondition;
import com.example.atelier.domain.metric.FilterGroup;
import com.example.atelier.domain.metric.FilterOperator;
import com.example.atelier.domain.query.CompiledQuery;
import com.example.atelier.domain.query.MetricQueryRequest;
import com.example.atelier.domain.query.QueryResult;
import com.example.atelier.query.service.MetricQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 指标 API — 降低使用门槛的统一入口。
 *
 * <p>旧版需理解 indexPk、relation、多种 getIndexData 重载；
 * 新版只需 metricCode + filters。
 */
@RestController
@RequestMapping("/api/v2/metrics")
public class MetricController {

    private final MetricQueryService queryService;

    public MetricController(MetricQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 查询指标数据。
     *
     * POST /api/v2/metrics/query
     * { "metricCodes": ["revenue"], "filters": [{"field":"dept_code","operator":"IN","values":["001"]}] }
     */
    @PostMapping("/query")
    public ApiResponse<QueryResult> query(@RequestBody MetricQueryApiRequest request) {
        return ApiResponse.ok(queryService.query(toDomainRequest(request)));
    }

    /**
     * 预览编译 SQL（调试）。
     *
     * GET /api/v2/metrics/{code}/sql
     */
    @GetMapping("/{code}/sql")
    public ApiResponse<Map<String, Object>> previewSql(@PathVariable String code) {
        MetricQueryRequest request = MetricQueryRequest.builder()
                .metricCodes(Collections.singletonList(code))
                .build();
        return ApiResponse.ok(toSqlPreviewResult(queryService.compileOnly(request)));
    }

    /**
     * 按当前过滤条件预览编译 SQL。
     *
     * POST /api/v2/metrics/sql/preview
     */
    @PostMapping("/sql/preview")
    public ApiResponse<Map<String, Object>> previewSqlWithFilters(@RequestBody MetricQueryApiRequest request) {
        return ApiResponse.ok(toSqlPreviewResult(queryService.compileOnly(toDomainRequest(request))));
    }

    private Map<String, Object> toSqlPreviewResult(CompiledQuery compiled) {
        Map<String, Object> result = new HashMap<>();
        result.put("sql", compiled.getSql());
        result.put("datasourceId", compiled.getDatasourceId());
        result.put("columns", compiled.getColumnLabels());
        return result;
    }

    private MetricQueryRequest toDomainRequest(MetricQueryApiRequest apiRequest) {
        List<FilterCondition> filters = null;
        if (apiRequest.getFilters() != null) {
            filters = apiRequest.getFilters().stream()
                    .map(this::toFilterCondition)
                    .collect(Collectors.toList());
        }
        List<FilterGroup> filterGroups = null;
        if (apiRequest.getFilterGroups() != null) {
            filterGroups = apiRequest.getFilterGroups().stream()
                    .map(group -> FilterGroup.builder()
                            .conditions(group.getConditions() == null ? null :
                                    group.getConditions().stream()
                                            .map(this::toFilterCondition)
                                            .collect(Collectors.toList()))
                            .build())
                    .collect(Collectors.toList());
        }
        return MetricQueryRequest.builder()
                .metricCodes(apiRequest.getMetricCodes())
                .filters(filters)
                .filterGroups(filterGroups)
                .pageIndex(apiRequest.getPageIndex())
                .pageSize(apiRequest.getPageSize())
                .build();
    }

    private FilterCondition toFilterCondition(MetricQueryApiRequest.FilterDto f) {
        return FilterCondition.builder()
                .field(f.getField())
                .operator(parseOperator(f.getOperator()))
                .values(f.getValues())
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
}
