package com.yonyougov.atelier.query.spi;

import com.yonyougov.atelier.domain.query.CompiledQuery;
import com.yonyougov.atelier.domain.query.QueryResult;

/**
 * 查询执行 SPI — 由 atelier-infra 的 JdbcQueryExecutor 实现。
 */
public interface QueryExecutor {

    QueryResult execute(CompiledQuery query, int pageIndex, int pageSize);
}
