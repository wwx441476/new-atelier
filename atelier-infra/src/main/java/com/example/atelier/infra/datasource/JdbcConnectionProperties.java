package com.example.atelier.infra.datasource;

import java.util.Map;
import java.util.Properties;

/**
 * JDBC 连接属性工具 — 统一应用于 HikariCP 与 DriverManager。
 */
public final class JdbcConnectionProperties {

    private JdbcConnectionProperties() {
    }

    public static Properties toDriverProperties(DataSourceConfig config) {
        Properties props = new Properties();
        if (config.getUsername() != null) {
            props.setProperty("user", config.getUsername());
        }
        if (config.getPassword() != null) {
            props.setProperty("password", config.getPassword());
        }
        applyConnectionProperties(props, config.getConnectionProperties());
        return props;
    }

    public static void applyConnectionProperties(Properties target, Map<String, String> connectionProperties) {
        if (target == null || connectionProperties == null || connectionProperties.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : connectionProperties.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().trim().isEmpty() && entry.getValue() != null) {
                target.setProperty(entry.getKey().trim(), entry.getValue());
            }
        }
    }
}
