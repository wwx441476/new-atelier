package com.example.atelier.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class DbBrowseQueryRequest {

    /** 自定义 SELECT SQL */
    private String sql;

    private List<FilterDto> filters;

    private List<FilterGroupDto> filterGroups;

    private int pageIndex = 1;

    private int pageSize = 20;

    @Data
    public static class FilterDto {
        private String field;
        private String operator;
        private List<String> values;
    }

    @Data
    public static class FilterGroupDto {
        private List<FilterDto> conditions;
    }
}
