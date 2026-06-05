package com.yonyougov.atelier.metrics.strategy;

import com.yonyougov.atelier.domain.metric.AggregationType;
import com.yonyougov.atelier.domain.metric.DimensionBinding;
import com.yonyougov.atelier.domain.metric.MetricDefinition;
import com.yonyougov.atelier.domain.metric.MetricType;
import com.yonyougov.atelier.metrics.compiler.CompileContext;
import com.yonyougov.atelier.metrics.compiler.SqlFragments;
import com.yonyougov.atelier.metrics.compiler.WhereClauseBuilder;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 SQL 数据集的指标编译 — 对应旧版 IndexType.sqlBased。
 */
public class SqlMetricStrategy implements MetricCompileStrategy {

    @Override
    public MetricType supportedType() {
        return MetricType.SQL;
    }

    @Override
    public SqlFragments compile(CompileContext context) {
        MetricDefinition metric = context.getMetric();

        String measure = metric.getFieldCode();
        if (metric.getAggregation() != null && metric.getAggregation() != AggregationType.NONE) {
            measure = metric.getAggregation().name() + "(" + measure + ")";
        }
        String alias = StringUtils.defaultIfBlank(metric.getAlias(), metric.getFieldCode());

        List<String> selectParts = new ArrayList<>();
        selectParts.add(measure + " AS " + alias);

        if (metric.getDimensions() != null) {
            for (DimensionBinding dim : metric.getDimensions()) {
                selectParts.add(dim.getFieldCode());
            }
        }

        String datasetSql = stripSemicolon(metric.getDatasetSql());
        String fromClause = "(" + datasetSql + ") DATASET_TMP";
        String whereClause = WhereClauseBuilder.build(context.getFilters());
        String groupBy = buildGroupBy(metric);

        return SqlFragments.builder()
                .selectClause(String.join(", ", selectParts))
                .fromClause(fromClause)
                .whereClause(whereClause)
                .groupByClause(groupBy)
                .build();
    }

    private String buildGroupBy(MetricDefinition metric) {
        if (metric.getAggregation() == null || metric.getAggregation() == AggregationType.NONE) {
            return "";
        }
        if (metric.getDimensions() == null) {
            return "";
        }
        return metric.getDimensions().stream()
                .map(DimensionBinding::getFieldCode)
                .collect(Collectors.joining(", "));
    }

    private String stripSemicolon(String sql) {
        if (sql == null) {
            return "";
        }
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
