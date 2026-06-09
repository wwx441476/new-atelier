package com.example.atelier.metadata.ddl;

import com.example.atelier.domain.metadata.MetaTableField;
import com.example.atelier.infra.datasource.DbType;
import com.example.atelier.infra.exception.AtelierException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 根据元数据字段定义生成 CREATE TABLE DDL。
 */
public final class TableDdlBuilder {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9_]+$");

    private TableDdlBuilder() {
    }

    public static String build(DbType dbType, String tableCode, List<MetaTableField> fields) {
        validateIdentifier(tableCode, "表编码");
        if (fields == null || fields.isEmpty()) {
            throw new AtelierException("请先配置字段后再生成建表 DDL");
        }

        DbType resolvedDbType = dbType != null ? dbType : DbType.UNKNOWN;
        List<String> columnDefs = new ArrayList<>();
        for (MetaTableField field : fields) {
            String fieldCode = field.getFieldCode();
            if (fieldCode == null || fieldCode.trim().isEmpty()) {
                continue;
            }
            validateIdentifier(fieldCode, "字段编码");
            columnDefs.add(buildColumnDefinition(resolvedDbType, field));
        }
        if (columnDefs.isEmpty()) {
            throw new AtelierException("请先配置有效字段后再生成建表 DDL");
        }

        return createTablePrefix(resolvedDbType) + tableCode + " (\n  "
                + String.join(",\n  ", columnDefs) + "\n)";
    }

    private static String buildColumnDefinition(DbType dbType, MetaTableField field) {
        String fieldCode = field.getFieldCode();
        boolean primaryKey = isPrimaryKeyField(fieldCode);
        StringBuilder column = new StringBuilder();
        column.append(fieldCode).append(' ').append(mapColumnType(dbType, field));
        if (primaryKey) {
            if (supportsAutoIncrement(dbType, field.getFieldType())) {
                column.append(" AUTO_INCREMENT");
            }
            column.append(" PRIMARY KEY");
        } else if (Boolean.FALSE.equals(field.getNullable())) {
            column.append(" NOT NULL");
        }
        return column.toString();
    }

    private static boolean isPrimaryKeyField(String fieldCode) {
        return "id".equalsIgnoreCase(fieldCode);
    }

    private static boolean supportsAutoIncrement(DbType dbType, String fieldType) {
        if (fieldType == null) {
            return false;
        }
        String normalized = fieldType.toUpperCase(Locale.ROOT);
        if (!"INTEGER".equals(normalized) && !"INT".equals(normalized)) {
            return false;
        }
        return dbType == DbType.H2 || dbType == DbType.MYSQL;
    }

    private static String mapColumnType(DbType dbType, MetaTableField field) {
        String type = field.getFieldType() != null
                ? field.getFieldType().toUpperCase(Locale.ROOT)
                : "VARCHAR";
        Integer length = field.getFieldLength();
        Integer precision = field.getFieldPrecision();

        switch (type) {
            case "VARCHAR":
            case "STRING":
                int varcharLen = length != null && length > 0 ? length : 255;
                if (dbType == DbType.ORACLE || dbType == DbType.DM) {
                    return "VARCHAR2(" + varcharLen + ")";
                }
                return "VARCHAR(" + varcharLen + ")";
            case "INTEGER":
            case "INT":
                if (dbType == DbType.ORACLE || dbType == DbType.DM) {
                    return "NUMBER";
                }
                return "INT";
            case "DECIMAL":
            case "NUMERIC":
                int scale = precision != null && precision >= 0 ? precision : 2;
                int columnPrecision = length != null && length > 0 ? length : 18;
                return "DECIMAL(" + columnPrecision + "," + scale + ")";
            case "DATE":
                return "DATE";
            case "TIMESTAMP":
                return "TIMESTAMP";
            case "BOOLEAN":
                if (dbType == DbType.MYSQL) {
                    return "TINYINT(1)";
                }
                return "BOOLEAN";
            default:
                return "VARCHAR(255)";
        }
    }

    private static String createTablePrefix(DbType dbType) {
        switch (dbType) {
            case H2:
            case MYSQL:
            case POSTGRESQL:
            case STARROCKS:
                return "CREATE TABLE IF NOT EXISTS ";
            default:
                return "CREATE TABLE ";
        }
    }

    private static void validateIdentifier(String value, String label) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new AtelierException(label + "非法，仅允许字母、数字与下划线: " + value);
        }
    }
}
