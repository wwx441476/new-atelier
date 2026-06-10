package com.example.atelier.api.copilot;

import com.example.atelier.dimension.spi.DimensionService;
import com.example.atelier.domain.dimension.Dimension;
import com.example.atelier.domain.metadata.MetaTable;
import com.example.atelier.domain.metadata.MetaTableField;
import com.example.atelier.domain.metric.MetricDefinition;
import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.domain.warning.WarningRuleJob;
import com.example.atelier.domain.warning.WarningRuleJobStatus;
import com.example.atelier.warning.spi.WarningRuleJobService;
import com.example.atelier.infra.datasource.DataSourceConfig;
import com.example.atelier.infra.persistence.service.DataSourcePersistenceService;
import com.example.atelier.infra.persistence.service.MetricDefinitionService;
import com.example.atelier.metadata.spi.MetadataService;
import com.example.atelier.warning.spi.WarningRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class CopilotWorkspaceContextBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSourcePersistenceService dataSourceService;
    private final MetadataService metadataService;
    private final DimensionService dimensionService;
    private final MetricDefinitionService metricDefinitionService;
    private final WarningRuleService warningRuleService;
    private final WarningRuleJobService warningRuleJobService;

    public CopilotWorkspaceContextBuilder(DataSourcePersistenceService dataSourceService,
                                          MetadataService metadataService,
                                          DimensionService dimensionService,
                                          MetricDefinitionService metricDefinitionService,
                                          WarningRuleService warningRuleService,
                                          WarningRuleJobService warningRuleJobService) {
        this.dataSourceService = dataSourceService;
        this.metadataService = metadataService;
        this.dimensionService = dimensionService;
        this.metricDefinitionService = metricDefinitionService;
        this.warningRuleService = warningRuleService;
        this.warningRuleJobService = warningRuleJobService;
    }

    public String buildSummary() {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            ArrayNode datasources = root.putArray("datasources");
            for (DataSourceConfig ds : dataSourceService.findAllConfigs()) {
                ObjectNode node = datasources.addObject();
                node.put("id", ds.getId());
                node.put("name", ds.getName());
                node.put("dbType", ds.getDbType() != null ? ds.getDbType().name() : null);
                node.put("jdbcUrl", ds.getJdbcUrl());
            }
            ArrayNode metaTables = root.putArray("metaTables");
            for (MetaTable table : metadataService.listTables()) {
                ObjectNode node = metaTables.addObject();
                node.put("id", table.getId());
                node.put("tableCode", table.getTableCode());
                node.put("tableName", table.getTableName());
                node.put("datasourceId", table.getDatasourceId());
                node.put("schemaCode", table.getSchemaCode());
                ArrayNode fields = node.putArray("fields");
                if (table.getId() != null) {
                    for (MetaTableField field : metadataService.listFields(table.getId())) {
                        ObjectNode fieldNode = fields.addObject();
                        fieldNode.put("fieldCode", field.getFieldCode());
                        fieldNode.put("fieldName", field.getFieldName());
                        fieldNode.put("fieldType", field.getFieldType());
                        if (field.getSort() != null) {
                            fieldNode.put("sort", field.getSort());
                        }
                    }
                }
            }
            ArrayNode dimensions = root.putArray("dimensions");
            for (Dimension dim : dimensionService.listDimensions()) {
                ObjectNode node = dimensions.addObject();
                node.put("id", dim.getId());
                node.put("code", dim.getCode());
                node.put("name", dim.getName());
                node.put("type", dim.getType() != null ? dim.getType().name() : null);
                node.put("datasourceId", dim.getDatasourceId());
            }
            ArrayNode metrics = root.putArray("metrics");
            for (MetricDefinition metric : metricDefinitionService.listAll()) {
                ObjectNode node = metrics.addObject();
                node.put("code", metric.getCode());
                node.put("name", metric.getName());
                node.put("type", metric.getType() != null ? metric.getType().name() : null);
                node.put("datasourceId", metric.getDatasourceId());
                node.put("tableCode", metric.getTableCode());
                node.put("fieldCode", metric.getFieldCode());
                node.put("aggregation", metric.getAggregation() != null ? metric.getAggregation().name() : null);
            }
            ArrayNode rules = root.putArray("warningRules");
            for (WarningRule rule : warningRuleService.listRules()) {
                ObjectNode node = rules.addObject();
                node.put("id", rule.getId());
                node.put("code", rule.getCode());
                node.put("name", rule.getName());
                node.put("ruleType", rule.getRuleType() != null ? rule.getRuleType().name() : "METRIC");
                node.put("expression", rule.getExpression());
            }
            ArrayNode recentJobs = root.putArray("recentWarningJobs");
            for (WarningRuleJob job : warningRuleJobService.listRecent(
                    Arrays.asList(WarningRuleJobStatus.SUCCESS, WarningRuleJobStatus.FAILED), 10)) {
                ObjectNode node = recentJobs.addObject();
                node.put("jobId", job.getId());
                node.put("ruleId", job.getRuleId());
                node.put("ruleCode", job.getRuleCode());
                node.put("ruleName", job.getRuleName());
                node.put("status", job.getStatus() != null ? job.getStatus().name() : null);
                node.put("total", job.getTotal() != null ? job.getTotal() : 0);
                node.put("pageMatchedCount", job.getMatchedCount() != null ? job.getMatchedCount() : 0);
                if (job.getParams() != null) {
                    node.put("pageIndex", job.getParams().getPageIndex());
                    node.put("pageSize", job.getParams().getPageSize());
                }
            }
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }
}
