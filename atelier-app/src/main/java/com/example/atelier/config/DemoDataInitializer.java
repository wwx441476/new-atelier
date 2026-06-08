package com.example.atelier.config;

import com.example.atelier.infra.datasource.DataSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;

/**
 * 演示数据初始化 — 在 H2 内存库中创建 orders/dept 表及样例数据。
 */
@Component
@org.springframework.core.annotation.Order(2)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final DataSourceRegistry registry;

    public DemoDataInitializer(DataSourceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (registry.getConfig("ds-demo") == null) {
            return;
        }
        try (Connection conn = registry.getConnection("ds-demo")) {
            runScript(conn, "schema.sql");
            runScript(conn, "data.sql");
            log.info("演示数据源 ds-demo 初始化完成");
        }
    }

    private void runScript(Connection conn, String classpathFile) throws Exception {
        ClassPathResource resource = new ClassPathResource(classpathFile);
        if (!resource.exists()) {
            return;
        }
        ScriptUtils.executeSqlScript(conn, resource);
    }
}
