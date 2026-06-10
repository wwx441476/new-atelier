package com.example.atelier.infra.jdbc;

import com.example.atelier.domain.datasource.DbColumnInfo;
import com.example.atelier.domain.datasource.DbCreateTableRequest;
import com.example.atelier.domain.datasource.DbSchemaInfo;
import com.example.atelier.domain.datasource.DbTableInfo;
import com.example.atelier.domain.query.SqlExecuteResult;
import com.example.atelier.domain.metric.FilterCondition;
import com.example.atelier.domain.metric.FilterGroup;
import com.example.atelier.domain.query.QueryResult;
import com.example.atelier.infra.datasource.DataSourceRegistry;
import com.example.atelier.infra.datasource.DbType;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.metrics.compiler.WhereClauseBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DatabaseBrowserService {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9_]+$");

    private static final Set<String> SYSTEM_SCHEMAS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "INFORMATION_SCHEMA",
            "PG_CATALOG",
            "MYSQL",
            "PERFORMANCE_SCHEMA",
            "SYS",
            "SYSTEM"
    )));

    private final DataSourceRegistry registry;
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate();

    public DatabaseBrowserService(DataSourceRegistry registry) {
        this.registry = registry;
    }

    public List<DbSchemaInfo> listSchemas(String datasourceId) {
        ensureDatasourceExists(datasourceId);
        try (Connection connection = registry.getConnection(datasourceId)) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            LinkedHashSet<String> schemaNames = new LinkedHashSet<>();

            try (ResultSet rs = metaData.getSchemas()) {
                while (rs.next()) {
                    String schema = rs.getString("TABLE_SCHEM");
                    if (isUsableSchema(schema)) {
                        schemaNames.add(schema);
                    }
                }
            }

            if (schemaNames.isEmpty()) {
                String currentSchema = connection.getSchema();
                if (isUsableSchema(currentSchema)) {
                    schemaNames.add(currentSchema);
                }
            }
            if (schemaNames.isEmpty()) {
                schemaNames.add("PUBLIC");
            }

            List<DbSchemaInfo> schemas = new ArrayList<>();
            for (String schema : schemaNames) {
                schemas.add(DbSchemaInfo.builder()
                        .name(schema)
                        .catalog(catalog)
                        .build());
            }
            return schemas;
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("读取 Schema 列表失败: " + e.getMessage(), e);
        }
    }

    public List<DbTableInfo> listTables(String datasourceId, String schema) {
        ensureDatasourceExists(datasourceId);
        if (schema != null && !schema.trim().isEmpty()) {
            validateIdentifier(schema.trim(), "Schema");
        }
        try (Connection connection = registry.getConnection(datasourceId)) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schemaPattern = normalizeSchemaPattern(connection, schema);

            List<DbTableInfo> tables = new ArrayList<>();
            try (ResultSet rs = metaData.getTables(catalog, schemaPattern, "%",
                    new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (shouldSkipTable(tableName)) {
                        continue;
                    }
                    tables.add(DbTableInfo.builder()
                            .schema(rs.getString("TABLE_SCHEM"))
                            .name(tableName)
                            .type(rs.getString("TABLE_TYPE"))
                            .remarks(rs.getString("REMARKS"))
                            .build());
                }
            }
            tables.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return tables;
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("读取表列表失败: " + e.getMessage(), e);
        }
    }

    public List<DbColumnInfo> listColumns(String datasourceId, String schema, String table) {
        ensureDatasourceExists(datasourceId);
        validateIdentifier(table, "表名");
        if (schema != null && !schema.trim().isEmpty()) {
            validateIdentifier(schema.trim(), "Schema");
        }

        try (Connection connection = registry.getConnection(datasourceId)) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schemaPattern = normalizeSchemaPattern(connection, schema);

            List<DbColumnInfo> columns = new ArrayList<>();
            try (ResultSet rs = metaData.getColumns(catalog, schemaPattern, table, null)) {
                while (rs.next()) {
                    columns.add(DbColumnInfo.builder()
                            .name(rs.getString("COLUMN_NAME"))
                            .typeName(rs.getString("TYPE_NAME"))
                            .columnSize(readInt(rs, "COLUMN_SIZE"))
                            .decimalDigits(readInt(rs, "DECIMAL_DIGITS"))
                            .nullable(readNullable(rs.getInt("NULLABLE")))
                            .remarks(rs.getString("REMARKS"))
                            .ordinalPosition(readInt(rs, "ORDINAL_POSITION"))
                            .build());
                }
            }
            return columns;
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("读取字段列表失败: " + e.getMessage(), e);
        }
    }

    public QueryResult previewTableData(String datasourceId, String schema, String table,
                                        int pageIndex, int pageSize) {
        String sql = buildTableSelectSql(schema, table, null, null);
        return executePagedQuery(datasourceId, sql, pageIndex, pageSize);
    }

    public QueryResult previewTableWithFilters(String datasourceId, String schema, String table,
                                               List<FilterCondition> filters,
                                               List<FilterGroup> filterGroups,
                                               int pageIndex, int pageSize) {
        String sql = buildTableSelectSql(schema, table, filters, filterGroups);
        return executePagedQuery(datasourceId, sql, pageIndex, pageSize);
    }

    public QueryResult executeSelectQuery(String datasourceId, String sql, int pageIndex, int pageSize) {
        ensureDatasourceExists(datasourceId);
        String cleanSql = validateAndNormalizeSelectSql(sql);
        return executePagedQuery(datasourceId, cleanSql, pageIndex, pageSize);
    }

    public SqlExecuteResult executeWriteSql(String datasourceId, String sql) {
        ensureDatasourceExists(datasourceId);
        String cleanSql = validateAndNormalizeWriteSql(sql);
        String statementType = detectWriteStatementType(cleanSql);
        try (Connection connection = registry.getConnection(datasourceId)) {
            int affectedRows = jdbcTemplate.executeUpdate(connection, cleanSql);
            return SqlExecuteResult.builder()
                    .sql(cleanSql)
                    .statementType(statementType)
                    .affectedRows(affectedRows)
                    .message(buildWriteSuccessMessage(statementType, affectedRows))
                    .build();
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("SQL 执行失败: " + e.getMessage(), e);
        }
    }

    public SqlExecuteResult createTable(String datasourceId, DbCreateTableRequest request) {
        ensureDatasourceExists(datasourceId);
        String ddl = previewCreateTableDdl(datasourceId, request);
        return executeWriteSql(datasourceId, ddl);
    }

    public String previewCreateTableDdl(String datasourceId, DbCreateTableRequest request) {
        ensureDatasourceExists(datasourceId);
        DbType dbType = registry.getDbType(datasourceId);
        return BrowseDdlBuilder.buildCreateTable(dbType, request);
    }

    private QueryResult executePagedQuery(String datasourceId, String sql, int pageIndex, int pageSize) {
        ensureDatasourceExists(datasourceId);
        DbType dbType = registry.getDbType(datasourceId);
        int page = pageIndex <= 0 ? 1 : pageIndex;
        int size = pageSize <= 0 ? 20 : pageSize;

        try (Connection connection = registry.getConnection(datasourceId)) {
            long total = jdbcTemplate.count(connection, sql);
            List<Map<String, Object>> rows = Collections.emptyList();
            if (total > 0) {
                String pageSql = PageSqlBuilder.build(dbType, sql, page, size);
                rows = QueryResultMapper.mapRows(jdbcTemplate.queryForList(connection, pageSql), dbType);
            }
            return QueryResult.builder()
                    .total(total)
                    .rows(rows)
                    .headers(buildPreviewHeaders(rows))
                    .sql(sql)
                    .build();
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("查询执行失败: " + e.getMessage(), e);
        }
    }

    private String buildTableSelectSql(String schema, String table,
                                       List<FilterCondition> filters,
                                       List<FilterGroup> filterGroups) {
        validateIdentifier(table, "表名");
        String schemaName = schema != null && !schema.trim().isEmpty() ? schema.trim() : null;
        if (schemaName != null) {
            validateIdentifier(schemaName, "Schema");
        }
        String qualifiedTable = qualifyTable(schemaName, table);
        String sql = "SELECT * FROM " + qualifiedTable;
        String whereClause = WhereClauseBuilder.resolve(filters, filterGroups);
        if (StringUtils.isNotBlank(whereClause)) {
            sql += " WHERE " + whereClause;
        }
        return sql;
    }

    private String validateAndNormalizeWriteSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new AtelierException("SQL 不能为空");
        }
        String normalized = sql.trim().replaceAll(";+\\s*$", "");
        if (normalized.contains(";")) {
            throw new AtelierException("仅允许单条语句");
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!isAllowedWriteStatement(upper)) {
            throw new AtelierException("仅支持 CREATE TABLE / ALTER TABLE / DROP TABLE / INSERT / UPDATE / DELETE");
        }
        String[] forbidden = {
                "DROP DATABASE", "DROP SCHEMA", "TRUNCATE ", "GRANT ", "REVOKE ",
                "EXEC ", "EXECUTE ", "CALL ", "MERGE ", "ATTACH ", "DETACH "
        };
        for (String keyword : forbidden) {
            if (upper.contains(keyword)) {
                throw new AtelierException("SQL 包含不允许的关键字: " + keyword.trim());
            }
        }
        return normalized;
    }

    private boolean isAllowedWriteStatement(String upper) {
        return upper.startsWith("INSERT ")
                || upper.startsWith("UPDATE ")
                || upper.startsWith("DELETE ")
                || upper.startsWith("CREATE TABLE")
                || upper.startsWith("ALTER TABLE")
                || upper.startsWith("DROP TABLE");
    }

    private String detectWriteStatementType(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        if (upper.startsWith("CREATE TABLE")) {
            return "CREATE TABLE";
        }
        if (upper.startsWith("ALTER TABLE")) {
            return "ALTER TABLE";
        }
        if (upper.startsWith("DROP TABLE")) {
            return "DROP TABLE";
        }
        if (upper.startsWith("INSERT")) {
            return "INSERT";
        }
        if (upper.startsWith("UPDATE")) {
            return "UPDATE";
        }
        if (upper.startsWith("DELETE")) {
            return "DELETE";
        }
        return "WRITE";
    }

    private String buildWriteSuccessMessage(String statementType, int affectedRows) {
        if ("CREATE TABLE".equals(statementType) || "ALTER TABLE".equals(statementType)
                || "DROP TABLE".equals(statementType)) {
            return statementType + " 执行成功";
        }
        return statementType + " 执行成功，影响 " + affectedRows + " 行";
    }

    private String validateAndNormalizeSelectSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new AtelierException("SQL 不能为空");
        }
        String normalized = sql.trim().replaceAll(";+\\s*$", "");
        if (normalized.contains(";")) {
            throw new AtelierException("仅允许单条 SELECT 语句");
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("SELECT")) {
            throw new AtelierException("仅允许 SELECT 查询");
        }
        String[] forbidden = {"INSERT ", "UPDATE ", "DELETE ", "DROP ", "CREATE ", "ALTER ", "TRUNCATE "};
        for (String keyword : forbidden) {
            if (upper.contains(keyword)) {
                throw new AtelierException("SQL 包含不允许的关键字");
            }
        }
        return normalized;
    }

    private Map<String, String> buildPreviewHeaders(List<Map<String, Object>> rows) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (rows != null && !rows.isEmpty()) {
            for (String key : rows.get(0).keySet()) {
                headers.put(key, key);
            }
        }
        return headers;
    }

    private String qualifyTable(String schema, String table) {
        if (schema != null && !schema.isEmpty()) {
            return schema + "." + table;
        }
        return table;
    }

    private String normalizeSchemaPattern(Connection connection, String schema) throws SQLException {
        if (schema != null && !schema.trim().isEmpty()) {
            return schema.trim();
        }
        String currentSchema = connection.getSchema();
        return currentSchema != null && !currentSchema.isEmpty() ? currentSchema : null;
    }

    private boolean shouldSkipTable(String tableName) {
        if (tableName == null) {
            return true;
        }
        String upper = tableName.toUpperCase(Locale.ROOT);
        return upper.startsWith("ATELIER_") || "DMP_DATASOURCE".equals(upper);
    }

    private boolean isUsableSchema(String schema) {
        if (schema == null || schema.trim().isEmpty()) {
            return false;
        }
        return !SYSTEM_SCHEMAS.contains(schema.toUpperCase(Locale.ROOT));
    }

    private Integer readInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Boolean readNullable(int nullableFlag) {
        if (nullableFlag == DatabaseMetaData.columnNullable) {
            return true;
        }
        if (nullableFlag == DatabaseMetaData.columnNoNulls) {
            return false;
        }
        return null;
    }

    private void ensureDatasourceExists(String datasourceId) {
        if (registry.getConfig(datasourceId) == null) {
            throw new AtelierException("数据源不存在: " + datasourceId);
        }
    }

    private void validateIdentifier(String value, String label) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new AtelierException(label + "非法，仅允许字母、数字与下划线: " + value);
        }
    }
}
