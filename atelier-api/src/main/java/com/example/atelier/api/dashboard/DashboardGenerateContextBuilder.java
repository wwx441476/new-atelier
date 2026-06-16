package com.example.atelier.api.dashboard;

import com.example.atelier.dimension.spi.DimensionService;
import com.example.atelier.domain.dimension.Dimension;
import com.example.atelier.domain.dimension.DimensionValue;
import com.example.atelier.domain.metadata.MetaTable;
import com.example.atelier.domain.metric.MetricDefinition;
import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.infra.datasource.DataSourceConfig;
import com.example.atelier.infra.persistence.service.DashboardScreenService;
import com.example.atelier.infra.persistence.service.DataSourcePersistenceService;
import com.example.atelier.infra.persistence.service.MetricDefinitionService;
import com.example.atelier.metadata.spi.MetadataService;
import com.example.atelier.warning.spi.WarningRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class DashboardGenerateContextBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSourcePersistenceService dataSourceService;
    private final MetadataService metadataService;
    private final DimensionService dimensionService;
    private final MetricDefinitionService metricDefinitionService;
    private final WarningRuleService warningRuleService;
    private final DashboardScreenService dashboardScreenService;

    public DashboardGenerateContextBuilder(DataSourcePersistenceService dataSourceService,
                                           MetadataService metadataService,
                                           DimensionService dimensionService,
                                           MetricDefinitionService metricDefinitionService,
                                           WarningRuleService warningRuleService,
                                           DashboardScreenService dashboardScreenService) {
        this.dataSourceService = dataSourceService;
        this.metadataService = metadataService;
        this.dimensionService = dimensionService;
        this.metricDefinitionService = metricDefinitionService;
        this.warningRuleService = warningRuleService;
        this.dashboardScreenService = dashboardScreenService;
    }

    public String buildSummary() {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            ArrayNode datasources = root.putArray("datasources");
            for (DataSourceConfig ds : dataSourceService.findAllConfigs()) {
                ObjectNode node = datasources.addObject();
                node.put("id", ds.getId());
                node.put("name", ds.getName());
            }
            ArrayNode metaTables = root.putArray("metaTables");
            for (MetaTable table : metadataService.listTables()) {
                ObjectNode node = metaTables.addObject();
                node.put("tableCode", table.getTableCode());
                node.put("tableName", table.getTableName());
                node.put("datasourceId", table.getDatasourceId());
                node.put("schemaCode", table.getSchemaCode());
            }
            ArrayNode dimensions = root.putArray("dimensions");
            for (Dimension dim : dimensionService.listDimensions()) {
                ObjectNode node = dimensions.addObject();
                node.put("code", dim.getCode());
                node.put("name", dim.getName());
                if (dim.getId() != null) {
                    ArrayNode values = node.putArray("values");
                    for (DimensionValue value : dimensionService.listValues(dim.getId())) {
                        ObjectNode valueNode = values.addObject();
                        valueNode.put("code", value.getCode());
                        valueNode.put("name", value.getName());
                    }
                }
            }
            ArrayNode metrics = root.putArray("metrics");
            for (MetricDefinition metric : metricDefinitionService.listAll()) {
                ObjectNode node = metrics.addObject();
                node.put("code", metric.getCode());
                node.put("name", metric.getName());
                node.put("type", metric.getType() != null ? metric.getType().name() : null);
            }
            ArrayNode rules = root.putArray("warningRules");
            for (WarningRule rule : warningRuleService.listRules()) {
                ObjectNode node = rules.addObject();
                node.put("id", rule.getId());
                node.put("code", rule.getCode());
                node.put("name", rule.getName());
            }
            ArrayNode dashboards = root.putArray("existingDashboardCodes");
            dashboardScreenService.listAll().forEach(screen -> dashboards.add(screen.getCode()));
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }
}
