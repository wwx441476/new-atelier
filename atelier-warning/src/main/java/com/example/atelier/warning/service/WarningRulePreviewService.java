package com.example.atelier.warning.service;

import com.example.atelier.domain.metric.FilterCondition;
import com.example.atelier.domain.metric.FilterGroup;
import com.example.atelier.domain.query.MetricQueryRequest;
import com.example.atelier.domain.query.QueryResult;
import com.example.atelier.domain.warning.CompositeRuleConfig;
import com.example.atelier.domain.warning.SemanticGroupMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import com.example.atelier.warning.evaluator.SemanticEvaluationOptions;
import com.example.atelier.warning.evaluator.SemanticRuleConfigSupport;
import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.domain.warning.WarningRulePreviewResult;
import com.example.atelier.domain.warning.WarningRuleType;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.metadata.spi.MetadataService;
import com.example.atelier.query.service.MetricQueryService;
import com.example.atelier.warning.evaluator.RowMetricContextResolver;
import com.example.atelier.warning.evaluator.WarningExpressionEvaluator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
public class WarningRulePreviewService {

    public static final String TRIGGERED_FIELD = "_triggered";
    public static final String MATCH_REASON_FIELD = "_matchReason";
    public static final String MATCH_LAYER_FIELD = "_matchLayer";
    public static final String LLM_INVOKED_FIELD = "_llmInvoked";
    public static final String METRIC_TRIGGERED_FIELD = "_metricTriggered";
    public static final String SEMANTIC_TRIGGERED_FIELD = "_semanticTriggered";

    private final MetricQueryService metricQueryService;
    private final MetadataService metadataService;
    private final SemanticRuleEvaluator semanticRuleEvaluator;
    private final WarningExpressionEvaluator expressionEvaluator = new WarningExpressionEvaluator();

    public WarningRulePreviewService(MetricQueryService metricQueryService,
                                     MetadataService metadataService,
                                     SemanticRuleEvaluator semanticRuleEvaluator) {
        this.metricQueryService = metricQueryService;
        this.metadataService = metadataService;
        this.semanticRuleEvaluator = semanticRuleEvaluator;
    }

    public WarningRulePreviewResult preview(WarningRule rule, int pageIndex, int pageSize,
                                            List<FilterCondition> filters,
                                            List<FilterGroup> filterGroups) {
        return preview(rule, pageIndex, pageSize, filters, filterGroups, SemanticEvaluationOptions.defaults());
    }

    public WarningRulePreviewResult preview(WarningRule rule, int pageIndex, int pageSize,
                                            List<FilterCondition> filters,
                                            List<FilterGroup> filterGroups,
                                            SemanticEvaluationOptions evaluationOptions) {
        WarningRuleType type = rule.getRuleType() != null ? rule.getRuleType() : WarningRuleType.METRIC;
        switch (type) {
            case SEMANTIC:
                return previewSemantic(rule, pageIndex, pageSize, evaluationOptions);
            case COMPOSITE:
                return previewComposite(rule, pageIndex, pageSize, evaluationOptions);
            case METRIC:
            default:
                return previewMetric(rule, pageIndex, pageSize, filters, filterGroups);
        }
    }

    private WarningRulePreviewResult previewMetric(WarningRule rule, int pageIndex, int pageSize,
                                                   List<FilterCondition> filters,
                                                   List<FilterGroup> filterGroups) {
        validateMetricRule(rule);
        int page = normalizePage(pageIndex);
        int size = normalizeSize(pageSize);

        MetricQueryRequest request = MetricQueryRequest.builder()
                .metricCodes(rule.getMetricCodes())
                .filters(filters)
                .filterGroups(filterGroups)
                .pageIndex(page)
                .pageSize(size)
                .applyRowAuth(false)
                .build();
        QueryResult queryResult = metricQueryService.query(request);
        String sql = metricQueryService.compileOnly(request).getSql();

        List<Map<String, Object>> previewRows = new ArrayList<>();
        long matched = 0;
        for (Map<String, Object> row : queryResult.getRows()) {
            Map<String, Object> enriched = new LinkedHashMap<>(row);
            boolean triggered = expressionEvaluator.evaluate(rule.getExpression(),
                    buildMetricContext(row, rule.getMetricCodes()));
            enriched.put(TRIGGERED_FIELD, triggered);
            if (triggered) {
                matched++;
            }
            previewRows.add(enriched);
        }

        Map<String, String> headers = buildHeaders(queryResult.getHeaders());
        headers.put(TRIGGERED_FIELD, "是否触发");

        return buildResult(rule, rule.getExpression(), sql, queryResult.getTotal(), matched, previewRows, headers);
    }

