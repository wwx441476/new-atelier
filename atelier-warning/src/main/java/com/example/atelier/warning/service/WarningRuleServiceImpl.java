package com.example.atelier.warning.service;

import com.example.atelier.domain.query.MetricQueryRequest;
import com.example.atelier.domain.query.QueryResult;
import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.domain.warning.WarningRulePreviewResult;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.persistence.entity.WarningRuleEntity;
import com.example.atelier.infra.persistence.jpa.WarningRuleJpaRepository;
import com.example.atelier.query.service.MetricQueryService;
import com.example.atelier.warning.evaluator.WarningExpressionEvaluator;
import com.example.atelier.warning.spi.WarningRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WarningRuleServiceImpl implements WarningRuleService {

    public static final String TRIGGERED_FIELD = "_triggered";

    private final WarningRuleJpaRepository repository;
    private final MetricQueryService metricQueryService;
    private final WarningExpressionEvaluator evaluator = new WarningExpressionEvaluator();

    public WarningRuleServiceImpl(WarningRuleJpaRepository repository,
                                  MetricQueryService metricQueryService) {
        this.repository = repository;
        this.metricQueryService = metricQueryService;
    }

    @Override
    public List<WarningRule> listRules() {
        return repository.findAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<WarningRule> getRule(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<WarningRule> getByCode(String code) {
        return repository.findByRuleCode(code).map(this::toDomain);
    }

    @Override
    @Transactional
    public WarningRule saveRule(WarningRule rule) {
        if (rule.getCode() == null || rule.getCode().trim().isEmpty()) {
            throw new AtelierException("规则编码不能为空");
        }
        repository.findByRuleCode(rule.getCode()).ifPresent(existing -> {
            if (rule.getId() == null || !existing.getPkWarningRule().equals(rule.getId())) {
                throw new AtelierException("规则编码已存在: " + rule.getCode());
            }
        });
        WarningRuleEntity entity = rule.getId() != null
                ? repository.findById(rule.getId()).orElse(newEntity(rule))
                : newEntity(rule);
        entity.setCatalogCode(rule.getCatalogCode());
        entity.setRuleCode(rule.getCode());
        entity.setRuleName(rule.getName());
        entity.setMetricCodes(joinCodes(rule.getMetricCodes()));
        entity.setExpression(rule.getExpression());
        entity.setEnabled(rule.getEnabled() != null && rule.getEnabled() ? 1 : 0);
        entity.setWarningLevel(rule.getWarningLevel());
        entity.setNotifyConfig(rule.getNotifyConfig());
        entity.setComments(rule.getComments());
        entity.setModifyTime(LocalDateTime.now());
        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional
    public void deleteRule(String id) {
        repository.deleteById(id);
    }

    @Override
    public boolean evaluateExpression(String expression, Map<String, Object> metricValues) {
        return evaluator.evaluate(expression, metricValues);
    }

    @Override
    public WarningRulePreviewResult previewRule(String id, int pageIndex, int pageSize) {
        WarningRule rule = getRule(id)
                .orElseThrow(() -> new AtelierException("预警规则不存在: " + id));
        if (rule.getMetricCodes() == null || rule.getMetricCodes().isEmpty()) {
            throw new AtelierException("规则未关联指标，无法预览");
        }
        if (rule.getExpression() == null || rule.getExpression().trim().isEmpty()) {
            throw new AtelierException("规则表达式为空，无法预览");
        }

        int page = pageIndex <= 0 ? 1 : pageIndex;
        int size = pageSize <= 0 ? 20 : pageSize;

        MetricQueryRequest request = MetricQueryRequest.builder()
                .metricCodes(rule.getMetricCodes())
                .pageIndex(page)
                .pageSize(size)
                .applyRowAuth(false)
                .build();
        QueryResult queryResult = metricQueryService.query(request);

        List<Map<String, Object>> previewRows = new ArrayList<>();
        long matched = 0;
        for (Map<String, Object> row : queryResult.getRows()) {
            Map<String, Object> enriched = new LinkedHashMap<>(row);
            boolean triggered = evaluateExpression(rule.getExpression(),
                    buildMetricContext(row, rule.getMetricCodes()));
            enriched.put(TRIGGERED_FIELD, triggered);
            if (triggered) {
                matched++;
            }
            previewRows.add(enriched);
        }

        Map<String, String> headers = new LinkedHashMap<>();
        if (queryResult.getHeaders() != null) {
            headers.putAll(queryResult.getHeaders());
        }
        headers.put(TRIGGERED_FIELD, "是否触发");

        return WarningRulePreviewResult.builder()
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .expression(rule.getExpression())
                .total(queryResult.getTotal())
                .matchedCount(matched)
                .rows(previewRows)
                .headers(headers)
                .build();
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

    private WarningRuleEntity newEntity(WarningRule rule) {
        return WarningRuleEntity.builder()
                .pkWarningRule(rule.getId() != null ? rule.getId() : UUID.randomUUID().toString())
                .createTime(LocalDateTime.now())
                .build();
    }

    private WarningRule toDomain(WarningRuleEntity entity) {
        return WarningRule.builder()
                .id(entity.getPkWarningRule())
                .catalogCode(entity.getCatalogCode())
                .code(entity.getRuleCode())
                .name(entity.getRuleName())
                .metricCodes(splitCodes(entity.getMetricCodes()))
                .expression(entity.getExpression())
                .enabled(entity.getEnabled() != null && entity.getEnabled() == 1)
                .warningLevel(entity.getWarningLevel())
                .notifyConfig(entity.getNotifyConfig())
                .comments(entity.getComments())
                .build();
    }

    private String joinCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        return String.join(",", codes);
    }

    private List<String> splitCodes(String codes) {
        if (codes == null || codes.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(codes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
