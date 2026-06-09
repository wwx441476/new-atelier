package com.example.atelier.domain.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 分页查询结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResult {

    private long total;

    private List<Map<String, Object>> rows;

    private Map<String, String> headers;

    /** 查询 SQL（预览场景返回） */
    private String sql;
}
