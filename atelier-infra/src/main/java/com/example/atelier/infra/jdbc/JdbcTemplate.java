package com.example.atelier.infra.jdbc;

import com.example.atelier.infra.exception.AtelierException;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 轻量 JDBC 模板 — 对应 Apache dbutils QueryRunner 用法。
 */
public class JdbcTemplate {

    private final QueryRunner queryRunner = new QueryRunner();

    /**
     * 执行分页数据查询，返回行列表。
     */
    public List<Map<String, Object>> queryForList(Connection connection, String sql) {
        try {
            List<Map<String, Object>> rows = queryRunner.query(connection, sql, new MapListHandler());
            return rows != null ? rows : Collections.emptyList();
        } catch (SQLException e) {
            throw new AtelierException("SQL 查询失败: " + sql, e);
        }
    }

    /**
     * 对子查询做 count，返回总记录数。
     */
    public long count(Connection connection, String sql) {
        String countSql = PageSqlBuilder.buildCountSql(sql);
        try {
            Number total = queryRunner.query(connection, countSql, new ScalarHandler<Number>());
            return total != null ? total.longValue() : 0L;
        } catch (SQLException e) {
            throw new AtelierException("COUNT 查询失败: " + countSql, e);
        }
    }

    /**
     * 执行 DDL / DML 更新语句。
     */
    public void execute(Connection connection, String sql) {
        try {
            queryRunner.update(connection, sql);
        } catch (SQLException e) {
            throw new AtelierException("SQL 执行失败: " + e.getMessage(), e);
        }
    }
}
