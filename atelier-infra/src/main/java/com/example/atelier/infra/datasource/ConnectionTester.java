package com.example.atelier.infra.datasource;

import com.example.atelier.infra.exception.AtelierException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 数据源连接测试 — 不经过连接池，用于管理端校验配置。
 */
public final class ConnectionTester {

    private ConnectionTester() {
    }

    public static boolean test(DataSourceConfig config) {
        if (config == null || config.getJdbcUrl() == null) {
            throw new AtelierException("jdbcUrl 不能为空");
        }
        try {
            Properties props = JdbcConnectionProperties.toDriverProperties(config);
            try (Connection connection = DriverManager.getConnection(config.getJdbcUrl(), props)) {
                return connection.isValid(3);
            }
        } catch (SQLException e) {
            throw new AtelierException("连接测试失败: " + e.getMessage(), e);
        }
    }
}
