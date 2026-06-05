package com.yonyougov.atelier.metrics.compiler;

import com.yonyougov.atelier.domain.metric.FilterCondition;
import com.yonyougov.atelier.domain.metric.MetricDefinition;
import com.yonyougov.atelier.domain.model.MetricModel;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * 编译上下文 — 单次查询所需全部输入。
 */
@Value
@Builder
public class CompileContext {

    MetricDefinition metric;

    MetricModel model;

    List<FilterCondition> filters;

    /** 复合指标：已解析的依赖指标定义 code -> definition */
    Map<String, MetricDefinition> dependencyMetrics;

    /** 复合指标：依赖指标编译后的 FROM 子句片段 */
    Map<String, String> dependencyFromClauses;
}
