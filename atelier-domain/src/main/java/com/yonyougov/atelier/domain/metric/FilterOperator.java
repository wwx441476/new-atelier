package com.yonyougov.atelier.domain.metric;

/**
 * 过滤操作符 — 查询时传入，不固化在指标定义中。
 */
public enum FilterOperator {
    EQ("="),
    NE("!="),
    GT(">"),
    GE(">="),
    LT("<"),
    LE("<="),
    IN("in"),
    NOT_IN("not in"),
    BETWEEN("between"),
    LIKE("like");

    private final String sqlToken;

    FilterOperator(String sqlToken) {
        this.sqlToken = sqlToken;
    }

    public String getSqlToken() {
        return sqlToken;
    }
}
