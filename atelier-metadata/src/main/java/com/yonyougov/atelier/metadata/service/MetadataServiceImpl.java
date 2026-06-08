package com.yonyougov.atelier.metadata.service;

import com.yonyougov.atelier.domain.metadata.MetaTable;
import com.yonyougov.atelier.domain.metadata.MetaTableField;
import com.yonyougov.atelier.domain.query.QueryResult;
import com.yonyougov.atelier.infra.datasource.DataSourceConfig;
import com.yonyougov.atelier.infra.datasource.DataSourceRegistry;
import com.yonyougov.atelier.infra.datasource.DbType;
import com.yonyougov.atelier.infra.exception.AtelierException;
import com.yonyougov.atelier.infra.jdbc.JdbcTemplate;
import com.yonyougov.atelier.infra.jdbc.PageSqlBuilder;
import com.yonyougov.atelier.infra.jdbc.QueryResultMapper;
import com.yonyougov.atelier.infra.persistence.entity.MetaTableEntity;
import com.yonyougov.atelier.infra.persistence.entity.MetaTableFieldEntity;
import com.yonyougov.atelier.infra.persistence.jpa.MetaTableFieldJpaRepository;
import com.yonyougov.atelier.infra.persistence.jpa.MetaTableJpaRepository;
import com.yonyougov.atelier.metadata.spi.MetadataService;
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
        String sql = "SELECT * FROM " + tableCode;

        try (Connection connection = dataSourceRegistry.getConnection(datasourceId)) {
            long total = jdbcTemplate.count(connection, sql);
            List<Map<String, Object>> rows = Collections.emptyList();
            if (total > 0) {
                String pageSql = PageSqlBuilder.build(dbType, sql, page, size);
                rows = QueryResultMapper.mapRows(jdbcTemplate.queryForList(connection, pageSql), dbType);
            }
            return QueryResult.builder()
                    .total(total)
                    .rows(rows)
                    .headers(buildPreviewHeaders(entity.getPkMetaTable(), rows))
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

    private Map<String, String> buildPreviewHeaders(String tableId, List<Map<String, Object>> rows) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (MetaTableField field : listFields(tableId)) {
            String code = field.getFieldCode();
            if (code != null) {
                headers.put(code.toLowerCase(),
                        field.getFieldName() != null ? field.getFieldName() : code);
            }
        }
        if (rows != null && !rows.isEmpty()) {
            for (String key : rows.get(0).keySet()) {
                headers.putIfAbsent(key, key);
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
