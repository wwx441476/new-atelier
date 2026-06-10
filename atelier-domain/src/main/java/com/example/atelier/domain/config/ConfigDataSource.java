package com.example.atelier.domain.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源配置导出项 — 不含 infra 依赖，便于 JSON 交换。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigDataSource {

    private String id;

    private String name;

    private String jdbcUrl;

    private String username;

    /** 导出时可置空；导入时空密码表示保留目标环境已有密码 */
    private String password;

    private String dbType;

    @Builder.Default
    private boolean enabled = true;
}
