package com.yonyougov.atelier.config;

import com.yonyougov.atelier.infra.datasource.DataSourceRegistry;
import com.yonyougov.atelier.infra.query.JdbcQueryExecutor;
import com.yonyougov.atelier.metrics.compiler.MetricQueryCompiler;
import com.yonyougov.atelier.metrics.spi.MetricDefinitionRepository;
import com.yonyougov.atelier.metrics.spi.MetricModelRepository;
import com.yonyougov.atelier.query.service.MetricQueryService;
import com.yonyougov.atelier.query.spi.QueryExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
public class AtelierConfiguration {

    @Bean(destroyMethod = "close")
    public DataSourceRegistry dataSourceRegistry() {
        return new DataSourceRegistry();
    }

    @Bean
    public QueryExecutor queryExecutor(DataSourceRegistry registry) {
        return new JdbcQueryExecutor(registry);
    }

    @Bean
    public MetricQueryCompiler metricQueryCompiler(MetricDefinitionRepository definitionRepository,
                                                    MetricModelRepository modelRepository) {
        return new MetricQueryCompiler(definitionRepository, modelRepository);
    }

    @Bean
    public MetricQueryService metricQueryService(MetricQueryCompiler compiler, QueryExecutor executor) {
        return new MetricQueryService(compiler, executor);
    }
}