    private WarningRulePreviewResult previewSemantic(WarningRule rule, int pageIndex, int pageSize,
                                                     SemanticEvaluationOptions evaluationOptions) {
        SemanticRuleConfig semantic = requireSemanticConfig(rule);
        int page = normalizePage(pageIndex);
        int size = normalizeSize(pageSize);
        QueryResult queryResult = metadataService.previewTableData(semantic.getMetaTableId(), page, size);

        List<EvaluatedPreviewRow> evaluated = evaluateSemanticRows(
                queryResult.getRows(), semantic, evaluationOptions, false, null);
        long matched = evaluated.stream().filter(EvaluatedPreviewRow::isTriggered).count();

        Map<String, String> headers = buildHeaders(queryResult.getHeaders());
        headers.put(TRIGGERED_FIELD, "是否触发");
        headers.put(SEMANTIC_TRIGGERED_FIELD, "语义触发");
        SemanticPreviewSupport.putHeaders(headers, semantic);

        return buildResult(rule, SemanticPreviewSupport.summarizeSemantic(semantic), queryResult.getSql(),
                queryResult.getTotal(), matched,
                evaluated.stream().map(EvaluatedPreviewRow::getRow).collect(java.util.stream.Collectors.toList()),
                headers);
    }

    private WarningRulePreviewResult previewComposite(WarningRule rule, int pageIndex, int pageSize,
                                                      SemanticEvaluationOptions evaluationOptions) {
        validateMetricRule(rule);
        SemanticRuleConfig semantic = requireSemanticConfig(rule);
        CompositeRuleConfig composite = rule.getRuleConfig();
        boolean andLogic = !"OR".equalsIgnoreCase(composite.getTriggerLogic());

        int page = normalizePage(pageIndex);
        int size = normalizeSize(pageSize);
        QueryResult queryResult = metadataService.previewTableData(semantic.getMetaTableId(), page, size);

        List<EvaluatedPreviewRow> evaluated = evaluateSemanticRows(
                queryResult.getRows(), semantic, evaluationOptions, true, rule);
        long matched = evaluated.stream().filter(EvaluatedPreviewRow::isTriggered).count();

        Map<String, String> headers = buildHeaders(queryResult.getHeaders());
        headers.put(METRIC_TRIGGERED_FIELD, "指标触发");
        headers.put(TRIGGERED_FIELD, "是否触发");
        SemanticPreviewSupport.putHeaders(headers, semantic);

        String summary = rule.getExpression() + " " + (andLogic ? "且" : "或") + " "
                + SemanticPreviewSupport.summarizeSemantic(semantic);
        return buildResult(rule, summary, queryResult.getSql(), queryResult.getTotal(), matched,
                evaluated.stream().map(EvaluatedPreviewRow::getRow).collect(java.util.stream.Collectors.toList()),
                headers);
    }

    private List<EvaluatedPreviewRow> evaluateSemanticRows(List<Map<String, Object>> rows,
                                                           SemanticRuleConfig semantic,
                                                           SemanticEvaluationOptions evaluationOptions,
                                                           boolean composite,
                                                           WarningRule compositeRule) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        boolean andLogic = composite
                && compositeRule.getRuleConfig() != null
                && !"OR".equalsIgnoreCase(compositeRule.getRuleConfig().getTriggerLogic());

        List<EvaluatedPreviewRow> results = Collections.synchronizedList(new ArrayList<>(rows.size()));
        for (int i = 0; i < rows.size(); i++) {
            results.add(null);
        }

