package com.example.atelier.warning.evaluator;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从元数据表预览行解析指标变量（同行粒度）。
 * 演示场景：orders 表 profit = amount - cost_amount。
 */
public final class RowMetricContextResolver {

    private RowMetricContextResolver() {
    }

    public static Map<String, Object> buildContext(Map<String, Object> row, List<String> metricCodes) {
        Map<String, Object> context = new HashMap<>();
        if (row == null || metricCodes == null) {
            return context;
        }
        for (String code : metricCodes) {
            Object value = resolveValue(row, code);
            if (value != null) {
                context.put(code, value);
            }
        }
        return context;
    }

    static Object resolveValue(Map<String, Object> row, String code) {
        if (row.containsKey(code)) {
            return row.get(code);
        }
        if ("profit".equals(code) && row.containsKey("amount") && row.containsKey("cost_amount")) {
            return toDouble(row.get("amount")) - toDouble(row.get("cost_amount"));
        }
        if ("revenue".equals(code) && row.containsKey("amount")) {
            return row.get("amount");
        }
        if ("cost".equals(code) && row.containsKey("cost_amount")) {
            return row.get("cost_amount");
        }
        return null;
    }

    private static double toDouble(Object value) {
        if (value == null) {
            return 0D;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
