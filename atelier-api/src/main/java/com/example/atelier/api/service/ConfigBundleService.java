package com.example.atelier.api.service;

import com.example.atelier.dimension.spi.DimensionService;
import com.example.atelier.domain.config.AtelierConfigBundle;
import com.example.atelier.domain.config.ConfigDataSource;
import com.example.atelier.domain.config.ConfigImportOptions;
import com.example.atelier.domain.config.ConfigImportResult;
import com.example.atelier.domain.dimension.Dimension;
import com.example.atelier.domain.dimension.DimensionValue;
import com.example.atelier.domain.dimension.DimensionValueSource;
import com.example.atelier.domain.metadata.MetaTable;
import com.example.atelier.domain.metadata.MetaTableField;
import com.example.atelier.domain.metric.MetricDefinition;
import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.infra.datasource.DataSourceConfig;
import com.example.atelier.infra.datasource.DataSourceRegistry;
import com.example.atelier.infra.datasource.DbType;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.persistence.service.DataSourcePersistenceService;
import com.example.atelier.infra.persistence.service.MetricDefinitionService;
import com.example.atelier.metadata.spi.MetadataService;
import com.example.atelier.warning.spi.WarningRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * 全量配置导出 / 导入 — 支持跨环境 JSON 迁移。
 */
@Service
public class ConfigBundleService {

    private final DataSourcePersistenceService dataSourceService;
    private final DataSourceRegistry dataSourceRegistry;
    private final MetadataService metadataService;
    private final DimensionService dimensionService;
    private final MetricDefinitionService metricDefinitionService;
    private final WarningRuleService warningRuleService;

    public ConfigBundleService(DataSourcePersistenceService dataSourceService,
                               DataSourceRegistry dataSourceRegistry,
                               MetadataService metadataService,
                               DimensionService dimensionService,
                               MetricDefinitionService metricDefinitionService,
                               WarningRuleService warningRuleService) {
        this.dataSourceService = dataSourceService;
        this.dataSourceRegistry = dataSourceRegistry;
        this.metadataService = metadataService;
        this.dimensionService = dimensionService;
        this.metricDefinitionService = metricDefinitionService;
        this.warningRuleService = warningRuleService;
    }

    public AtelierConfigBundle exportBundle(boolean includeSecrets) {
        AtelierConfigBundle bundle = AtelierConfigBundle.builder()
                .exportedAt(Instant.now())
                .build();

        for (DataSourceConfig config : dataSourceService.findAllConfigs()) {
            bundle.getDatasources().add(toExportDataSource(config, includeSecrets));
        }

        for (MetaTable table : metadataService.listTables()) {
            List<MetaTableField> fields = table.getId() != null
                    ? metadataService.listFields(table.getId())
                    : java.util.Collections.emptyList();
            bundle.getMetadataTables().add(AtelierConfigBundle.MetaTableExport.builder()
                    .table(table)
                    .fields(fields)
                    .build());
        }

        for (Dimension dimension : dimensionService.listDimensions()) {
            Dimension full = dimension.getId() != null
                    ? dimensionService.getDimension(dimension.getId()).orElse(dimension)
                    : dimension;
            List<DimensionValue> values = java.util.Collections.emptyList();
            if (full.getId() != null && full.getValueSource() != DimensionValueSource.TABLE) {
                values = dimensionService.listValues(full.getId());
            }
            bundle.getDimensions().add(AtelierConfigBundle.DimensionExport.builder()
                    .dimension(full)
                    .fields(full.getFields())
                    .values(values)
                    .build());
        }

        bundle.getMetrics().addAll(metricDefinitionService.listAll());
        bundle.getWarningRules().addAll(warningRuleService.listRules());
        return bundle;
    }

    @Transactional
    public ConfigImportResult importBundle(AtelierConfigBundle bundle, ConfigImportOptions options) {
        if (bundle == null) {
            throw new AtelierException("导入配置不能为空");
        }
        validateVersion(bundle.getVersion());

        ConfigImportResult result = ConfigImportResult.builder()
                .imported(new LinkedHashMap<>())
                .skipped(new LinkedHashMap<>())
                .build();

        if (options.isImportDatasources()) {
            importDatasources(bundle.getDatasources(), result);
        }
        if (options.isImportMetadata()) {
            importMetadata(bundle.getMetadataTables(), result);
        }
        if (options.isImportDimensions()) {
            importDimensions(bundle.getDimensions(), result);
        }
        if (options.isImportMetrics()) {
            importMetrics(bundle.getMetrics(), result);
        }
        if (options.isImportWarningRules()) {
            importWarningRules(bundle.getWarningRules(), result);
        }

        result.setMessage("配置导入完成");
        return result;
    }

