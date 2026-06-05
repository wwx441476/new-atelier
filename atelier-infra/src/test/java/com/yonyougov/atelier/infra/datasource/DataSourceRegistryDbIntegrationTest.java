package com.yonyougov.atelier.infra.datasource;

import com.yonyougov.atelier.domain.query.CompiledQuery;
import com.yonyougov.atelier.domain.query.QueryResult;
import com.yonyougov.atelier.infra.persistence.DataSourceJpaTestConfig;
import com.yonyougov.atelier.infra.persistence.service.DataSourcePersistenceService;
import com.yonyougov.atelier.infra.query.JdbcQueryExecutor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;

/**
 * 集成测试：持久化数据源 → Registry 加载 → JdbcQueryExecutor 查询。
 */
@RunWith(SpringRunner.class)
@DataJpaTest
@Import({DataSourcePersistenceService.class, JdbcQueryExecutor.class, DataSourceJpaTestConfig.class})
public class DataSourceRegistryDbIntegrationTest {

    private static final String DS_ID = "ds-it";
    private static final String JDBC_URL = "jdbc:h2:mem:registry_it;DB_CLOSE_DELAY=-1;MODE=MySQL";

    @Autowired
    private DataSourcePersistenceService persistenceService;

    private DataSourceRegistry registry;
    private JdbcQueryExecutor executor;

    @Before
    public void setUp() throws Exception {
        registry = new DataSourceRegistry();
        executor = new JdbcQueryExecutor(registry);

        persistenceService.save(DataSourceConfig.builder()
                .id(DS_ID)
                .name("Integration H2")
                .jdbcUrl(JDBC_URL)
                .username("sa")
                .password("")
                .dbType(DbType.H2)
                .enabled(true)
                .build());

        registry.registerAll(persistenceService.findAllEnabledConfigs());

        try (Connection conn = registry.getConnection(DS_ID);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS orders (dept_code VARCHAR(20), amount DECIMAL(18,2))");
            stmt.execute("DELETE FROM orders");
            stmt.execute("INSERT INTO orders VALUES ('001', 150.00)");
        }
    }

    @After
    public void tearDown() {
        registry.close();
    }

    @Test
    public void shouldQueryThroughRegistryLoadedFromDb() {
        CompiledQuery query = CompiledQuery.builder()
                .sql("SELECT dept_code, SUM(amount) AS revenue FROM orders GROUP BY dept_code")
                .datasourceId(DS_ID)
                .columnLabels(Collections.singletonMap("revenue", "收入"))
                .metricValueColumns(Collections.singletonList("revenue"))
                .build();

        QueryResult result = executor.execute(query, 1, 10);

        Assert.assertEquals(1, result.getTotal());
        Assert.assertEquals("001", result.getRows().get(0).get("dept_code"));
        Assert.assertEquals(150.0, ((Number) result.getRows().get(0).get("revenue")).doubleValue(), 0.01);
    }
}
