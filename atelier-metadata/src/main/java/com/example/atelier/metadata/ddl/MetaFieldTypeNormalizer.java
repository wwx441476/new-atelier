package com.example.atelier.metadata.ddl;

import java.util.Locale;

/**
 * 将 JDBC {@code TYPE_NAME} 规范为元数据统一类型（与演示数据、DDL 生成一致）。
 */
public final class MetaFieldTypeNormalizer {

    private MetaFieldTypeNormalizer() {
    }

    public static String normalize(String jdbcTypeName) {
        if (jdbcTypeName == null || jdbcTypeName.trim().isEmpty()) {
            return "VARCHAR";
        }
        String type = jdbcTypeName.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        switch (type) {
            case "VARCHAR":
            case "VARCHAR2":
            case "NVARCHAR":
            case "NVARCHAR2":
            case "CHARACTER_VARYING":
            case "CHAR_VARYING":
            case "NCHAR_VARYING":
            case "STRING":
            case "TEXT":
            case "CLOB":
            case "NCLOB":
            case "LONGVARCHAR":
            case "LONGNVARCHAR":
            case "CHAR":
            case "NCHAR":
                return "VARCHAR";
            case "INT":
            case "INTEGER":
            case "BIGINT":
            case "SMALLINT":
            case "TINYINT":
                return "INTEGER";
            case "DECIMAL":
            case "NUMERIC":
            case "NUMBER":
            case "DOUBLE":
            case "FLOAT":
            case "REAL":
                return "DECIMAL";
            case "DATE":
                return "DATE";
            case "TIMESTAMP":
            case "DATETIME":
            case "TIMESTAMP_WITH_TIMEZONE":
                return "TIMESTAMP";
            case "BOOLEAN":
            case "BIT":
                return "BOOLEAN";
            default:
                return type.contains("_") ? type.replace('_', ' ') : type;
        }
    }
}
