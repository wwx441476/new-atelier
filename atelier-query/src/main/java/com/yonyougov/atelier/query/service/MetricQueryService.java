package com.yonyougov.atelier.query.service;

import com.yonyougov.atelier.domain.query.CompiledQuery;
import com.yonyougov.atelier.domain.query.MetricQueryRequest;
import com.yonyougov.atelier.domain.query.QueryResult;
import com.yonyougov.atelier.metrics.compiler.MetricQueryCompiler;
import com.yonyougov.atelier.query.spi.QueryExecutor;

/**
 * 指标查询服务 — 编排「编译 + 执行」，对外单一入口。
 */
public class MetricQueryService {

    private final MetricQueryCompiler compiler;
    private final QueryExecutor executor;

    public MetricQueryService(MetricQueryCompiler compiler, QueryExecutor executor) {
        this.compiler = compiler;
        this.executor = executor;
    }

    /**
     * 查询指标数据。
     */
    public QueryResult query(MetricQueryRequest request) {
        CompiledQuery compiled = compiler.compile(request);
        return executor.execute(compiled, request.getPageIndex(), request.getPageSize());
    }

    /**
     * 仅编译 SQL（调试/预览用）。
     */
    public CompiledQuery compileOnly(MetricQueryRequest request) {
        return compiler.compile(request);
    }
}
