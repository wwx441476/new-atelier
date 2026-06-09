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

    public static List<String> buildAddColumnStatements(DbType dbType,
                                                        String schemaCode,
                                                        String tableCode,
                                                        List<MetaTableField> fields) {
        validateIdentifier(tableCode, "表编码");
        if (schemaCode != null && !schemaCode.trim().isEmpty()) {
            validateIdentifier(schemaCode, "Schema");
        }
        if (fields == null || fields.isEmpty()) {
            return new ArrayList<>();
        }

        DbType resolvedDbType = dbType != null ? dbType : DbType.UNKNOWN;
        String qualifiedTable = qualifyTableName(schemaCode, tableCode);
        List<String> statements = new ArrayList<>();
        for (MetaTableField field : fields) {
            String fieldCode = field.getFieldCode();
            if (fieldCode == null || fieldCode.trim().isEmpty()) {
                continue;
            }
            validateIdentifier(fieldCode, "字段编码");
            statements.add(buildAddColumnStatement(resolvedDbType, qualifiedTable, field));
        }
        return statements;
    }

    public static String build(DbType dbType, String schemaCode, String tableCode, List<MetaTableField> fields) {
        validateIdentifier(tableCode, "表编码");
        if (schemaCode != null && !schemaCode.trim().isEmpty()) {
            validateIdentifier(schemaCode, "Schema");
        }
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

        String qualifiedTable = qualifyTableName(schemaCode, tableCode);
        return createTablePrefix(resolvedDbType) + qualifiedTable + " (\n  "
                + String.join(",\n  ", columnDefs) + "\n)";
    }

    private static String qualifyTableName(String schemaCode, String tableCode) {
        if (schemaCode == null || schemaCode.trim().isEmpty()) {
            return tableCode;
        }
        return schemaCode + "." + tableCode;
    }

    private static String buildAddColumnStatement(DbType dbType, String qualifiedTable, MetaTableField field) {
        String columnDef = buildAlterColumnDefinition(dbType, field);
        if (dbType == DbType.ORACLE || dbType == DbType.DM) {
            return "ALTER TABLE " + qualifiedTable + " ADD (" + columnDef + ")";
        }
        return "ALTER TABLE " + qualifiedTable + " ADD COLUMN " + columnDef;
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

    /**
     * 增量加列定义 — 已有数据的表不能 NOT NULL（无默认值时 H2/MySQL 等会复制数据失败）。
     * 全量建表仍可在 {@link #buildColumnDefinition} 中保留 NOT NULL。
     */
    private static String buildAlterColumnDefinition(DbType dbType, MetaTableField field) {
        return field.getFieldCode() + ' ' + mapColumnType(dbType, field);
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
