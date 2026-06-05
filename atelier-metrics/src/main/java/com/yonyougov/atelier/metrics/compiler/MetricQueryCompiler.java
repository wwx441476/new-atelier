package com.yonyougov.atelier.metrics.compiler;

import com.yonyougov.atelier.domain.metric.MetricDefinition;
import com.yonyougov.atelier.domain.metric.MetricType;
import com.yonyougov.atelier.domain.model.MetricModel;
import com.yonyougov.atelier.domain.query.CompiledQuery;
import com.yonyougov.atelier.domain.query.MetricQueryRequest;
import com.yonyougov.atelier.metrics.spi.MetricDefinitionRepository;
import com.yonyougov.atelier.metrics.spi.MetricModelRepository;
import com.yonyougov.atelier.metrics.strategy.CompositeMetricStrategy;
import com.yonyougov.atelier.metrics.strategy.MetricCompileStrategy;
import com.yonyougov.atelier.metrics.strategy.SqlMetricStrategy;
import com.yonyougov.atelier.metrics.strategy.TableMetricStrategy;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 指标查询编译器 — 唯一 SQL 生成入口。
 *
 * <p>替代旧版 addIndex / updateIndex / getAuthFilterSql / buildSqlMap / getIndexSqlForParam 五套重复逻辑。
 */
public class MetricQueryCompiler {

    private final Map<MetricType, MetricCompileStrategy> strategies = new EnumMap<>(MetricType.class);
    private final MetricDefinitionRepository definitionRepository;
    private final MetricModelRepository modelRepository;

    public MetricQueryCompiler(MetricDefinitionRepository definitionRepository,
                               MetricModelRepository modelRepository) {
        this.definitionRepository = definitionRepository;
        this.modelRepository = modelRepository;
        register(new TableMetricStrategy());
        register(new SqlMetricStrategy());
        register(new CompositeMetricStrategy());
    }

    private void register(MetricCompileStrategy strategy) {
        strategies.put(strategy.supportedType(), strategy);
    }

    public CompiledQuery compile(MetricQueryRequest request) {
        List<MetricDefinition> metrics = definitionRepository.findByCodes(request.getMetricCodes());
        if (metrics.isEmpty()) {
            throw new IllegalArgumentException("未找到指标: " + request.getMetricCodes());
        }

        if (metrics.size() == 1) {
            return compileSingle(metrics.get(0), request);
        }
        return compileMultiple(metrics, request);
    }

    private CompiledQuery compileSingle(MetricDefinition metric, MetricQueryRequest request) {
        SqlFragments fragments = compileFragments(metric, request, new HashMap<>(), new HashMap<>());
        Map<String, String> labels = buildColumnLabels(metric);

        return CompiledQuery.builder()
                .sql(fragments.toSql())
                .datasourceId(metric.getDatasourceId())
                .columnLabels(labels)
                .metricValueColumns(java.util.Collections.singletonList(
                        metric.getAlias() != null ? metric.getAlias() : metric.getCode()))
                .build();
    }

    private CompiledQuery compileMultiple(List<MetricDefinition> metrics, MetricQueryRequest request) {
        // 多指标：每个指标编译为子查询，再按公共维度 JOIN（骨架实现）
        StringBuilder outerSelect = new StringBuilder("SELECT ");
        StringBuilder outerFrom = new StringBuilder(" FROM ");
        Map<String, String> labels = new LinkedHashMap<>();

        for (int i = 0; i < metrics.size(); i++) {
            MetricDefinition metric = metrics.get(i);
            SqlFragments fragments = compileFragments(metric, request, new HashMap<>(), new HashMap<>());
            String tmpAlias = "M" + i;
            if (i > 0) {
                outerSelect.append(", ");
            }
            String valueCol = metric.getAlias() != null ? metric.getAlias() : metric.getCode();
            outerSelect.append(tmpAlias).append(".").append(valueCol);
            labels.put(valueCol, metric.getName());

            if (i == 0) {
                outerFrom.append("(").append(fragments.toSql()).append(") ").append(tmpAlias);
            } else {
                outerFrom.append(" INNER JOIN (").append(fragments.toSql()).append(") ").append(tmpAlias)
                        .append(" ON 1=1");
            }
        }

        return CompiledQuery.builder()
                .sql(outerSelect.append(outerFrom).toString())
                .datasourceId(metrics.get(0).getDatasourceId())
                .columnLabels(labels)
                .metricValueColumns(metrics.stream()
                        .map(m -> m.getAlias() != null ? m.getAlias() : m.getCode())
                        .collect(Collectors.toList()))
                .build();
    }

    private SqlFragments compileFragments(MetricDefinition metric,
                                          MetricQueryRequest request,
                                          Map<String, MetricDefinition> deps,
                                          Map<String, String> depFromClauses) {
        MetricModel model = null;
        if (metric.getType() == MetricType.TABLE && metric.getModelCode() != null) {
            model = modelRepository.findByCode(metric.getModelCode())
                    .orElseThrow(() -> new IllegalArgumentException("模型不存在: " + metric.getModelCode()));
        }

        if (metric.getType() == MetricType.COMPOSITE) {
            resolveDependencies(metric, request, deps, depFromClauses);
        }

        CompileContext context = CompileContext.builder()
                .metric(metric)
                .model(model)
                .filters(request.getFilters())
                .dependencyMetrics(deps)
                .dependencyFromClauses(depFromClauses)
                .build();

        MetricCompileStrategy strategy = strategies.get(metric.getType());
        if (strategy == null) {
            throw new IllegalStateException("不支持的指标类型: " + metric.getType());
        }
        return strategy.compile(context);
    }

    private void resolveDependencies(MetricDefinition composite,
                                     MetricQueryRequest request,
                                     Map<String, MetricDefinition> deps,
                                     Map<String, String> depFromClauses) {
        // 从 formula 解析依赖的指标 code 并递归编译 FROM 子句
        for (String token : composite.getFormula().split("[^a-zA-Z0-9_]+")) {
            if (token.isEmpty()) {
                continue;
            }
            definitionRepository.findByCode(token).ifPresent(dep -> {
                if (!deps.containsKey(token)) {
                    deps.put(token, dep);
                    SqlFragments sub = compileFragments(dep, request, deps, depFromClauses);
                    depFromClauses.put(token, sub.toSql());
                }
            });
        }
    }

    private Map<String, String> buildColumnLabels(MetricDefinition metric) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (metric.getDimensions() != null) {
            metric.getDimensions().forEach(d -> labels.put(d.getFieldCode(), d.getFieldName()));
        }
        String valueCol = metric.getAlias() != null ? metric.getAlias() : metric.getCode();
        labels.put(valueCol, metric.getName());
        return labels;
    }
}
