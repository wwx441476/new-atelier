package com.example.atelier.metadata.service;

import com.example.atelier.domain.datasource.DbColumnInfo;
import com.example.atelier.domain.datasource.DbTableInfo;
import com.example.atelier.domain.metadata.MetaTable;
import com.example.atelier.domain.metadata.MetaTableDdlResult;
import com.example.atelier.domain.metadata.MetaTableField;
import com.example.atelier.domain.metadata.MetaTableImportRequest;
import com.example.atelier.domain.metadata.MetaTableImportResult;
import com.example.atelier.metadata.ddl.MetaFieldTypeNormalizer;
import com.example.atelier.metadata.ddl.TableDdlBuilder;
import com.example.atelier.domain.query.QueryResult;
import com.example.atelier.infra.datasource.DataSourceConfig;
import com.example.atelier.infra.datasource.DataSourceRegistry;
import com.example.atelier.infra.datasource.DbType;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.jdbc.DatabaseBrowserService;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MetadataServiceImpl implements MetadataService {

    private static final Pattern SAFE_TABLE_CODE = Pattern.compile("^[A-Za-z0-9_]+$");

    private final MetaTableJpaRepository tableRepository;
    private final MetaTableFieldJpaRepository fieldRepository;
    private final DataSourceRegistry dataSourceRegistry;
    private final DatabaseBrowserService databaseBrowserService;
    private final JdbcTemplate jdbcTemplate;

    public MetadataServiceImpl(MetaTableJpaRepository tableRepository,
                               MetaTableFieldJpaRepository fieldRepository,
                               DataSourceRegistry dataSourceRegistry,
                               DatabaseBrowserService databaseBrowserService) {
        this.tableRepository = tableRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRegistry = dataSourceRegistry;
        this.databaseBrowserService = databaseBrowserService;
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
        entity.setSchemaCode(normalizeSchemaCode(table.getSchemaCode()));
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
        boolean creating = field.getId() == null;
        MetaTableFieldEntity entity = creating
                ? newFieldEntity(field)
                : fieldRepository.findById(field.getId()).orElse(newFieldEntity(field));
        entity.setPkMetaTable(field.getTableId());
        entity.setFieldCode(field.getFieldCode());
        entity.setFieldName(field.getFieldName());
        entity.setFieldType(field.getFieldType());
        entity.setFieldLength(field.getFieldLength());
        entity.setFieldPrecision(field.getFieldPrecision());
        entity.setNullable(field.getNullable() != null && field.getNullable() ? 1 : 0);
        if (creating) {
            int insertSort = resolveInsertSort(field);
            shiftSortForInsert(field.getTableId(), insertSort);
            entity.setSortNo(insertSort);
        } else {
            entity.setSortNo(field.getSort());
        }
        return toField(fieldRepository.save(entity));
    }

    private int resolveInsertSort(MetaTableField field) {
        if (field.getSort() != null) {
            return field.getSort();
        }
        return nextSortNo(field.getTableId());
    }

    private int nextSortNo(String tableId) {
        int max = 0;
        for (MetaTableFieldEntity existing : fieldRepository.findByPkMetaTableOrderBySortNoAsc(tableId)) {
            if (existing.getSortNo() != null && existing.getSortNo() > max) {
                max = existing.getSortNo();
            }
        }
        return max + 1;
    }

    /** 为新字段腾出排序位：sortNo >= insertSort 的已有字段顺延 +1 */
    private void shiftSortForInsert(String tableId, int insertSort) {
        List<MetaTableFieldEntity> fields = fieldRepository.findByPkMetaTableOrderBySortNoAsc(tableId);
        for (int i = fields.size() - 1; i >= 0; i--) {
            MetaTableFieldEntity existing = fields.get(i);
            if (existing.getSortNo() != null && existing.getSortNo() >= insertSort) {
                existing.setSortNo(existing.getSortNo() + 1);
                fieldRepository.save(existing);
            }
        }
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
                    String tableSchema = rs.getString("TABLE_SCHEM");
                    discovered.add(MetaTable.builder()
                            .tableCode(tableName)
                            .tableName(tableName)
                            .schemaCode(tableSchema)
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
    @Transactional
    public MetaTableImportResult importTablesFromDatabase(MetaTableImportRequest request) {
        if (request == null || request.getDatasourceId() == null || request.getDatasourceId().trim().isEmpty()) {
            throw new AtelierException("数据源不能为空");
        }
        if (request.getTableNames() == null || request.getTableNames().isEmpty()) {
            throw new AtelierException("请至少选择一张表");
        }
        String datasourceId = request.getDatasourceId().trim();
        String schemaCode = normalizeSchemaCode(request.getSchemaCode());
        String catalogCode = request.getCatalogCode();

        List<DbTableInfo> physicalTables = databaseBrowserService.listTables(datasourceId, schemaCode);
        Map<String, DbTableInfo> physicalByName = new LinkedHashMap<>();
        for (DbTableInfo table : physicalTables) {
            physicalByName.put(table.getName().toLowerCase(Locale.ROOT), table);
        }

        List<MetaTable> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String rawName : request.getTableNames()) {
            if (rawName == null || rawName.trim().isEmpty()) {
                continue;
            }
            String physicalName = rawName.trim();
            String tableCode = normalizeTableCode(physicalName);
            validateTableCode(tableCode);

            Optional<MetaTableEntity> existing = findExistingTable(datasourceId, schemaCode, tableCode);
            if (existing.isPresent()) {
                MetaTableEntity existingEntity = existing.get();
                int synced = syncMissingFieldsFromPhysicalTable(
                        datasourceId, schemaCode, physicalName, existingEntity.getPkMetaTable());
                if (synced > 0) {
                    imported.add(toTableWithFields(existingEntity));
                } else {
                    skipped.add(physicalName);
                }
                continue;
            }

            DbTableInfo physical = physicalByName.get(physicalName.toLowerCase(Locale.ROOT));
            if (physical == null) {
                throw new AtelierException("物理表不存在: " + qualifyTableName(schemaCode, physicalName));
            }

            String resolvedSchema = physical.getSchema() != null ? physical.getSchema() : schemaCode;
            MetaTableEntity entity = MetaTableEntity.builder()
                    .pkMetaTable(UUID.randomUUID().toString())
                    .catalogCode(catalogCode)
                    .tableCode(tableCode)
                    .tableName(physical.getRemarks() != null && !physical.getRemarks().trim().isEmpty()
                            ? physical.getRemarks().trim()
                            : tableCode)
                    .pkDatasource(datasourceId)
                    .schemaCode(normalizeSchemaCode(resolvedSchema))
                    .comments(physical.getRemarks())
                    .createTime(LocalDateTime.now())
                    .modifyTime(LocalDateTime.now())
                    .build();
            MetaTableEntity saved = tableRepository.save(entity);
            importFieldsFromPhysicalTable(datasourceId, resolvedSchema, physical.getName(), saved.getPkMetaTable());
            imported.add(toTableWithFields(saved));
        }

        return MetaTableImportResult.builder()
                .imported(imported)
                .skipped(skipped)
                .importedCount(imported.size())
                .skippedCount(skipped.size())
                .build();
    }

    private void importFieldsFromPhysicalTable(String datasourceId, String schema, String tableName,
                                               String metaTableId) {
        syncMissingFieldsFromPhysicalTable(datasourceId, schema, tableName, metaTableId);
    }

    @Override
    @Transactional
    public int syncFieldsFromPhysicalTable(String tableId) {
        MetaTableEntity entity = resolveTableEntity(tableId)
                .orElseThrow(() -> new AtelierException("元数据表不存在: " + tableId));
        if (entity.getPkDatasource() == null || entity.getTableCode() == null) {
            return 0;
        }
        String schema = normalizeSchemaCode(entity.getSchemaCode());
        return syncMissingFieldsFromPhysicalTable(
                entity.getPkDatasource(), schema, entity.getTableCode(), entity.getPkMetaTable());
    }

    private int syncMissingFieldsFromPhysicalTable(String datasourceId, String schema, String tableName,
                                                   String metaTableId) {
        List<DbColumnInfo> columns = databaseBrowserService.listColumns(datasourceId, schema, tableName);
        if (columns == null || columns.isEmpty()) {
            return 0;
        }
        Set<String> existingCodes = fieldRepository.findByPkMetaTableOrderBySortNoAsc(metaTableId).stream()
                .map(MetaTableFieldEntity::getFieldCode)
                .filter(code -> code != null && !code.trim().isEmpty())
                .map(code -> code.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int fallbackSort = nextSortNo(metaTableId);
        int synced = 0;
        for (DbColumnInfo column : columns) {
            String fieldCode = normalizeFieldCode(column.getName());
            if (fieldCode == null || existingCodes.contains(fieldCode.toLowerCase(Locale.ROOT))) {
                continue;
            }
            int sortNo = column.getOrdinalPosition() != null && column.getOrdinalPosition() > 0
                    ? column.getOrdinalPosition()
                    : fallbackSort;
            MetaTableFieldEntity fieldEntity = MetaTableFieldEntity.builder()
                    .pkMetaField(UUID.randomUUID().toString())
                    .pkMetaTable(metaTableId)
                    .fieldCode(fieldCode)
                    .fieldName(column.getRemarks() != null && !column.getRemarks().trim().isEmpty()
                            ? column.getRemarks().trim()
                            : fieldCode)
                    .fieldType(MetaFieldTypeNormalizer.normalize(column.getTypeName()))
                    .fieldLength(column.getColumnSize())
                    .fieldPrecision(column.getDecimalDigits())
                    .nullable(column.getNullable() != null && column.getNullable() ? 1 : 0)
                    .sortNo(sortNo)
                    .build();
            fieldRepository.save(fieldEntity);
            existingCodes.add(fieldCode.toLowerCase(Locale.ROOT));
            fallbackSort = Math.max(fallbackSort, sortNo) + 1;
            synced++;
        }
        return synced;
    }

    private Optional<MetaTableEntity> findExistingTable(String datasourceId, String schemaCode, String tableCode) {
        return tableRepository.findByPkDatasource(datasourceId).stream()
                .filter(entity -> tableCode.equalsIgnoreCase(entity.getTableCode()))
                .filter(entity -> schemaMatchesForDuplicate(entity.getSchemaCode(), schemaCode))
                .findFirst();
    }

    /**
     * 同一数据源下，表编码相同即视为同一张物理表。
     * Schema 一方为空、另一方有值时仍视为重复（兼容历史数据未填 schema 的情况）。
     */
    private boolean schemaMatchesForDuplicate(String existing, String requested) {
        String left = blankToNull(existing);
        String right = blankToNull(requested);
        if (left == null || right == null) {
            return true;
        }
        return left.equalsIgnoreCase(right);
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeTableCode(String tableName) {
        return tableName.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeFieldCode(String columnName) {
        if (columnName == null || columnName.trim().isEmpty()) {
            return null;
        }
        String normalized = columnName.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_TABLE_CODE.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
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
        String schemaCode = entity.getSchemaCode();
        String ddl = TableDdlBuilder.build(dbType, schemaCode, tableCode, fields);

        boolean tableExists = false;
        List<MetaTableField> missingFields = Collections.emptyList();
        List<String> alterStatements = Collections.emptyList();
        try (Connection connection = dataSourceRegistry.getConnection(datasourceId)) {
            tableExists = physicalTableExists(connection, schemaCode, tableCode);
            if (tableExists) {
                Set<String> physicalColumns = listPhysicalColumnNames(connection, schemaCode, tableCode);
                missingFields = findMissingFields(fields, physicalColumns);
                if (!missingFields.isEmpty()) {
                    alterStatements = TableDdlBuilder.buildAddColumnStatements(
                            dbType, schemaCode, tableCode, missingFields);
                }
            }
        } catch (Exception e) {
            throw new AtelierException("检查物理表是否存在失败: " + e.getMessage(), e);
        }

        String alterDdl = alterStatements.isEmpty() ? null : String.join(";\n", alterStatements);
        List<String> missingFieldCodes = missingFields.stream()
                .map(MetaTableField::getFieldCode)
                .collect(Collectors.toList());

        return MetaTableDdlResult.builder()
                .ddl(ddl)
                .alterDdl(alterDdl)
                .missingFieldCodes(missingFieldCodes)
                .syncNeeded(!missingFields.isEmpty())
                .tableExists(tableExists)
                .datasourceId(datasourceId)
                .tableCode(tableCode)
                .build();
    }

    @Override
    public void executeSyncTable(String tableId) {
        MetaTableEntity entity = resolveTableEntity(tableId)
                .orElseThrow(() -> new AtelierException("元数据表不存在: " + tableId));
        MetaTableDdlResult preview = buildCreateTableDdl(tableId);
        if (!preview.isTableExists()) {
            throw new AtelierException("物理表不存在，请先执行建表: " + preview.getTableCode());
        }
        if (!preview.isSyncNeeded()) {
            throw new AtelierException("物理表字段已与元数据一致，无需同步");
        }

        DbType dbType = resolveDbType(entity.getPkDatasource());
        List<MetaTableField> fields = listFields(entity.getPkMetaTable());
        try (Connection connection = dataSourceRegistry.getConnection(entity.getPkDatasource())) {
            Set<String> physicalColumns = listPhysicalColumnNames(
                    connection, entity.getSchemaCode(), entity.getTableCode());
            List<MetaTableField> missingFields = findMissingFields(fields, physicalColumns);
            List<String> statements = TableDdlBuilder.buildAddColumnStatements(
                    dbType, entity.getSchemaCode(), entity.getTableCode(), missingFields);
            jdbcTemplate.executeAll(connection, statements);
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("增量同步执行失败: " + e.getMessage(), e);
        }
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
        String sql = buildPreviewSql(entity.getSchemaCode(), tableCode, previewFields);

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

    private DbType resolveDbType(String datasourceId) {
        DataSourceConfig config = dataSourceRegistry.getConfig(datasourceId);
        if (config == null) {
            throw new AtelierException("数据源不存在: " + datasourceId);
        }
        return config.getDbType() != null ? config.getDbType() : DbType.UNKNOWN;
    }

    private Set<String> listPhysicalColumnNames(Connection connection, String schemaCode, String tableCode)
            throws java.sql.SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = resolvePhysicalSchema(connection, schemaCode);
        Set<String> columns = new HashSet<>();
        collectColumnNames(metaData, catalog, schema, tableCode, columns);
        if (columns.isEmpty() && !tableCode.equals(tableCode.toUpperCase(Locale.ROOT))) {
            collectColumnNames(metaData, catalog, schema, tableCode.toUpperCase(Locale.ROOT), columns);
        }
        return columns;
    }

    private void collectColumnNames(DatabaseMetaData metaData,
                                    String catalog,
                                    String schema,
                                    String tableCode,
                                    Set<String> columns) throws java.sql.SQLException {
        try (ResultSet rs = metaData.getColumns(catalog, schema, tableCode, null)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                if (name != null) {
                    columns.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    private List<MetaTableField> findMissingFields(List<MetaTableField> metadataFields,
                                                  Set<String> physicalColumns) {
        if (metadataFields == null || metadataFields.isEmpty()) {
            return Collections.emptyList();
        }
        List<MetaTableField> missing = new ArrayList<>();
        for (MetaTableField field : metadataFields) {
            String code = field.getFieldCode();
            if (code == null || code.trim().isEmpty()) {
                continue;
            }
            if (!physicalColumns.contains(code.toLowerCase(Locale.ROOT))) {
                missing.add(field);
            }
        }
        return missing;
    }

    private boolean physicalTableExists(Connection connection, String schemaCode, String tableCode)
            throws java.sql.SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = resolvePhysicalSchema(connection, schemaCode);
        if (tableExistsInSchema(metaData, catalog, schema, tableCode)) {
            return true;
        }
        if (!tableCode.equals(tableCode.toUpperCase())) {
            return tableExistsInSchema(metaData, catalog, schema, tableCode.toUpperCase());
        }
        return false;
    }

    private boolean tableExistsInSchema(DatabaseMetaData metaData, String catalog, String schema, String tableCode)
            throws java.sql.SQLException {
        try (ResultSet rs = metaData.getTables(catalog, schema, tableCode, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private String resolvePhysicalSchema(Connection connection, String schemaCode) throws java.sql.SQLException {
        if (schemaCode != null && !schemaCode.trim().isEmpty()) {
            return schemaCode.trim();
        }
        String schema = connection.getSchema();
        if (schema != null && !schema.isEmpty()) {
            return schema;
        }
        String user = connection.getMetaData().getUserName();
        return user != null ? user.toUpperCase() : null;
    }

    private String normalizeSchemaCode(String schemaCode) {
        if (schemaCode == null) {
            return null;
        }
        String trimmed = schemaCode.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        validateSchemaCode(trimmed);
        return trimmed;
    }

    private void validateSchemaCode(String schemaCode) {
        if (!SAFE_TABLE_CODE.matcher(schemaCode).matches()) {
            throw new AtelierException("非法 Schema，仅允许字母、数字与下划线: " + schemaCode);
        }
    }

    private String qualifyTableName(String schemaCode, String tableCode) {
        if (schemaCode == null || schemaCode.trim().isEmpty()) {
            return tableCode;
        }
        validateSchemaCode(schemaCode.trim());
        return schemaCode.trim() + "." + tableCode;
    }

    private String buildPreviewSql(String schemaCode, String tableCode, List<MetaTableField> fields) {
        String qualifiedTable = qualifyTableName(schemaCode, tableCode);
        List<String> fieldCodes = resolvePreviewFieldCodes(fields);
        if (fieldCodes.isEmpty()) {
            return "SELECT * FROM " + qualifiedTable;
        }
        return "SELECT " + String.join(", ", fieldCodes) + " FROM " + qualifiedTable;
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
                .schemaCode(entity.getSchemaCode())
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
