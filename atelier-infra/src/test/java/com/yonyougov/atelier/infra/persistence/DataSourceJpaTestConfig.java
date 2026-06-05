package com.yonyougov.atelier.infra.persistence;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@TestConfiguration
@EntityScan(basePackages = "com.yonyougov.atelier.infra.persistence.entity")
@EnableJpaRepositories(basePackages = "com.yonyougov.atelier.infra.persistence.jpa")
public class DataSourceJpaTestConfig {
}
