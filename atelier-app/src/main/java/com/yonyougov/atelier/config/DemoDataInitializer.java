package com.yonyougov.atelier.config;

import com.yonyougov.atelier.infra.datasource.DataSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

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
        try (Connection conn = registry.getConnection("ds-demo");
             Statement stmt = conn.createStatement()) {
            runScript(stmt, "schema.sql");
            runScript(stmt, "data.sql");
            log.info("演示数据源 ds-demo 初始化完成");
        }
    }

    private void runScript(Statement stmt, String classpathFile) throws Exception {
        ClassPathResource resource = new ClassPathResource(classpathFile);
        if (!resource.exists()) {
            return;
        }
        String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                stmt.execute(trimmed);
            }
        }
    }
}
