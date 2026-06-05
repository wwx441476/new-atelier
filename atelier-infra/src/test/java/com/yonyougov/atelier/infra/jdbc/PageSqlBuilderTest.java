package com.yonyougov.atelier.infra.jdbc;

import com.yonyougov.atelier.infra.datasource.DbType;
import org.junit.Assert;
import org.junit.Test;

public class PageSqlBuilderTest {

    private static final String BASE_SQL = "SELECT dept_code, SUM(amount) FROM orders GROUP BY dept_code";

    @Test
    public void shouldBuildMysqlStylePageSql() {
        String pageSql = PageSqlBuilder.build(DbType.MYSQL, BASE_SQL, 2, 10);
        Assert.assertEquals(BASE_SQL + " LIMIT 10 OFFSET 10", pageSql);
    }

    @Test
    public void shouldBuildH2PageSql() {
        String pageSql = PageSqlBuilder.build(DbType.H2, BASE_SQL, 1, 5);
        Assert.assertEquals(BASE_SQL + " LIMIT 5 OFFSET 0", pageSql);
    }

    @Test
    public void shouldBuildOracleStylePageSql() {
        String pageSql = PageSqlBuilder.build(DbType.ORACLE, BASE_SQL, 2, 10);
        Assert.assertTrue(pageSql.contains("ROWNUM"));
        Assert.assertTrue(pageSql.contains("WHERE RN > 10"));
        Assert.assertTrue(pageSql.contains("ROWNUM <= 20"));
    }

    @Test
    public void shouldBuildDmPageSqlLikeOracle() {
        String pageSql = PageSqlBuilder.build(DbType.DM, BASE_SQL, 1, 20);
        Assert.assertTrue(pageSql.contains("ROWNUM RN"));
        Assert.assertTrue(pageSql.contains("WHERE RN > 0"));
    }

    @Test
    public void shouldNormalizeInvalidPageIndex() {
        String pageSql = PageSqlBuilder.build(DbType.MYSQL, BASE_SQL, 0, 10);
        Assert.assertTrue(pageSql.endsWith("LIMIT 10 OFFSET 0"));
    }

    @Test
    public void shouldBuildCountSql() {
        String countSql = PageSqlBuilder.buildCountSql(BASE_SQL);
        Assert.assertEquals("SELECT COUNT(*) FROM (" + BASE_SQL + ") ATELIER_TMP", countSql);
    }
}
