package com.example.atelier.domain.query;

import com.example.atelier.domain.metric.FilterCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 指标查询请求 — 统一入口，降低使用门槛。
 *
 * <p>示例：查 revenue 按部门、年度筛选，第 1 页 20 条。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricQueryRequest {

    /** 单指标或多指标 code */
    private List<String> metricCodes;

    /** 查询时过滤，替代旧版定义时固化 WHERE */
    private List<FilterCondition> filters;

    /** 需要返回的维度列（空则返回全部绑定维度） */
    private List<String> dimensionCodes;

    private int pageIndex = 1;

    private int pageSize = 20;

    /** 是否应用行级权限 */
    private boolean applyRowAuth = true;
}
