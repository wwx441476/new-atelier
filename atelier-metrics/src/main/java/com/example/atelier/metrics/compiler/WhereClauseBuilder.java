package com.example.atelier.metrics.compiler;

import com.example.atelier.domain.metric.FilterCondition;
import com.example.atelier.domain.metric.FilterOperator;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WHERE 子句构建 — 统一入口，替代旧版 addWhereSql 在 5 处重复。
 */
public final class WhereClauseBuilder {

    private WhereClauseBuilder() {
    }

    public static String build(List<FilterCondition> filters) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (FilterCondition filter : filters) {
            String part = buildOne(filter);
            if (StringUtils.isNotBlank(part)) {
                parts.add(part);
            }
        }
        return String.join(" AND ", parts);
    }

    private static String buildOne(FilterCondition filter) {
        if (filter.getOperator() == null || filter.getValues() == null || filter.getValues().isEmpty()) {
            return "";
        }
        String field = filter.getField();
        FilterOperator op = filter.getOperator();
        List<String> values = filter.getValues();

        switch (op) {
            case IN:
            case NOT_IN:
                return buildIn(field, values, op == FilterOperator.NOT_IN);
            case BETWEEN:
                if (values.size() >= 2) {
                    return field + " BETWEEN '" + escape(values.get(0)) + "' AND '" + escape(values.get(1)) + "'";
                }
                return "";
            case LIKE:
                return field + " LIKE '" + escape(values.get(0)) + "%'";
            default:
                return field + " " + op.getSqlToken() + " '" + escape(values.get(0)) + "'";
        }
    }

    private static String buildIn(String field, List<String> values, boolean notIn) {
        String joined = values.stream()
                .map(v -> "'" + escape(v) + "'")
                .collect(Collectors.joining(", "));
        String prefix = notIn ? " NOT IN (" : " IN (";
        return field + prefix + joined + ")";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
