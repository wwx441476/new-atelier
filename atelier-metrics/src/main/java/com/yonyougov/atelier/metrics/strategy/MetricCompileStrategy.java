package com.yonyougov.atelier.metrics.strategy;

import com.yonyougov.atelier.domain.metric.MetricType;
import com.yonyougov.atelier.metrics.compiler.CompileContext;
import com.yonyougov.atelier.metrics.compiler.SqlFragments;

/**
 * 指标编译策略 — 按类型拆分，替代旧版 DataIndexServiceImpl 中三分支散落 5 处。
 */
public interface MetricCompileStrategy {

    MetricType supportedType();

    SqlFragments compile(CompileContext context);
}
