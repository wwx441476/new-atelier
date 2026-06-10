package com.example.atelier.infra.jdbc;

import com.example.atelier.domain.datasource.DbCreateTableColumn;
import com.example.atelier.domain.datasource.DbCreateTableRequest;
import com.example.atelier.infra.datasource.DbType;
import com.example.atelier.infra.exception.AtelierException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class BrowseDdlBuilder {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Pattern SAFE_COLUMN_TYPE = Pattern.compile("^[A-Za-z0-9_(), ]+$");

    private BrowseDdlBuilder() {
    }

    static String buildCreateTable(DbType dbType, DbCreateTableRequest request) {
        if (request == null) {
            throw new AtelierException("建表请求不能为空");
        }
        String tableName = request.getTableName();
        validateIdentifier(tableName, "表名");
        if (request.getSchema() != null && !request.getSchema().trim().isEmpty()) {
            validateIdentifier(request.getSchema().trim(), "Schema");
        }
        List<DbCreateTableColumn> columns = request.getColumns();
        if (columns == null || columns.isEmpty()) {
            throw new AtelierException("请至少定义一个字段");
        }

        DbType resolved = dbType != null ? dbType : DbType.UNKNOWN;
        List<String> columnDefs = new ArrayList<>();
        for (DbCreateTableColumn column : columns) {
            columnDefs.add(buildColumnDefinition(column));
        }
        if (columnDefs.isEmpty()) {
            throw new AtelierException("请至少定义一个有效字段");
        }

        boolean ifNotExists = request.getIfNotExists() == null || request.getIfNotExists();
        String qualifiedTable = qualifyTable(request.getSchema(), tableName);
        String prefix = createTablePrefix(resolved, ifNotExists);
        return prefix + qualifiedTable + " (\n  " + String.join(",\n  ", columnDefs) + "\n)";
    }

    private static String buildColumnDefinition(DbCreateTableColumn column) {
        String name = column.getName();
        validateIdentifier(name, "字段名");
        String type = column.getType();
        if (type == null || type.trim().isEmpty()) {
            throw new AtelierException("字段类型不能为空: " + name);
        }
        String normalizedType = type.trim().toUpperCase(Locale.ROOT);
        if (!SAFE_COLUMN_TYPE.matcher(normalizedType).matches()) {
            throw new AtelierException("字段类型非法: " + type);
        }
        StringBuilder builder = new StringBuilder();
        builder.append(name).append(' ').append(normalizedType);
        if (Boolean.TRUE.equals(column.getPrimaryKey())) {
            builder.append(" PRIMARY KEY");
        } else if (Boolean.FALSE.equals(column.getNullable())) {
            builder.append(" NOT NULL");
        }
        return builder.toString();
    }

    private static String createTablePrefix(DbType dbType, boolean ifNotExists) {
        if (ifNotExists && (dbType == DbType.H2 || dbType == DbType.MYSQL || dbType == DbType.POSTGRESQL)) {
            return "CREATE TABLE IF NOT EXISTS ";
        }
        return "CREATE TABLE ";
    }

    private static String qualifyTable(String schema, String table) {
        if (schema != null && !schema.trim().isEmpty()) {
            return schema.trim() + "." + table;
        }
        return table;
    }

    private static void validateIdentifier(String value, String label) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new AtelierException(label + "非法，仅允许字母、数字与下划线: " + value);
        }
    }
}
