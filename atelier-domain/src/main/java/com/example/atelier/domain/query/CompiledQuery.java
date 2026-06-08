package com.example.atelier.domain.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 编译后的可执行查询。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompiledQuery {

    private String sql;

    private String datasourceId;

    /** 结果列元信息：code -> label */
    private Map<String, String> columnLabels;

    /** 指标值列 code 列表 */
    private List<String> metricValueColumns;
}
