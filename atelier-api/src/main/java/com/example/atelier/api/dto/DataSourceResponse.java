package com.example.atelier.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 数据源响应（不含密码明文）。
 */
@Data
@Builder
public class DataSourceResponse {

    private String id;
    private String name;
    private String jdbcUrl;
    private String username;
    private String dbType;
    private boolean enabled;
    private java.util.Map<String, String> connectionProperties;
}
