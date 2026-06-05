package com.yonyougov.atelier.infra.datasource;

import com.yonyougov.atelier.infra.exception.AtelierException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * HikariCP 连接池封装 — 对应 bd-platform ConnectionPoolManager 的单池能力。
 */
public class ConnectionPool implements AutoCloseable {

    private final HikariDataSource dataSource;

    public ConnectionPool(DataSourceConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword() != null ? config.getPassword() : "");
        hikariConfig.setPoolName("atelier-" + config.getId());
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(30_000);
        this.dataSource = new HikariDataSource(hikariConfig);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public void validate() {
        try (Connection conn = getConnection()) {
            if (!conn.isValid(3)) {
                throw new AtelierException("数据源连接不可用: " + dataSource.getPoolName());
            }
        } catch (SQLException e) {
            throw new AtelierException("数据源连接校验失败: " + dataSource.getPoolName(), e);
        }
    }
}
