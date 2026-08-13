package com.example.atelier.infra;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * atelier-infra 模块无独立 Boot 主类；供 {@code @DataJpaTest} 向上扫描定位。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = "com.example.atelier.infra.persistence.entity")
@EnableJpaRepositories(basePackages = "com.example.atelier.infra.persistence.jpa")
public class InfraTestApplication {
}
