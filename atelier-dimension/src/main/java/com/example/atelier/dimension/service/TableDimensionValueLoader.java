package com.example.atelier.dimension.service;

import com.example.atelier.domain.dimension.DimensionField;
import com.example.atelier.domain.dimension.DimensionValue;
import com.example.atelier.infra.datasource.DataSourceRegistry;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.jdbc.JdbcTemplate;
import com.example.atelier.infra.persistence.entity.MetaTableEntity;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 从物理表加载维度值（编码 / 名称 / 父编码列由维度字段映射指定）。
 */
@Component
public class TableDimensionValueLoader {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9_]+$");

    private final DataSourceRegistry dataSourceRegistry;
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate();

    public TableDimensionValueLoader(DataSourceRegistry dataSourceRegistry) {
        this.dataSourceRegistry = dataSourceRegistry;
    }

    public List<DimensionValue> load(String dimensionId,
                                     MetaTableEntity metaTable,
                                     List<DimensionField> fields) {
        if (metaTable == null) {
            throw new AtelierException("表数据源维度未关联元数据表");
        }
        String codeColumn = findMappedColumn(fields, true, false, false, "编码");
        String nameColumn = findMappedColumn(fields, false, true, false, "名称");
        String parentColumn = findMappedColumn(fields, false, false, true, "父编码");

        validateIdentifier(codeColumn, "编码字段");
        validateIdentifier(nameColumn, "名称字段");
        if (parentColumn != null) {
            validateIdentifier(parentColumn, "父编码字段");
        }

        String tableName = qualifyTableName(metaTable.getSchemaCode(), metaTable.getTableCode());
        validateIdentifier(metaTable.getTableCode(), "表编码");
        if (metaTable.getSchemaCode() != null && !metaTable.getSchemaCode().trim().isEmpty()) {
            validateIdentifier(metaTable.getSchemaCode().trim(), "Schema");
        }

        StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
        sql.append(codeColumn).append(" AS code, ");
        sql.append(nameColumn).append(" AS name");
        if (parentColumn != null) {
            sql.append(", ").append(parentColumn).append(" AS parent_code");
        }
        sql.append(" FROM ").append(tableName);
        sql.append(" WHERE ").append(codeColumn).append(" IS NOT NULL");
        sql.append(" ORDER BY ").append(codeColumn);

        String datasourceId = metaTable.getPkDatasource();
        try (Connection connection = dataSourceRegistry.getConnection(datasourceId)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(connection, sql.toString());
            List<DimensionValue> values = new ArrayList<>();
            int sort = 1;
            for (Map<String, Object> row : rows) {
                Object codeObj = firstPresent(row, "code", codeColumn);
                if (codeObj == null) {
                    continue;
                }
                String code = String.valueOf(codeObj);
                Object nameObj = firstPresent(row, "name", nameColumn);
                String name = nameObj != null ? String.valueOf(nameObj) : code;
                String parentCode = null;
                if (parentColumn != null) {
                    Object parentObj = firstPresent(row, "parent_code", parentColumn);
                    parentCode = parentObj != null ? String.valueOf(parentObj) : null;
                }
                values.add(DimensionValue.builder()
                        .id("tbl-" + dimensionId + "-" + code)
                        .dimensionId(dimensionId)
                        .code(code)
                        .name(name)
                        .parentCode(parentCode)
                        .sort(sort++)
                        .build());
            }
            return values;
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("从物理表加载维度值失败: " + e.getMessage(), e);
        }
    }

    private static String findMappedColumn(List<DimensionField> fields,
                                           boolean codeField,
                                           boolean nameField,
                                           boolean parentField,
                                           String label) {
        if (fields == null) {
            throw new AtelierException("表数据源维度未配置字段映射（" + label + "）");
        }
        for (DimensionField field : fields) {
            if (field.getFieldCode() == null || field.getFieldCode().trim().isEmpty()) {
                continue;
            }
            if (codeField && Boolean.TRUE.equals(field.getCodeField())) {
                return field.getFieldCode();
            }
            if (nameField && Boolean.TRUE.equals(field.getNameField())) {
                return field.getFieldCode();
            }
            if (parentField && Boolean.TRUE.equals(field.getParentField())) {
                return field.getFieldCode();
            }
        }
        if (parentField) {
            return null;
        }
        throw new AtelierException("表数据源维度未配置" + label + "字段映射");
    }

    private static Object firstPresent(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key) && row.get(key) != null) {
                return row.get(key);
            }
            String lower = key.toLowerCase();
            if (row.containsKey(lower) && row.get(lower) != null) {
                return row.get(lower);
            }
            String upper = key.toUpperCase();
            if (row.containsKey(upper) && row.get(upper) != null) {
                return row.get(upper);
            }
        }
        return null;
    }

    private static String qualifyTableName(String schemaCode, String tableCode) {
        if (schemaCode == null || schemaCode.trim().isEmpty()) {
            return tableCode;
        }
        return schemaCode.trim() + "." + tableCode;
    }

    private static void validateIdentifier(String value, String label) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new AtelierException(label + "非法，仅允许字母、数字与下划线: " + value);
        }
    }
}