        IntStream.range(0, rows.size()).parallel().forEach(index -> {
            Map<String, Object> row = rows.get(index);
            Map<String, Object> enriched = new LinkedHashMap<>(row);
            boolean triggered;
            if (composite) {
                boolean metricTriggered = expressionEvaluator.evaluate(compositeRule.getExpression(),
                        buildMetricContext(row, compositeRule.getMetricCodes()));
                SemanticGroupMatchResult groupResult;
                if (andLogic && !metricTriggered) {
                    groupResult = emptySemanticResult();
                } else {
                    groupResult = semanticRuleEvaluator.evaluateRow(row, semantic, evaluationOptions);
                }
                boolean semanticTriggered = groupResult.isTriggered();
                triggered = andLogic
                        ? metricTriggered && semanticTriggered
                        : metricTriggered || semanticTriggered;
                enriched.put(METRIC_TRIGGERED_FIELD, metricTriggered);
                enriched.put(SEMANTIC_TRIGGERED_FIELD, semanticTriggered);
                enriched.put(TRIGGERED_FIELD, triggered);
                SemanticPreviewSupport.enrichRow(enriched, groupResult);
            } else {
                SemanticGroupMatchResult groupResult =
                        semanticRuleEvaluator.evaluateRow(row, semantic, evaluationOptions);
                triggered = groupResult.isTriggered();
                enriched.put(TRIGGERED_FIELD, triggered);
                enriched.put(SEMANTIC_TRIGGERED_FIELD, triggered);
                SemanticPreviewSupport.enrichRow(enriched, groupResult);
            }
            results.set(index, new EvaluatedPreviewRow(enriched, triggered));
        });

        return new ArrayList<>(results);
    }

    private static SemanticGroupMatchResult emptySemanticResult() {
        return SemanticGroupMatchResult.builder()
                .triggered(false)
                .checkTriggered(Collections.emptyMap())
                .checkResults(Collections.emptyMap())
                .build();
    }

    private SemanticRuleConfig requireSemanticConfig(WarningRule rule) {
        if (rule.getRuleConfig() == null || rule.getRuleConfig().getSemantic() == null) {
            throw new AtelierException("语义配置不完整，无法预览");
        }
        SemanticRuleConfig semantic = rule.getRuleConfig().getSemantic();
        if (semantic.getMetaTableId() == null || semantic.getMetaTableId().trim().isEmpty()) {
            throw new AtelierException("请选择元数据表");
        }
        if (SemanticRuleConfigSupport.normalizeGroups(semantic).isEmpty()) {
            throw new AtelierException("请配置语义条件");
        }
        return semantic;
    }

    private void validateMetricRule(WarningRule rule) {
        if (rule.getMetricCodes() == null || rule.getMetricCodes().isEmpty()) {
            throw new AtelierException("规则未关联指标，无法预览");
        }
        if (rule.getExpression() == null || rule.getExpression().trim().isEmpty()) {
            throw new AtelierException("规则表达式为空，无法预览");
        }
    }

    private Map<String, Object> buildMetricContext(Map<String, Object> row, List<String> metricCodes) {
        return RowMetricContextResolver.buildContext(row, metricCodes);
    }

    private Map<String, String> buildHeaders(Map<String, String> source) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (source != null) {
            headers.putAll(source);
        }
        return headers;
    }

    private WarningRulePreviewResult buildResult(WarningRule rule, String expressionSummary, String sql,
                                                 long total, long matched,
                                                 List<Map<String, Object>> rows,
                                                 Map<String, String> headers) {
        return WarningRulePreviewResult.builder()
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .expression(expressionSummary)
                .sql(sql)
                .total(total)
                .matchedCount(matched)
                .rows(rows)
                .headers(headers)
                .build();
    }

    private int normalizePage(int pageIndex) {
        return pageIndex <= 0 ? 1 : pageIndex;
    }

    private int normalizeSize(int pageSize) {
        return pageSize <= 0 ? 20 : pageSize;
    }

    private static final class EvaluatedPreviewRow {
        private final Map<String, Object> row;
        private final boolean triggered;

        private EvaluatedPreviewRow(Map<String, Object> row, boolean triggered) {
            this.row = row;
            this.triggered = triggered;
        }

        Map<String, Object> getRow() {
            return row;
        }

        boolean isTriggered() {
            return triggered;
        }
    }
}
