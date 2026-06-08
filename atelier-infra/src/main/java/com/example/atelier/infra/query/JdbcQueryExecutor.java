package com.example.atelier.infra.query;

import com.example.atelier.domain.query.CompiledQuery;
import com.example.atelier.domain.query.QueryResult;
import com.example.atelier.infra.datasource.DataSourceConfig;
import com.example.atelier.infra.datasource.DataSourceRegistry;
import com.example.atelier.infra.datasource.DbType;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.jdbc.JdbcTemplate;
import com.example.atelier.infra.jdbc.PageSqlBuilder;
import com.example.atelier.infra.jdbc.QueryResultMapper;
import com.example.atelier.query.spi.QueryExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JDBC 查询执行器 — 替代 StubQueryExecutor，对接真实数据源。
 *
 * <p>执行模式参考 DataIndexServiceImpl：
 * count 子查询 + 分页数据查询。
 */
public class JdbcQueryExecutor implements QueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(JdbcQueryExecutor.class);

    private final DataSourceRegistry registry;
    private final JdbcTemplate jdbcTemplate;

    public JdbcQueryExecutor(DataSourceRegistry registry) {
        this(registry, new JdbcTemplate());
    }

    public JdbcQueryExecutor(DataSourceRegistry registry, JdbcTemplate jdbcTemplate) {
        this.registry = registry;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public QueryResult execute(CompiledQuery query, int pageIndex, int pageSize) {
        if (query == null || query.getSql() == null || query.getSql().trim().isEmpty()) {
            throw new AtelierException("编译 SQL 为空");
        }
        String datasourceId = query.getDatasourceId();
        DataSourceConfig config = registry.getConfig(datasourceId);
        if (config == null) {
            throw new AtelierException("数据源不存在: " + datasourceId);
        }

        DbType dbType = config.getDbType() != null ? config.getDbType() : DbType.UNKNOWN;
        String sql = query.getSql().trim();

        try (Connection connection = registry.getConnection(datasourceId)) {
            long total = jdbcTemplate.count(connection, sql);
            List<Map<String, Object>> rows = Collections.emptyList();
            if (total > 0) {
                String pageSql = PageSqlBuilder.build(dbType, sql, pageIndex, pageSize);
                log.debug("执行分页查询 [{}]: {}", datasourceId, pageSql);
                rows = QueryResultMapper.mapRows(jdbcTemplate.queryForList(connection, pageSql), dbType);
            }
            return QueryResult.builder()
                    .total(total)
                    .rows(rows)
                    .headers(query.getColumnLabels())
                    .build();
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("指标查询执行失败", e);
        }
    }
}