    private void importDatasources(List<ConfigDataSource> datasources, ConfigImportResult result) {
        if (datasources == null) {
            return;
        }
        int count = 0;
        for (ConfigDataSource exported : datasources) {
            if (exported == null || isBlank(exported.getId())) {
                continue;
            }
            DataSourceConfig config = toImportDataSource(exported);
            Optional<DataSourceConfig> existing = dataSourceService.findConfigById(exported.getId());
            if (existing.isPresent() && isBlank(exported.getPassword())) {
                config.setPassword(existing.get().getPassword());
            }
            DataSourceConfig saved = dataSourceService.save(config);
            if (saved.isEnabled()) {
                dataSourceRegistry.refresh(saved);
            } else {
                dataSourceRegistry.unregister(saved.getId());
            }
            count++;
        }
        result.getImported().put("datasources", count);
    }

    private void importMetadata(List<AtelierConfigBundle.MetaTableExport> tables, ConfigImportResult result) {
        if (tables == null) {
            return;
        }
        int tableCount = 0;
        int fieldCount = 0;
        for (AtelierConfigBundle.MetaTableExport item : tables) {
            if (item == null || item.getTable() == null) {
                continue;
            }
            MetaTable saved = metadataService.saveTable(item.getTable());
            tableCount++;
            if (saved.getId() != null && item.getFields() != null) {
                for (MetaTableField field : item.getFields()) {
                    field.setTableId(saved.getId());
                    metadataService.saveField(field);
                    fieldCount++;
                }
            }
        }
        result.getImported().put("metadataTables", tableCount);
        result.getImported().put("metadataFields", fieldCount);
    }

    private void importDimensions(List<AtelierConfigBundle.DimensionExport> dimensions, ConfigImportResult result) {
        if (dimensions == null) {
            return;
        }
        int dimCount = 0;
        int valueCount = 0;
        for (AtelierConfigBundle.DimensionExport item : dimensions) {
            if (item == null || item.getDimension() == null) {
                continue;
            }
            Dimension toSave = item.getDimension();
            if (item.getFields() != null) {
                toSave.setFields(item.getFields());
            }
            Dimension saved = dimensionService.saveDimension(toSave);
            dimCount++;

            if (saved.getId() == null || item.getValues() == null || item.getValues().isEmpty()) {
                continue;
            }
            if (saved.getValueSource() == DimensionValueSource.TABLE) {
                result.getSkipped().merge("dimensionValues", item.getValues().size(), Integer::sum);
                continue;
            }
            for (DimensionValue existing : dimensionService.listValues(saved.getId())) {
                if (existing.getId() != null) {
                    dimensionService.deleteValue(existing.getId());
                }
            }
            for (DimensionValue value : item.getValues()) {
                value.setId(null);
                value.setDimensionId(saved.getId());
                dimensionService.saveValue(value);
                valueCount++;
            }
        }
        result.getImported().put("dimensions", dimCount);
        result.getImported().put("dimensionValues", valueCount);
    }

    private void importMetrics(List<MetricDefinition> metrics, ConfigImportResult result) {
        if (metrics == null) {
            return;
        }
        int count = 0;
        for (MetricDefinition metric : metrics) {
            if (metric == null || isBlank(metric.getCode())) {
                continue;
            }
            metricDefinitionService.save(metric);
            count++;
        }
        result.getImported().put("metrics", count);
    }

    private void importWarningRules(List<WarningRule> rules, ConfigImportResult result) {
        if (rules == null) {
            return;
        }
        int count = 0;
        for (WarningRule rule : rules) {
            if (rule == null || isBlank(rule.getCode())) {
                continue;
            }
            warningRuleService.saveRule(rule);
            count++;
        }
        result.getImported().put("warningRules", count);
    }

    private ConfigDataSource toExportDataSource(DataSourceConfig config, boolean includeSecrets) {
        return ConfigDataSource.builder()
                .id(config.getId())
                .name(config.getName())
                .jdbcUrl(config.getJdbcUrl())
                .username(config.getUsername())
                .password(includeSecrets ? config.getPassword() : null)
                .dbType(config.getDbType() != null ? config.getDbType().name() : DbType.UNKNOWN.name())
                .enabled(config.isEnabled())
                .build();
    }

    private DataSourceConfig toImportDataSource(ConfigDataSource exported) {
        DbType dbType = DbType.fromString(exported.getDbType());
        return DataSourceConfig.builder()
                .id(exported.getId())
                .name(exported.getName())
                .jdbcUrl(exported.getJdbcUrl())
                .username(exported.getUsername())
                .password(exported.getPassword())
                .dbType(dbType)
                .enabled(exported.isEnabled())
                .build();
    }

    private void validateVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            return;
        }
        if (!AtelierConfigBundle.FORMAT_VERSION.equals(version)) {
            throw new AtelierException("不支持的配置版本: " + version
                    + "，当前仅支持 " + AtelierConfigBundle.FORMAT_VERSION);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
