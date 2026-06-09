package com.example.atelier.domain.warning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 预警规则数据预览结果 — 指标查询行 + 表达式触发标记。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarningRulePreviewResult {

    private String ruleId;

    private String ruleName;

    private String expression;

    /** 关联指标编译后的查询 SQL */
    private String sql;

    /** 指标数据总行数 */
    private long total;

    /** 当前页触发预警的行数 */
    private long matchedCount;

    private List<Map<String, Object>> rows;

    private Map<String, String> headers;
}
