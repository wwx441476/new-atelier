package com.yonyougov.atelier.api.dto;

import lombok.Data;

/**
 * 数据源创建/更新请求。
 */
@Data
public class DataSourceRequest {

    private String id;
    private String name;
    private String jdbcUrl;
    private String username;
    private String password;
    private String dbType;
    private Boolean enabled;
}
