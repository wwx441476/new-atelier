package com.yonyougov.atelier.infra.jdbc;

import com.yonyougov.atelier.infra.datasource.DbType;

/**
 * 分页 SQL 构建 — 移植自 dmp-atelier SQLBasedUtils.getPageSql。
 */
public final class PageSqlBuilder {

    private PageSqlBuilder() {
    }

    public static String build(DbType dbType, String sql, int pageIndex, int pageSize) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }
        int page = pageIndex <= 0 ? 1 : pageIndex;
        int size = pageSize <= 0 ? 20 : pageSize;
        int offset = (page - 1) * size;

        if (dbType == null) {
            dbType = DbType.UNKNOWN;
        }

        switch (dbType) {
            case ORACLE:
            case DM:
                return "SELECT * FROM ( SELECT A.*, ROWNUM RN FROM (" + sql + ") A WHERE ROWNUM <= "
                        + (page * size) + " ) WHERE RN > " + offset;
            case MYSQL:
            case POSTGRESQL:
            case KINGBASE:
            case DB2:
            case STARROCKS:
            case H2:
            case UNKNOWN:
            default:
                return sql + " LIMIT " + size + " OFFSET " + offset;
        }
    }

    /**
     * 构建 count 子查询 — 对应 SQLBasedUtils.getCountSql / DataIndexServiceImpl 模式。
     */
    public static String buildCountSql(String sql) {
        return "SELECT COUNT(*) FROM (" + sql + ") ATELIER_TMP";
    }
}
