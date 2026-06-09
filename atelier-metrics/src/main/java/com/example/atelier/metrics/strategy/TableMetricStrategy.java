package com.example.atelier.metrics.strategy;

import com.example.atelier.domain.metric.AggregationType;
import com.example.atelier.domain.metric.DimensionBinding;
import com.example.atelier.domain.metric.MetricDefinition;
import com.example.atelier.domain.metric.MetricType;
import com.example.atelier.domain.model.MetricModel;
import com.example.atelier.domain.model.TableJoin;
import com.example.atelier.metrics.compiler.CompileContext;
import com.example.atelier.metrics.compiler.SqlFragments;
import com.example.atelier.metrics.compiler.WhereClauseBuilder;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于表的指标编译 — 对应旧版 IndexType.tableBased。
 */
public class TableMetricStrategy implements MetricCompileStrategy {

    @Override
    public MetricType supportedType() {
        return MetricType.TABLE;
    }

    @Override
    public SqlFragments compile(CompileContext context) {
        MetricDefinition metric = context.getMetric();
        MetricModel model = context.getModel();

        String measureExpr = buildMeasureExpression(metric);
        List<String> selectParts = new ArrayList<>();
        selectParts.add(measureExpr + " AS " + resolveAlias(metric));

        if (metric.getDimensions() != null) {
            for (DimensionBinding dim : metric.getDimensions()) {
                String qualified = qualifyField(metric.getTableCode(), dim.getFieldCode());
                selectParts.add(qualified);
            }
        }

        String fromClause = buildFromClause(model);
        String whereClause = WhereClauseBuilder.resolve(context.getFilters(), context.getFilterGroups());
        String groupBy = buildGroupBy(metric);

        return SqlFragments.builder()
                .selectClause(String.join(", ", selectParts))
                .fromClause(fromClause)
                .whereClause(whereClause)
                .groupByClause(groupBy)
                .build();
    }

    private String buildMeasureExpression(MetricDefinition metric) {
        String base;
        if (StringUtils.isNotBlank(metric.getExpression())) {
            base = metric.getExpression();
        } else {
            base = qualifyField(metric.getTableCode(), metric.getFieldCode());
        }
        AggregationType agg = metric.getAggregation();
        if (agg != null && agg != AggregationType.NONE) {
            return agg.name() + "(" + base + ")";
        }
        return base;
    }

    private String buildFromClause(MetricModel model) {
        StringBuilder from = new StringBuilder(model.getMainTableCode());
        if (model.getJoins() != null) {
            for (TableJoin join : model.getJoins()) {
                from.append(" ").append(join.getJoinType()).append(" ")
                        .append(join.getTableCode()).append(" ON ");
                List<String> onParts = join.getJoinFields().stream()
                        .map(f -> join.getLeftTableCode() + "." + f.getLeftField()
                                + " = " + join.getTableCode() + "." + f.getRightField())
                        .collect(Collectors.toList());
                from.append(String.join(" AND ", onParts));
            }
        }
        return from.toString();
    }

    private String buildGroupBy(MetricDefinition metric) {
        if (metric.getAggregation() == null || metric.getAggregation() == AggregationType.NONE) {
            return "";
        }
        if (metric.getDimensions() == null || metric.getDimensions().isEmpty()) {
            return "";
        }
        return metric.getDimensions().stream()
                .map(DimensionBinding::getFieldCode)
                .collect(Collectors.joining(", "));
    }

    private String qualifyField(String tableCode, String fieldCode) {
        if (StringUtils.isNotBlank(tableCode)) {
            return tableCode + "." + fieldCode;
        }
        return fieldCode;
    }

    private String resolveAlias(MetricDefinition metric) {
        if (StringUtils.isNotBlank(metric.getAlias())) {
            return metric.getAlias();
        }
        return metric.getFieldCode();
    }
}
