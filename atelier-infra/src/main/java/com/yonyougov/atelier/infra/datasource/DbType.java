package com.yonyougov.atelier.infra.datasource;

/**
 * 数据库类型 — 对应 bd-platform 的 DBType。
 */
public enum DbType {

    ORACLE,
    MYSQL,
    DM,
    KINGBASE,
    POSTGRESQL,
    DB2,
    STARROCKS,
    H2,
    UNKNOWN;

    /**
     * 从配置字符串解析，忽略大小写。
     */
    public static DbType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return UNKNOWN;
        }
        try {
            return DbType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }

    /** Oracle 系分页会产生 ROWNUM/RN 列，结果映射时需过滤 */
    public boolean usesRowNumPagination() {
        return this == ORACLE || this == DM;
    }
}
