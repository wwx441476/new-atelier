package com.example.atelier.infra.query;

import com.example.atelier.domain.query.CompiledQuery;
import com.example.atelier.domain.query.QueryResult;
import com.example.atelier.infra.datasource.DataSourceConfig;
import com.example.atelier.infra.datasource.DataSourceRegistry;
import com.example.atelier.infra.datasource.DbType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class JdbcQueryExecutorTest {

    private DataSourceRegistry registry;
    private JdbcQueryExecutor executor;

    @Before
    public void setUp() throws Exception {
        registry = new DataSourceRegistry();
        registry.register(DataSourceConfig.builder()
                .id("ds-test")
                .name("H2 Test")
                .jdbcUrl("jdbc:h2:mem:atelier_test;DB_CLOSE_DELAY=-1;MODE=MySQL")
                .username("sa")
                .password("")
                .dbType(DbType.H2)
                .build());

        try (Connection conn = registry.getConnection("ds-test");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS orders (dept_code VARCHAR(20), amount DECIMAL(18,2))");
            stmt.execute("DELETE FROM orders");
            stmt.execute("INSERT INTO orders VALUES ('001', 100.00)");
            stmt.execute("INSERT INTO orders VALUES ('001', 200.00)");
            stmt.execute("INSERT INTO orders VALUES ('002', 50.00)");
        }

        executor = new JdbcQueryExecutor(registry);
    }

    @After
    public void tearDown() {
        registry.close();
    }

    @Test
    public void shouldExecutePagedQueryWithCount() {
        Map<String, String> headers = new HashMap<>();
        headers.put("dept_code", "部门");
        headers.put("revenue", "收入");

        CompiledQuery query = CompiledQuery.builder()
                .sql("SELECT dept_code, SUM(amount) AS revenue FROM orders GROUP BY dept_code ORDER BY dept_code")
                .datasourceId("ds-test")
                .columnLabels(headers)
                .metricValueColumns(Collections.singletonList("revenue"))
                .build();

        QueryResult result = executor.execute(query, 1, 10);

        Assert.assertEquals(2, result.getTotal());
        Assert.assertEquals(2, result.getRows().size());
        Assert.assertEquals("001", result.getRows().get(0).get("dept_code"));
        Assert.assertEquals(300.0, ((Number) result.getRows().get(0).get("revenue")).doubleValue(), 0.01);
        Assert.assertEquals(headers, result.getHeaders());
    }

    @Test
    public void shouldReturnEmptyRowsWhenNoData() {
        CompiledQuery query = CompiledQuery.builder()
                .sql("SELECT dept_code FROM orders WHERE dept_code = '999'")
                .datasourceId("ds-test")
                .columnLabels(Collections.singletonMap("dept_code", "部门"))
                .build();

        QueryResult result = executor.execute(query, 1, 10);

        Assert.assertEquals(0, result.getTotal());
        Assert.assertTrue(result.getRows().isEmpty());
    }
}
