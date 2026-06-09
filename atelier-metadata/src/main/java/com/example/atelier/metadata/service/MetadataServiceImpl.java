package com.example.atelier.metadata.service;

import com.example.atelier.domain.metadata.MetaTable;
import com.example.atelier.domain.metadata.MetaTableDdlResult;
import com.example.atelier.domain.metadata.MetaTableField;
import com.example.atelier.metadata.ddl.TableDdlBuilder;
import com.example.atelier.domain.query.QueryResult;
import com.example.atelier.infra.datasource.DataSourceConfig;
import com.example.atelier.infra.datasource.DataSourceRegistry;
import com.example.atelier.infra.datasource.DbType;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.jdbc.JdbcTemplate;
import com.example.atelier.infra.jdbc.PageSqlBuilder;
import com.example.atelier.infra.jdbc.QueryResultMapper;
import com.example.atelier.infra.persistence.entity.MetaTableEntity;
import com.example.atelier.infra.persistence.entity.MetaTableFieldEntity;
import com.example.atelier.infra.persistence.jpa.MetaTableFieldJpaRepository;
import com.example.atelier.infra.persistence.jpa.MetaTableJpaRepository;
import com.example.atelier.metadata.spi.MetadataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MetadataServiceImpl implements MetadataService {

    private static final Pattern SAFE_TABLE_CODE = Pattern.compile("^[A-Za-z0-9_]+$");

    private final MetaTableJpaRepository tableRepository;
    private final MetaTableFieldJpaRepository fieldRepository;
    private final DataSourceRegistry dataSourceRegistry;
    private final JdbcTemplate jdbcTemplate;

    public MetadataServiceImpl(MetaTableJpaRepository tableRepository,
                               MetaTableFieldJpaRepository fieldRepository,
                               DataSourceRegistry dataSourceRegistry) {
        this.tableRepository = tableRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRegistry = dataSourceRegistry;
        this.jdbcTemplate = new JdbcTemplate();
    }

    @Override
    public List<MetaTable> listTables() {
        return tableRepository.findAll().stream().map(this::toTable).collect(Collectors.toList());
    }

    @Override
    public List<MetaTable> listTablesByDatasource(String datasourceId) {
        return tableRepository.findByPkDatasource(datasourceId).stream()
                .map(this::toTable)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<MetaTable> getTable(String id) {
        return tableRepository.findById(id).map(this::toTableWithFields);
    }

    @Override
    @Transactional
    public MetaTable saveTable(MetaTable table) {
        if (table.getTableCode() == null || table.getTableCode().trim().isEmpty()) {
            throw new AtelierException("表编码不能为空");
        }
        MetaTableEntity entity = table.getId() != null
                ? tableRepository.findById(table.getId()).orElse(newEntity(table))
                : newEntity(table);
        entity.setCatalogCode(table.getCatalogCode());
        entity.setTableCode(table.getTableCode());
        entity.setTableName(table.getTableName());
        entity.setPkDatasource(table.getDatasourceId());
        entity.setComments(table.getComments());
        entity.setModifyTime(LocalDateTime.now());
        MetaTableEntity saved = tableRepository.save(entity);
        return toTable(saved);
    }

    @Override
    @Transactional
    public void deleteTable(String id) {
        fieldRepository.deleteByPkMetaTable(id);
        tableRepository.deleteById(id);
    }

    @Override
    public List<MetaTableField> listFields(String tableId) {
        return fieldRepository.findByPkMetaTableOrderBySortNoAsc(tableId).stream()
                .map(this::toField)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MetaTableField saveField(MetaTableField field) {
        if (field.getTableId() == null) {
            throw new AtelierException("tableId 不能为空");
        }
        MetaTableFieldEntity entity = field.getId() != null
                ? fieldRepository.findById(field.getId()).orElse(newFieldEntity(field))
                : newFieldEntity(field);
        entity.setPkMetaTable(field.getTableId());
        entity.setFieldCode(field.getFieldCode());
        entity.setFieldName(field.getFieldName());
        entity.setFieldType(field.getFieldType());
        entity.setFieldLength(field.getFieldLength());
        entity.setFieldPrecision(field.getFieldPrecision());
        entity.setNullable(field.getNullable() != null && field.getNullable() ? 1 : 0);
        entity.setSortNo(field.getSort());
        return toField(fieldRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteField(String fieldId) {
        fieldRepository.deleteById(fieldId);
    }

    @Override
    public List<MetaTable> discoverTables(String datasourceId) {
        List<MetaTable> discovered = new ArrayList<>();
        try (Connection connection = dataSourceRegistry.getConnection(datasourceId)) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (tableName == null || tableName.startsWith("ATELIER_") || tableName.equals("DMP_DATASOURCE")) {
                        continue;
                    }
                    discovered.add(MetaTable.builder()
                            .tableCode(tableName)
                            .tableName(tableName)
                            .datasourceId(datasourceId)
                            .build());
                }
            }
        } catch (Exception e) {
            throw new AtelierException("JDBC 表发现失败: " + e.getMessage(), e);
        }
        return discovered;
    }

    @Override
    public MetaTableDdlResult buildCreateTableDdl(String tableId) {
        MetaTableEntity entity = resolveTableEntity(tableId)
                .orElseThrow(() -> new AtelierException("元数据表不存在: " + tableId));
        String tableCode = entity.getTableCode();
        validateTableCode(tableCode);

        String datasourceId = entity.getPkDatasource();
        DataSourceConfig config = dataSourceRegistry.getConfig(datasourceId);
        if (config == null) {
            throw new AtelierException("数据源不存在: " + datasourceId);
        }
        DbType dbType = config.getDbType() != null ? config.getDbType() : DbType.UNKNOWN;
        List<MetaTableField> fields = listFields(entity.getPkMetaTable());
        String ddl = TableDdlBuilder.build(dbType, tableCode, fields);

        boolean tableExists = false;
        try (Connection connection = dataSourceRegistry.getConnection(datasourceId)) {
            tableExists = physicalTableExists(connection, tableCode);
        } catch (Exception e) {
            throw new AtelierException("检查物理表是否存在失败: " + e.getMessage(), e);
        }

        return MetaTableDdlResult.builder()
                .ddl(ddl)
                .tableExists(tableExists)
                .datasourceId(datasourceId)
                .tableCode(tableCode)
                .build();
    }

    @Override
    public void executeCreateTable(String tableId) {
        MetaTableDdlResult ddlResult = buildCreateTableDdl(tableId);
        if (ddlResult.isTableExists()) {
            throw new AtelierException("物理表已存在: " + ddlResult.getTableCode());
        }

        try (Connection connection = dataSourceRegistry.getConnection(ddlResult.getDatasourceId())) {
            jdbcTemplate.execute(connection, ddlResult.getDdl());
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("建表执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public QueryResult previewTableData(String tableId, int pageIndex, int pageSize) {
        MetaTableEntity entity = resolveTableEntity(tableId)
                .orElseThrow(() -> new AtelierException("元数据表不存在: " + tableId));
        String tableCode = entity.getTableCode();
        validateTableCode(tableCode);

        String datasourceId = entity.getPkDatasource();
        DataSourceConfig config = dataSourceRegistry.getConfig(datasourceId);
        if (config == null) {
            throw new AtelierException("数据源不存在: " + datasourceId);
        }
        DbType dbType = config.getDbType() != null ? config.getDbType() : DbType.UNKNOWN;

        int page = pageIndex <= 0 ? 1 : pageIndex;
        int size = pageSize <= 0 ? 20 : pageSize;
        List<MetaTableField> previewFields = listFields(entity.getPkMetaTable());
        String sql = buildPreviewSql(tableCode, previewFields);

        try (Connection connection = dataSourceRegistry.getConnection(datasourceId)) {
            long total = jdbcTemplate.count(connection, sql);
            List<Map<String, Object>> rows = Collections.emptyList();
            if (total > 0) {
                String pageSql = PageSqlBuilder.build(dbType, sql, page, size);
                rows = filterPreviewRows(
                        QueryResultMapper.mapRows(jdbcTemplate.queryForList(connection, pageSql), dbType),
                        previewFields);
            }
            return QueryResult.builder()
                    .total(total)
                    .rows(rows)
                    .headers(buildPreviewHeaders(previewFields))
                    .sql(sql)
                    .build();
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("表数据预览失败: " + e.getMessage(), e);
        }
    }

    private Optional<MetaTableEntity> resolveTableEntity(String tableId) {
        if (tableId == null || tableId.trim().isEmpty()) {
            return Optional.empty();
        }
        Optional<MetaTableEntity> direct = tableRepository.findById(tableId);
        if (direct.isPresent()) {
            return direct;
        }
        // Backward compatibility: historical typo used "ml-" while seed uses "mt-".
        if (tableId.startsWith("ml-")) {
            Optional<MetaTableEntity> typoFixed = tableRepository.findById("mt-" + tableId.substring(3));
            if (typoFixed.isPresent()) {
                return typoFixed;
            }
        }
        return tableRepository.findByTableCode(tableId);
    }

    private void validateTableCode(String tableCode) {
        if (tableCode == null || !SAFE_TABLE_CODE.matcher(tableCode).matches()) {
            throw new AtelierException("非法表编码，仅允许字母、数字与下划线: " + tableCode);
        }
    }

    private boolean physicalTableExists(Connection connection, String tableCode) throws java.sql.SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = resolveSchema(connection);
        try (ResultSet rs = metaData.getTables(catalog, schema, tableCode, new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }
        if (!tableCode.equals(tableCode.toUpperCase())) {
            try (ResultSet rs = metaData.getTables(catalog, schema, tableCode.toUpperCase(), new String[]{"TABLE"})) {
                return rs.next();
            }
        }
        return false;
    }

    private String resolveSchema(Connection connection) throws java.sql.SQLException {
        String schema = connection.getSchema();
        if (schema != null && !schema.isEmpty()) {
            return schema;
        }
        String user = connection.getMetaData().getUserName();
        return user != null ? user.toUpperCase() : null;
    }

    private String buildPreviewSql(String tableCode, List<MetaTableField> fields) {
        List<String> fieldCodes = resolvePreviewFieldCodes(fields);
        if (fieldCodes.isEmpty()) {
            return "SELECT * FROM " + tableCode;
        }
        return "SELECT " + String.join(", ", fieldCodes) + " FROM " + tableCode;
    }

    private List<String> resolvePreviewFieldCodes(List<MetaTableField> fields) {
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> codes = new ArrayList<>();
        for (MetaTableField field : fields) {
            String code = field.getFieldCode();
            if (code == null || code.trim().isEmpty()) {
                continue;
            }
            validateFieldCode(code);
            codes.add(code);
        }
        return codes;
    }

    private List<Map<String, Object>> filterPreviewRows(List<Map<String, Object>> rows,
                                                        List<MetaTableField> fields) {
        List<String> fieldCodes = resolvePreviewFieldCodes(fields);
        if (fieldCodes.isEmpty() || rows == null || rows.isEmpty()) {
            return rows;
        }
        List<String> normalizedCodes = fieldCodes.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        List<Map<String, Object>> filtered = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            for (String code : normalizedCodes) {
                if (row.containsKey(code)) {
                    mapped.put(code, row.get(code));
                }
            }
            filtered.add(mapped);
        }
        return filtered;
    }

    private void validateFieldCode(String fieldCode) {
        if (!SAFE_TABLE_CODE.matcher(fieldCode).matches()) {
            throw new AtelierException("非法字段编码，仅允许字母、数字与下划线: " + fieldCode);
        }
    }

    private Map<String, String> buildPreviewHeaders(List<MetaTableField> fields) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (MetaTableField field : fields) {
            String code = field.getFieldCode();
            if (code != null) {
                headers.put(code.toLowerCase(),
                        field.getFieldName() != null ? field.getFieldName() : code);
            }
        }
        return headers;
    }

    private MetaTableEntity newEntity(MetaTable table) {
        return MetaTableEntity.builder()
                .pkMetaTable(table.getId() != null ? table.getId() : UUID.randomUUID().toString())
                .createTime(LocalDateTime.now())
                .build();
    }

    private MetaTableFieldEntity newFieldEntity(MetaTableField field) {
        return MetaTableFieldEntity.builder()
                .pkMetaField(field.getId() != null ? field.getId() : UUID.randomUUID().toString())
                .build();
    }

    private MetaTable toTable(MetaTableEntity entity) {
        return MetaTable.builder()
                .id(entity.getPkMetaTable())
                .catalogCode(entity.getCatalogCode())
                .tableCode(entity.getTableCode())
                .tableName(entity.getTableName())
                .datasourceId(entity.getPkDatasource())
                .comments(entity.getComments())
                .build();
    }

    private MetaTable toTableWithFields(MetaTableEntity entity) {
        MetaTable table = toTable(entity);
        table.setFields(listFields(entity.getPkMetaTable()));
        return table;
    }

    private MetaTableField toField(MetaTableFieldEntity entity) {
        return MetaTableField.builder()
                .id(entity.getPkMetaField())
                .tableId(entity.getPkMetaTable())
                .fieldCode(entity.getFieldCode())
                .fieldName(entity.getFieldName())
                .fieldType(entity.getFieldType())
                .fieldLength(entity.getFieldLength())
                .fieldPrecision(entity.getFieldPrecision())
                .nullable(entity.getNullable() != null && entity.getNullable() == 1)
                .sort(entity.getSortNo())
                .build();
    }
}
