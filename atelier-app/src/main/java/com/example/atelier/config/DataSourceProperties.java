package com.example.atelier.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据源配置绑定 — application.yml 中 atelier.datasources。
 */
@Data
@ConfigurationProperties(prefix = "atelier")
public class DataSourceProperties {

    private List<DatasourceEntry> datasources = new ArrayList<>();

    @Data
    public static class DatasourceEntry {
        private String id;
        private String name;
        private String jdbcUrl;
        private String username;
        private String password;
        private String dbType;
    }
}
