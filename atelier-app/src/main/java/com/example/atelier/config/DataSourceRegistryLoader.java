package com.example.atelier.config;

import com.example.atelier.infra.datasource.DataSourceConfig;
import com.example.atelier.infra.datasource.DataSourceRegistry;
import com.example.atelier.infra.datasource.DbType;
import com.example.atelier.infra.persistence.service.DataSourcePersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时从数据库加载数据源；库为空时回退到 application.yml 配置（零配置演示）。
 */
@Component
@Order(1)
public class DataSourceRegistryLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSourceRegistryLoader.class);

    private final DataSourceRegistry registry;
    private final DataSourcePersistenceService persistenceService;
    private final DataSourceProperties properties;

    public DataSourceRegistryLoader(DataSourceRegistry registry,
                                    DataSourcePersistenceService persistenceService,
                                    DataSourceProperties properties) {
        this.registry = registry;
        this.persistenceService = persistenceService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<DataSourceConfig> fromDb = persistenceService.findAllEnabledConfigs();
        if (!fromDb.isEmpty()) {
            registry.registerAll(fromDb);
            log.info("已从数据库加载 {} 个数据源", fromDb.size());
            return;
        }
        log.info("数据库无数据源记录，回退到 application.yml 配置");
        properties.getDatasources().forEach(entry -> registry.register(DataSourceConfig.builder()
                .id(entry.getId())
                .name(entry.getName())
                .jdbcUrl(entry.getJdbcUrl())
                .username(entry.getUsername())
                .password(entry.getPassword())
                .dbType(DbType.fromString(entry.getDbType()))
                .enabled(true)
                .build()));
    }
}
