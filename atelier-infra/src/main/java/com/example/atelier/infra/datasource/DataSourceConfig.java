package com.example.atelier.infra.datasource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 数据源配置 — 对应 bd-platform 的 DataSourceVO 核心字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceConfig {

    /** 数据源唯一标识，对应旧版 dsPk */
    private String id;

    private String name;

    private String jdbcUrl;

    private String username;

    private String password;

    private DbType dbType;

    /** 是否启用，默认 true */
    @Builder.Default
    private boolean enabled = true;

    /** JDBC 连接属性，如 useSSL、serverTimezone 等 */
    private Map<String, String> connectionProperties;
}
