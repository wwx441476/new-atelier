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
        selectParts.add(qualifyFormula(formula, depCodes, deps) + " AS "
                + StringUtils.defaultIfBlank(metric.getAlias(), metric.getCode()));

        if (metric.getDimensions() != null) {
            for (DimensionBinding dim : metric.getDimensions()) {
                selectParts.add("T0." + dim.getFieldCode() + " AS " + quoteIdentifier(dim.getDimensionCode()));
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

        String whereClause = WhereClauseBuilder.resolve(context.getFilters(), context.getFilterGroups());

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

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String qualifyFormula(String formula, List<String> depCodes, Map<String, MetricDefinition> deps) {
        String qualified = formula;
        for (int i = 0; i < depCodes.size(); i++) {
            String code = depCodes.get(i);
            MetricDefinition dep = deps.get(code);
            if (dep == null) {
                continue;
            }
            String colAlias = StringUtils.defaultIfBlank(dep.getAlias(), dep.getCode());
            qualified = qualified.replaceAll("\\b" + Pattern.quote(code) + "\\b", "T" + i + "." + colAlias);
        }
        return qualified;
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
