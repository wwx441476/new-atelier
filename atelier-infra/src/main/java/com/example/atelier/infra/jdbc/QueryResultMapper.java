package com.example.atelier.infra.jdbc;

import com.example.atelier.infra.datasource.DbType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询结果行映射 — 处理 Oracle/DM 分页产生的 RN 列等。
 */
public final class QueryResultMapper {

    private QueryResultMapper() {
    }

    /**
     * 将 dbutils 返回的 Map 列表转为统一格式，并过滤分页辅助列。
     */
    public static List<Map<String, Object>> mapRows(List<Map<String, Object>> rows, DbType dbType) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                if (shouldSkipColumn(key, dbType)) {
                    continue;
                }
                mapped.put(key.toLowerCase(), entry.getValue());
            }
            result.add(mapped);
        }
        return result;
    }

    private static boolean shouldSkipColumn(String columnName, DbType dbType) {
        if (dbType != null && dbType.usesRowNumPagination()) {
            return "rn".equalsIgnoreCase(columnName);
        }
        return false;
    }
}
