package com.example.atelier.warning.service;

import com.example.atelier.domain.metric.FilterCondition;
import com.example.atelier.domain.metric.FilterGroup;
import com.example.atelier.domain.query.MetricQueryRequest;
import com.example.atelier.domain.query.QueryResult;
import com.example.atelier.domain.warning.CompositeRuleConfig;
import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.domain.warning.WarningRulePreviewResult;
import com.example.atelier.domain.warning.WarningRuleType;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.metadata.spi.MetadataService;
import com.example.atelier.query.service.MetricQueryService;
import com.example.atelier.warning.evaluator.WarningExpressionEvaluator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        WarningRuleType type = rule.getRuleType() != null ? rule.getRuleType() : WarningRuleType.METRIC;
        switch (type) {
            case SEMANTIC:
                return previewSemantic(rule, pageIndex, pageSize);
            case COMPOSITE:
                return previewComposite(rule, pageIndex, pageSize);
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

    private WarningRulePreviewResult previewSemantic(WarningRule rule, int pageIndex, int pageSize) {
        SemanticRuleConfig semantic = requireSemanticConfig(rule);
        int page = normalizePage(pageIndex);
        int size = normalizeSize(pageSize);
        QueryResult queryResult = metadataService.previewTableData(semantic.getMetaTableId(), page, size);

        List<Map<String, Object>> previewRows = new ArrayList<>();
        long matched = 0;
        for (Map<String, Object> row : queryResult.getRows()) {
            Map<String, Object> enriched = new LinkedHashMap<>(row);
            SemanticMatchResult match = evaluateSemanticRow(row, semantic);
            enriched.put(TRIGGERED_FIELD, match.isTriggered());
            enriched.put(MATCH_REASON_FIELD, match.getReason());
            enriched.put(MATCH_LAYER_FIELD, match.getLayer());
            enriched.put(LLM_INVOKED_FIELD, match.isLlmInvoked());
            if (match.isTriggered()) {
                matched++;
            }
            previewRows.add(enriched);
        }

        Map<String, String> headers = buildHeaders(queryResult.getHeaders());
        headers.put(TRIGGERED_FIELD, "是否触发");
        headers.put(MATCH_REASON_FIELD, "命中原因");
        headers.put(MATCH_LAYER_FIELD, "判定层");
        headers.put(LLM_INVOKED_FIELD, "LLM调用");

        return buildResult(rule, summarizeSemantic(semantic), queryResult.getSql(),
                queryResult.getTotal(), matched, previewRows, headers);
    }

    private WarningRulePreviewResult previewComposite(WarningRule rule, int pageIndex, int pageSize) {
        validateMetricRule(rule);
        SemanticRuleConfig semantic = requireSemanticConfig(rule);
        CompositeRuleConfig composite = rule.getRuleConfig();
        boolean andLogic = !"OR".equalsIgnoreCase(composite.getTriggerLogic());

        int page = normalizePage(pageIndex);
        int size = normalizeSize(pageSize);
        QueryResult queryResult = metadataService.previewTableData(semantic.getMetaTableId(), page, size);

        List<Map<String, Object>> previewRows = new ArrayList<>();
        long matched = 0;
        for (Map<String, Object> row : queryResult.getRows()) {
            Map<String, Object> enriched = new LinkedHashMap<>(row);
            boolean metricTriggered = expressionEvaluator.evaluate(rule.getExpression(),
                    buildMetricContext(row, rule.getMetricCodes()));
            SemanticMatchResult semanticMatch = evaluateSemanticRow(row, semantic);
            boolean semanticTriggered = semanticMatch.isTriggered();
            boolean triggered = andLogic
                    ? metricTriggered && semanticTriggered
                    : metricTriggered || semanticTriggered;

            enriched.put(METRIC_TRIGGERED_FIELD, metricTriggered);
            enriched.put(SEMANTIC_TRIGGERED_FIELD, semanticTriggered);
            enriched.put(TRIGGERED_FIELD, triggered);
            enriched.put(MATCH_REASON_FIELD, semanticMatch.getReason());
            enriched.put(MATCH_LAYER_FIELD, semanticMatch.getLayer());
            enriched.put(LLM_INVOKED_FIELD, semanticMatch.isLlmInvoked());
            if (triggered) {
                matched++;
            }
            previewRows.add(enriched);
        }

        Map<String, String> headers = buildHeaders(queryResult.getHeaders());
        headers.put(METRIC_TRIGGERED_FIELD, "指标触发");
        headers.put(SEMANTIC_TRIGGERED_FIELD, "语义触发");
        headers.put(TRIGGERED_FIELD, "是否触发");
        headers.put(MATCH_REASON_FIELD, "语义原因");
        headers.put(MATCH_LAYER_FIELD, "语义层");
        headers.put(LLM_INVOKED_FIELD, "LLM调用");

        String summary = rule.getExpression() + " " + (andLogic ? "且" : "或") + " " + summarizeSemantic(semantic);
        return buildResult(rule, summary, queryResult.getSql(), queryResult.getTotal(), matched, previewRows, headers);
    }

    private SemanticMatchResult evaluateSemanticRow(Map<String, Object> row, SemanticRuleConfig semantic) {
        Object raw = row.get(semantic.getFieldCode());
        String text = raw != null ? String.valueOf(raw) : "";
        return semanticRuleEvaluator.evaluate(text, semantic);
    }

    private SemanticRuleConfig requireSemanticConfig(WarningRule rule) {
        if (rule.getRuleConfig() == null || rule.getRuleConfig().getSemantic() == null) {
            throw new AtelierException("语义配置不完整，无法预览");
        }
        SemanticRuleConfig semantic = rule.getRuleConfig().getSemantic();
        if (semantic.getMetaTableId() == null || semantic.getMetaTableId().trim().isEmpty()) {
            throw new AtelierException("请选择元数据表");
        }
        if (semantic.getFieldCode() == null || semantic.getFieldCode().trim().isEmpty()) {
            throw new AtelierException("请选择检测字段");
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
        Map<String, Object> context = new HashMap<>();
        for (String code : metricCodes) {
            if (row.containsKey(code)) {
                context.put(code, row.get(code));
            }
        }
        return context;
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

    private String summarizeSemantic(SemanticRuleConfig semantic) {
        return semantic.getFieldCode() + "·语义合规";
    }

    private int normalizePage(int pageIndex) {
        return pageIndex <= 0 ? 1 : pageIndex;
    }

    private int normalizeSize(int pageSize) {
        return pageSize <= 0 ? 20 : pageSize;
    }
}
