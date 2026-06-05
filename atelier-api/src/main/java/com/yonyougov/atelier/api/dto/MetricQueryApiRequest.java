package com.yonyougov.atelier.api.dto;

import lombok.Data;

import java.util.List;

/**
 * API 层查询请求 — 简化前端入参。
 */
@Data
public class MetricQueryApiRequest {

    private List<String> metricCodes;

    private List<FilterDto> filters;

    private int pageIndex = 1;

    private int pageSize = 20;

    @Data
    public static class FilterDto {
        private String field;
        private String operator;
        private List<String> values;
    }
}
