package com.example.atelier.metrics.compiler;

import lombok.Builder;
import lombok.Value;

/**
 * SQL 分段 — 对应旧版 IndexSegmentSqlDto，但仅编译器内部使用。
 */
@Value
@Builder
public class SqlFragments {

    String selectClause;

    String fromClause;

    String whereClause;

    String groupByClause;

    public String toSql() {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ").append(selectClause);
        sb.append(" FROM ").append(fromClause);
        if (whereClause != null && !whereClause.isEmpty()) {
            sb.append(" WHERE ").append(whereClause);
        }
        if (groupByClause != null && !groupByClause.isEmpty()) {
            sb.append(" GROUP BY ").append(groupByClause);
        }
        return sb.toString();
    }
}
