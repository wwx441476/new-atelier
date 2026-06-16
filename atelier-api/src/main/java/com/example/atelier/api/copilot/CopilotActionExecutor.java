package com.example.atelier.api.copilot;

import com.example.atelier.dimension.spi.DimensionService;
import com.example.atelier.api.dashboard.DashboardGenerateService;
import com.example.atelier.api.dashboard.DashboardScreenNormalizer;
import com.example.atelier.domain.dashboard.DashboardScreen;
import com.example.atelier.domain.copilot.CopilotActionResult;
import com.example.atelier.domain.copilot.CopilotSqlQueryResult;
import com.example.atelier.domain.datasource.DbCreateTableColumn;
import com.example.atelier.domain.datasource.DbCreateTableRequest;
import com.example.atelier.domain.query.QueryResult;
import com.example.atelier.domain.query.SqlExecuteResult;
import com.example.atelier.infra.jdbc.DatabaseBrowserService;
import com.example.atelier.domain.dimension.Dimension;
import com.example.atelier.domain.dimension.DimensionField;
import com.example.atelier.domain.dimension.DimensionType;
import com.example.atelier.domain.dimension.DimensionValue;
import com.example.atelier.domain.dimension.DimensionValueSource;
import com.example.atelier.domain.metadata.MetaTable;
import com.example.atelier.domain.metadata.MetaTableField;
import com.example.atelier.domain.metadata.MetaTableImportRequest;
import com.example.atelier.domain.metric.AggregationType;
import com.example.atelier.domain.metric.DimensionBinding;
import com.example.atelier.domain.metric.MetricDefinition;
import com.example.atelier.domain.metric.MetricType;
import com.example.atelier.domain.copilot.CopilotWarningHitResult;
import com.example.atelier.domain.copilot.CopilotWarningJobResult;
import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.domain.warning.WarningRuleJob;
import com.example.atelier.domain.warning.WarningRuleJobSource;
import com.example.atelier.domain.warning.WarningRuleType;
import com.example.atelier.warning.spi.WarningRuleJobService;
import com.example.atelier.infra.datasource.DataSourceConfig;
import com.example.atelier.infra.datasource.DataSourceRegistry;
import com.example.atelier.infra.datasource.DbType;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.persistence.service.DataSourcePersistenceService;
import com.example.atelier.infra.persistence.service.MetricDefinitionService;
import com.example.atelier.metadata.spi.MetadataService;
import com.example.atelier.warning.spi.WarningRuleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Component
public class CopilotActionExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSourcePersistenceService dataSourceService;
    private final DataSourceRegistry dataSourceRegistry;
    private final MetadataService metadataService;
    private final DimensionService dimensionService;
    private final MetricDefinitionService metricDefinitionService;
    private final WarningRuleService warningRuleService;
    private final WarningRuleJobService warningRuleJobService;
    private final DatabaseBrowserService databaseBrowserService;
    private final CopilotWarningRuleResolver warningRuleResolver;
    private final DashboardGenerateService dashboardGenerateService;
    private final DashboardScreenNormalizer dashboardScreenNormalizer;

    public CopilotActionExecutor(DataSourcePersistenceService dataSourceService,
                                 DataSourceRegistry dataSourceRegistry,
                                 MetadataService metadataService,
                                 DimensionService dimensionService,
                                 MetricDefinitionService metricDefinitionService,
                                 WarningRuleService warningRuleService,
                                 WarningRuleJobService warningRuleJobService,
                                 DatabaseBrowserService databaseBrowserService,
                                 CopilotWarningRuleResolver warningRuleResolver,
                                 DashboardGenerateService dashboardGenerateService,
                                 DashboardScreenNormalizer dashboardScreenNormalizer) {
        this.dataSourceService = dataSourceService;
        this.dataSourceRegistry = dataSourceRegistry;
        this.metadataService = metadataService;
        this.dimensionService = dimensionService;
        this.metricDefinitionService = metricDefinitionService;
        this.warningRuleService = warningRuleService;
        this.warningRuleJobService = warningRuleJobService;
        this.databaseBrowserService = databaseBrowserService;
        this.warningRuleResolver = warningRuleResolver;
        this.dashboardGenerateService = dashboardGenerateService;
        this.dashboardScreenNormalizer = dashboardScreenNormalizer;
    }

    public CopilotActionResult execute(String tool, JsonNode params) {
        if (tool == null || tool.trim().isEmpty()) {
            return fail(tool, "工具名为空");
        }
        String normalized = tool.trim().toLowerCase();
        try {
            switch (normalized) {
                case "create_datasource":
                    return createDatasource(params);
                case "create_meta_table":
                    return createMetaTable(params);
                case "import_meta_tables":
                    return importMetaTables(params);
                case "create_meta_field":
                    return createMetaField(params);
                case "create_dimension":
                    return createDimension(params);
                case "create_dimension_value":
                    return createDimensionValue(params);
                case "create_metric":
                    return createMetric(params);
                case "create_warning_rule":
                    return createWarningRule(params);
                case "run_warning_rule":
                    return runWarningRule(params);
                case "get_warning_job_result":
                    return getWarningJobResult(params);
                case "execute_sql":
                    return executeSql(params);
                case "execute_write_sql":
                    return executeWriteSql(params);
                case "create_physical_table":
                    return createPhysicalTable(params);
                case "create_dashboard":
                    return createDashboard(params);
                default:
                    return fail(tool, "未知工具: " + tool);
            }
        } catch (AtelierException e) {
            return fail(tool, e.getMessage());
        } catch (Exception e) {
            return fail(tool, "执行失败: " + e.getMessage());
        }
    }

    private CopilotActionResult createDashboard(JsonNode params) {
        DashboardScreen screen = dashboardGenerateService.parseAndNormalize(params);
        DashboardScreen saved = dashboardScreenNormalizer.save(screen);
        return success("create_dashboard", "已创建大屏「" + saved.getName() + "」", saved);
    }

    private CopilotActionResult createDatasource(JsonNode params) {
        DataSourceConfig config = DataSourceConfig.builder()
                .id(text(params, "id"))
                .name(requiredText(params, "name"))
                .jdbcUrl(requiredText(params, "jdbcUrl"))
                .username(text(params, "username"))
                .password(text(params, "password"))
                .dbType(parseDbType(text(params, "dbType")))
                .enabled(params.path("enabled").asBoolean(true))
                .build();
        DataSourceConfig saved = dataSourceService.save(config);
        if (saved.isEnabled()) {
            dataSourceRegistry.refresh(saved);
        }
        return success("create_datasource", "已创建数据源 " + saved.getId(), saved);
    }

    private CopilotActionResult createMetaTable(JsonNode params) {
        MetaTable table = MetaTable.builder()
                .tableCode(requiredText(params, "tableCode"))
                .tableName(requiredText(params, "tableName"))
                .catalogCode(text(params, "catalogCode"))
                .datasourceId(requiredText(params, "datasourceId"))
                .schemaCode(text(params, "schemaCode"))
                .comments(text(params, "comments"))
                .build();
        MetaTable saved = metadataService.saveTable(table);
        List<MetaTableField> explicitFields = readMetaFields(params.get("fields"), saved.getId());
        for (MetaTableField field : explicitFields) {
            metadataService.saveField(field);
        }
        int synced = metadataService.syncFieldsFromPhysicalTable(saved.getId());
        MetaTable result = metadataService.getTable(saved.getId()).orElse(saved);
        String message = "已创建元数据表 " + saved.getTableCode();
        int fieldCount = result.getFields() != null ? result.getFields().size() : 0;
        if (fieldCount > 0) {
            message += "，含 " + fieldCount + " 个字段";
        } else if (synced == 0 && explicitFields.isEmpty()) {
            message += "（物理表未找到或未同步到字段，请检查 tableCode/schema 或使用 import_meta_tables）";
        }
        return success("create_meta_table", message, result);
    }

    private List<MetaTableField> readMetaFields(JsonNode node, String tableId) {
        List<MetaTableField> fields = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return fields;
        }
        int sort = 1;
        for (JsonNode item : node) {
            fields.add(MetaTableField.builder()
                    .tableId(tableId)
                    .fieldCode(requiredText(item, "fieldCode"))
                    .fieldName(text(item, "fieldName", requiredText(item, "fieldCode")))
                    .fieldType(text(item, "fieldType", "VARCHAR"))
                    .fieldLength(intOrNull(item, "fieldLength"))
                    .fieldPrecision(intOrNull(item, "fieldPrecision"))
                    .nullable(!item.has("nullable") || item.get("nullable").asBoolean(true))
                    .sort(intOrNull(item, "sort") != null ? intOrNull(item, "sort") : sort++)
                    .build());
        }
        return fields;
    }

    private CopilotActionResult importMetaTables(JsonNode params) {
        List<String> tableNames = readStringList(params.get("tableNames"));
        MetaTableImportRequest request = MetaTableImportRequest.builder()
                .datasourceId(requiredText(params, "datasourceId"))
                .schemaCode(text(params, "schemaCode"))
                .catalogCode(text(params, "catalogCode"))
                .tableNames(tableNames)
                .build();
        return success("import_meta_tables", "已同步 " + tableNames.size() + " 张表",
                metadataService.importTablesFromDatabase(request));
    }

    private CopilotActionResult createMetaField(JsonNode params) {
        String tableId = requiredText(params, "tableId");
        MetaTableField field = MetaTableField.builder()
                .tableId(tableId)
                .fieldCode(requiredText(params, "fieldCode"))
                .fieldName(requiredText(params, "fieldName"))
                .fieldType(text(params, "fieldType", "VARCHAR"))
                .fieldLength(intOrNull(params, "fieldLength"))
                .fieldPrecision(intOrNull(params, "fieldPrecision"))
                .nullable(params.path("nullable").asBoolean(true))
                .sort(intOrNull(params, "sort"))
                .build();
        MetaTableField saved = metadataService.saveField(field);
        return success("create_meta_field", "已添加字段 " + saved.getFieldCode(), saved);
    }

    private CopilotActionResult createDimension(JsonNode params) {
        Dimension dimension = Dimension.builder()
                .code(requiredText(params, "code"))
                .name(requiredText(params, "name"))
                .catalogCode(text(params, "catalogCode"))
                .type(parseDimensionType(text(params, "type", "LIST")))
                .datasourceId(requiredText(params, "datasourceId"))
                .metaTableId(text(params, "metaTableId"))
                .valueSource(parseValueSource(text(params, "valueSource", "MANUAL")))
                .comments(text(params, "comments"))
                .fields(readDimensionFields(params.get("fields")))
                .build();
        Dimension saved = dimensionService.saveDimension(dimension);
        return success("create_dimension", "已创建维度 " + saved.getCode(), saved);
    }

    private CopilotActionResult createDimensionValue(JsonNode params) {
        String dimensionId = requiredText(params, "dimensionId");
        DimensionValue value = DimensionValue.builder()
                .dimensionId(dimensionId)
                .code(requiredText(params, "code"))
                .name(requiredText(params, "name"))
                .parentCode(text(params, "parentCode"))
                .sort(intOrNull(params, "sort"))
                .build();
        DimensionValue saved = dimensionService.saveValue(value);
        return success("create_dimension_value", "已添加维度值 " + saved.getCode(), saved);
    }

    private CopilotActionResult createMetric(JsonNode params) {
        MetricType type = parseMetricType(text(params, "type", "TABLE"));
        String tableCode = text(params, "tableCode");
        String fieldCode = text(params, "fieldCode");
        String formula = text(params, "formula");

        if (type == MetricType.COMPOSITE && (formula == null || formula.isEmpty())) {
            if (tableCode != null && fieldCode != null) {
                type = MetricType.TABLE;
            } else {
                throw new AtelierException("复合指标必须填写 formula（如 revenue - cost），"
                        + "单表聚合请使用 type=TABLE 并指定 tableCode、fieldCode、aggregation");
            }
        }
        if (type == MetricType.TABLE) {
            if (tableCode == null || tableCode.isEmpty()) {
                throw new AtelierException("TABLE 类型指标缺少 tableCode");
            }
            if (fieldCode == null || fieldCode.isEmpty()) {
                throw new AtelierException("TABLE 类型指标缺少 fieldCode");
            }
        }

        List<DimensionBinding> dimensions = normalizeMetricDimensions(
                readDimensionBindings(params.get("dimensions")), tableCode);

        MetricDefinition metric = MetricDefinition.builder()
                .code(requiredText(params, "code"))
                .name(requiredText(params, "name"))
                .catalogCode(text(params, "catalogCode"))
                .type(type)
                .datasourceId(requiredText(params, "datasourceId"))
                .modelCode(text(params, "modelCode"))
                .tableCode(tableCode)
                .fieldCode(fieldCode)
                .fieldName(text(params, "fieldName"))
                .expression(text(params, "expression"))
                .datasetSql(text(params, "datasetSql"))
                .formula(type == MetricType.COMPOSITE ? formula : null)
                .aggregation(type == MetricType.TABLE
                        ? parseAggregation(text(params, "aggregation", "SUM"))
                        : null)
                .alias(text(params, "alias", text(params, "code")))
                .description(text(params, "description"))
                .dimensions(dimensions)
                .build();
        MetricDefinition saved = metricDefinitionService.save(metric);
        return success("create_metric", "已创建指标 " + saved.getCode(), saved);
    }

    private List<DimensionBinding> normalizeMetricDimensions(List<DimensionBinding> bindings, String tableCode) {
        if (bindings == null || bindings.isEmpty() || tableCode == null || tableCode.isEmpty()) {
            return bindings != null ? bindings : new ArrayList<>();
        }
        Set<String> tableFieldCodes = resolveTableFieldCodes(tableCode);
        if (tableFieldCodes.isEmpty()) {
            return bindings;
        }
        List<DimensionBinding> valid = new ArrayList<>();
        for (DimensionBinding binding : bindings) {
            if (binding.getFieldCode() != null
                    && tableFieldCodes.contains(binding.getFieldCode().toLowerCase())) {
                valid.add(binding);
            }
        }
        return valid;
    }

    private Set<String> resolveTableFieldCodes(String tableCode) {
        Set<String> codes = new java.util.HashSet<>();
        for (MetaTable table : metadataService.listTables()) {
            if (table.getTableCode() != null
                    && table.getTableCode().equalsIgnoreCase(tableCode)
                    && table.getId() != null) {
                for (MetaTableField field : metadataService.listFields(table.getId())) {
                    if (field.getFieldCode() != null) {
                        codes.add(field.getFieldCode().toLowerCase());
                    }
                }
                break;
            }
        }
        return codes;
    }

    private CopilotActionResult executeWriteSql(JsonNode params) {
        String datasourceId = requiredText(params, "datasourceId");
        String sql = requiredText(params, "sql");
        SqlExecuteResult result = databaseBrowserService.executeWriteSql(datasourceId, sql);
        return success("execute_write_sql", result.getMessage(), result);
    }

    private CopilotActionResult createPhysicalTable(JsonNode params) {
        String datasourceId = requiredText(params, "datasourceId");
        DbCreateTableRequest request = parseCreateTableRequest(params);
        SqlExecuteResult result = databaseBrowserService.createTable(datasourceId, request);
        return success("create_physical_table",
                "已创建物理表 " + request.getTableName(),
                result);
    }

    DbCreateTableRequest parseCreateTableRequest(JsonNode params) {
        List<DbCreateTableColumn> columns = new ArrayList<>();
        JsonNode columnsNode = resolveColumnsNode(params);
        if (columnsNode != null && columnsNode.isArray()) {
            for (JsonNode item : columnsNode) {
                columns.add(DbCreateTableColumn.builder()
                        .name(requiredText(item, "name"))
                        .type(requiredText(item, "type"))
                        .nullable(item.has("nullable") ? item.get("nullable").asBoolean() : null)
                        .primaryKey(item.path("primaryKey").asBoolean(false))
                        .build());
            }
        }
        if (columns.isEmpty()) {
            throw new AtelierException("建表至少需要一个字段，params 须包含 columns 数组");
        }
        return DbCreateTableRequest.builder()
                .schema(text(params, "schema"))
                .tableName(requiredText(params, "tableName"))
                .ifNotExists(!params.has("ifNotExists") || params.get("ifNotExists").asBoolean(true))
                .columns(columns)
                .build();
    }

    String previewCreateTableDdl(String datasourceId, JsonNode params) {
        return databaseBrowserService.previewCreateTableDdl(datasourceId, parseCreateTableRequest(params));
    }

    private JsonNode resolveColumnsNode(JsonNode params) {
        JsonNode columnsNode = params.get("columns");
        if (columnsNode != null && columnsNode.isArray()) {
            return columnsNode;
        }
        Iterator<String> fieldNames = params.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            JsonNode value = params.get(field);
            if (value != null && value.isArray() && looksLikeColumnsArray(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean looksLikeColumnsArray(JsonNode array) {
        if (array == null || !array.isArray() || array.size() == 0) {
            return false;
        }
        JsonNode first = array.get(0);
        return first != null && first.isObject() && first.has("name") && first.has("type");
    }

    private CopilotActionResult executeSql(JsonNode params) {
        String datasourceId = requiredText(params, "datasourceId");
        String sql = requiredText(params, "sql");
        int pageIndex = params.path("pageIndex").asInt(1);
        int pageSize = params.path("pageSize").asInt(20);
        QueryResult queryResult = databaseBrowserService.executeSelectQuery(datasourceId, sql, pageIndex, pageSize);
        CopilotSqlQueryResult payload = CopilotSqlQueryResult.builder()
                .datasourceId(datasourceId)
                .pageIndex(pageIndex <= 0 ? 1 : pageIndex)
                .pageSize(pageSize <= 0 ? 20 : pageSize)
                .total(queryResult.getTotal())
                .rows(queryResult.getRows())
                .headers(queryResult.getHeaders())
                .sql(queryResult.getSql())
                .build();
        return success("execute_sql",
                "查询完成，共 " + queryResult.getTotal() + " 条，当前第 " + payload.getPageIndex() + " 页",
                payload);
    }

    private CopilotActionResult getWarningJobResult(JsonNode params) {
        String jobId = requiredText(params, "jobId");
        CopilotWarningHitResult hits = warningRuleJobService.getJobHits(jobId)
                .orElseThrow(() -> new AtelierException("任务不存在或暂无结果: " + jobId));
        String message;
        if (hits.getMatchedRows() == null || hits.getMatchedRows().isEmpty()) {
            message = "任务「" + hits.getRuleName() + "」当前页无命中行";
        } else {
            message = "任务「" + hits.getRuleName() + "」命中 "
                    + hits.getMatchedRows().size() + " 条，见下方数据";
        }
        return success("get_warning_job_result", message, hits);
    }

    private CopilotActionResult runWarningRule(JsonNode params) {
        WarningRule rule = warningRuleResolver.resolve(params);
        int pageIndex = params.path("pageIndex").asInt(1);
        int pageSize = params.path("pageSize").asInt(20);
        boolean keywordOnly = !params.has("keywordOnly") || params.path("keywordOnly").asBoolean(true);
        WarningRuleJob job = warningRuleJobService.submitPreview(
                rule.getId(), pageIndex, pageSize, null, null, keywordOnly, WarningRuleJobSource.COPILOT);
        CopilotWarningJobResult payload = CopilotWarningJobResult.builder()
                .jobId(job.getId())
                .status(job.getStatus() != null ? job.getStatus().name() : "PENDING")
                .ruleId(job.getRuleId())
                .ruleCode(job.getRuleCode())
                .ruleName(job.getRuleName())
                .pageIndex(pageIndex <= 0 ? 1 : pageIndex)
                .pageSize(pageSize <= 0 ? 20 : pageSize)
                .keywordOnly(keywordOnly)
                .build();
        return success("run_warning_rule",
                "已提交预警任务「" + job.getRuleName() + "」，完成后将通知您",
                payload);
    }

    private CopilotActionResult createWarningRule(JsonNode params) {
        WarningRule rule = WarningRule.builder()
                .code(requiredText(params, "code"))
                .name(requiredText(params, "name"))
                .catalogCode(text(params, "catalogCode"))
                .metricCodes(readStringList(params.get("metricCodes")))
                .expression(text(params, "expression"))
                .enabled(params.path("enabled").asBoolean(true))
                .warningLevel(params.has("warningLevel") ? params.get("warningLevel").asInt() : 2)
                .ruleType(parseRuleType(text(params, "ruleType", "METRIC")))
                .comments(text(params, "comments"))
                .build();
        WarningRule saved = warningRuleService.saveRule(rule);
        return success("create_warning_rule", "已创建预警规则 " + saved.getCode(), saved);
    }

    private List<DimensionField> readDimensionFields(JsonNode node) {
        List<DimensionField> fields = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return fields;
        }
        for (JsonNode item : node) {
            fields.add(DimensionField.builder()
                    .fieldCode(requiredText(item, "fieldCode"))
                    .fieldName(text(item, "fieldName"))
                    .codeField(item.path("codeField").asBoolean(false))
                    .nameField(item.path("nameField").asBoolean(false))
                    .parentField(item.path("parentField").asBoolean(false))
                    .sort(intOrNull(item, "sort"))
                    .build());
        }
        return fields;
    }

    private List<DimensionBinding> readDimensionBindings(JsonNode node) {
        List<DimensionBinding> bindings = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return bindings;
        }
        for (JsonNode item : node) {
            bindings.add(DimensionBinding.builder()
                    .dimensionCode(requiredText(item, "dimensionCode"))
                    .fieldCode(requiredText(item, "fieldCode"))
                    .fieldName(text(item, "fieldName"))
                    .sort(intOrNull(item, "sort"))
                    .build());
        }
        return bindings;
    }

    private List<String> readStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private DbType parseDbType(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return DbType.UNKNOWN;
        }
        try {
            return DbType.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return DbType.UNKNOWN;
        }
    }

    private DimensionType parseDimensionType(String raw) {
        return DimensionType.valueOf(raw.trim().toUpperCase());
    }

    private DimensionValueSource parseValueSource(String raw) {
        return DimensionValueSource.valueOf(raw.trim().toUpperCase());
    }

    private MetricType parseMetricType(String raw) {
        return MetricType.valueOf(raw.trim().toUpperCase());
    }

    private AggregationType parseAggregation(String raw) {
        return AggregationType.valueOf(raw.trim().toUpperCase());
    }

    private WarningRuleType parseRuleType(String raw) {
        return WarningRuleType.valueOf(raw.trim().toUpperCase());
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isEmpty()) {
            throw new AtelierException("缺少必填参数: " + field);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        return text(node, field, null);
    }

    private String text(JsonNode node, String field, String defaultValue) {
        if (node == null || node.isMissingNode()) {
            return defaultValue;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        String text = value.asText("");
        return text.trim().isEmpty() ? defaultValue : text.trim();
    }

    private Integer intOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asInt();
    }

    private CopilotActionResult success(String tool, String message, Object result) {
        return CopilotActionResult.builder()
                .tool(tool)
                .success(true)
                .message(message)
                .result(result)
                .build();
    }

    private CopilotActionResult fail(String tool, String message) {
        return CopilotActionResult.builder()
                .tool(tool != null ? tool : "unknown")
                .success(false)
                .message(message)
                .build();
    }
}
