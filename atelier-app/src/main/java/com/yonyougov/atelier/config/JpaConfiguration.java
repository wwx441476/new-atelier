package com.yonyougov.atelier.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA 扫描配置 — 实体与仓储位于 atelier-infra 模块。
 */
@Configuration
@EntityScan(basePackages = "com.yonyougov.atelier.infra.persistence.entity")
@EnableJpaRepositories(basePackages = "com.yonyougov.atelier.infra.persistence.jpa")
public class JpaConfiguration {
}
