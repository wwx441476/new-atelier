package com.example.atelier.metrics.strategy;

import com.example.atelier.domain.metric.DimensionBinding;
import com.example.atelier.domain.metric.MetricDefinition;
import com.example.atelier.domain.metric.MetricType;
import com.example.atelier.metrics.compiler.CompileContext;
import com.example.atelier.metrics.compiler.SqlFragments;
import com.example.atelier.metrics.compiler.WhereClauseBuilder;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 复合指标编译 — 对应旧版 IndexType.indexBased。
 * 用指标 code 引用，不用 UUID 拼表达式。
 */
public class CompositeMetricStrategy implements MetricCompileStrategy {

    private static final Pattern METRIC_CODE_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    @Override
    public MetricType supportedType() {
        return MetricType.COMPOSITE;
    }

    @Override
    public SqlFragments compile(CompileContext context) {
        MetricDefinition metric = context.getMetric();
        Map<String, MetricDefinition> deps = context.getDependencyMetrics();
        Map<String, String> fromClauses = context.getDependencyFromClauses();

        String formula = metric.getFormula();
        List<String> depCodes = extractMetricCodes(formula, deps.keySet());

        List<String> selectParts = new ArrayList<>();
        selectParts.add(formula + " AS " + StringUtils.defaultIfBlank(metric.getAlias(), metric.getCode()));

        if (metric.getDimensions() != null) {
            for (DimensionBinding dim : metric.getDimensions()) {
                selectParts.add("T0." + dim.getFieldCode() + " AS " + dim.getDimensionCode());
            }
        }

        StringBuilder from = new StringBuilder();
        for (int i = 0; i < depCodes.size(); i++) {
            String code = depCodes.get(i);
            String subFrom = fromClauses.get(code);
            if (i == 0) {
                from.append("(").append(subFrom).append(") T0");
            } else {
                from.append(" INNER JOIN (").append(subFrom).append(") T").append(i);
                from.append(" ON ").append(buildJoinOn(metric, i));
            }
        }

        String whereClause = WhereClauseBuilder.build(context.getFilters());

        return SqlFragments.builder()
                .selectClause(String.join(", ", selectParts))
                .fromClause(from.toString())
                .whereClause(whereClause)
                .groupByClause("")
                .build();
    }

    private String buildJoinOn(MetricDefinition metric, int index) {
        if (metric.getDimensions() == null) {
            return "1=1";
        }
        return metric.getDimensions().stream()
                .map(dim -> "T0." + dim.getFieldCode() + " = T" + index + "." + dim.getFieldCode())
                .collect(Collectors.joining(" AND "));
    }

    private List<String> extractMetricCodes(String formula, java.util.Set<String> knownCodes) {
        List<String> result = new ArrayList<>();
        Matcher matcher = METRIC_CODE_PATTERN.matcher(formula);
        while (matcher.find()) {
            String token = matcher.group();
            if (knownCodes.contains(token) && !result.contains(token)) {
                result.add(token);
            }
        }
        return result;
    }
}
