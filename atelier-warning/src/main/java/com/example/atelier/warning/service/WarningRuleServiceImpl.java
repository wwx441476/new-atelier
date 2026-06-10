package com.example.atelier.warning.service;

import com.example.atelier.domain.metric.FilterCondition;
import com.example.atelier.domain.metric.FilterGroup;
import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.domain.warning.CompositeRuleConfig;
import com.example.atelier.domain.warning.ExpressionValidateResult;
import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import com.example.atelier.domain.warning.SemanticValidateResult;
import com.example.atelier.domain.warning.WarningRule;
import com.example.atelier.domain.warning.WarningRulePreviewResult;
import com.example.atelier.domain.warning.WarningRuleType;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.persistence.entity.WarningRuleEntity;
import com.example.atelier.infra.persistence.jpa.WarningRuleJpaRepository;
import com.example.atelier.infra.persistence.mapper.RuleConfigMapper;
import com.example.atelier.warning.evaluator.SemanticRuleValidator;
import com.example.atelier.domain.warning.SemanticCheckMode;
import com.example.atelier.domain.warning.SemanticFieldCheck;
import com.example.atelier.domain.warning.SemanticGroupMatchResult;
import com.example.atelier.domain.warning.SemanticSampleCheckResult;
import com.example.atelier.warning.evaluator.SemanticRuleConfigSupport;
import com.example.atelier.warning.evaluator.WarningExpressionEvaluator;
import com.example.atelier.warning.evaluator.WarningExpressionValidator;
import com.example.atelier.warning.spi.WarningRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WarningRuleServiceImpl implements WarningRuleService {

    private final WarningRuleJpaRepository repository;
    private final WarningRulePreviewService previewService;
    private final KeywordExpansionService keywordExpansionService;
    private final SemanticRuleEvaluator semanticRuleEvaluator;
    private final SemanticLlmConfigLoader llmConfigLoader;
    private final WarningExpressionEvaluator evaluator = new WarningExpressionEvaluator();
    private final WarningExpressionValidator expressionValidator = new WarningExpressionValidator();
    private final SemanticRuleValidator semanticRuleValidator = new SemanticRuleValidator();

    public WarningRuleServiceImpl(WarningRuleJpaRepository repository,
                                  WarningRulePreviewService previewService,
                                  KeywordExpansionService keywordExpansionService,
                                  SemanticRuleEvaluator semanticRuleEvaluator,
                                  SemanticLlmConfigLoader llmConfigLoader) {
        this.repository = repository;
        this.previewService = previewService;
        this.keywordExpansionService = keywordExpansionService;
        this.semanticRuleEvaluator = semanticRuleEvaluator;
        this.llmConfigLoader = llmConfigLoader;
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

        WarningRuleType ruleType = rule.getRuleType() != null ? rule.getRuleType() : WarningRuleType.METRIC;
        rule.setRuleType(ruleType);
        validateAndNormalizeRule(rule, ruleType);
        maybeExpandKeywords(rule, ruleType);

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
        entity.setRuleType(ruleType.name());
        entity.setRuleConfig(RuleConfigMapper.toJson(rule.getRuleConfig()));
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
    public ExpressionValidateResult validateExpression(String expression, List<String> metricCodes) {
        return expressionValidator.validate(expression, metricCodes);
    }

    @Override
    public SemanticValidateResult validateSemantic(SemanticRuleConfig config, String sampleText,
                                                   Map<String, Object> sampleRow) {
        SemanticValidateResult base = semanticRuleValidator.validate(config);
        if (!base.isValid()) {
            return base;
        }
        if (sampleRow != null && !sampleRow.isEmpty()) {
            SemanticGroupMatchResult groupResult = semanticRuleEvaluator.evaluateSampleRow(sampleRow, config);
            List<SemanticSampleCheckResult> sampleChecks = buildSampleChecks(config, groupResult);
            boolean triggered = groupResult.isTriggered();
            return SemanticValidateResult.builder()
                    .valid(true)
                    .message(buildSampleMessage(triggered, sampleChecks))
                    .sampleTriggered(triggered)
                    .sampleChecks(sampleChecks)
                    .build();
        }
        if (sampleText == null || sampleText.trim().isEmpty()) {
            return base;
        }
        SemanticMatchResult match = semanticRuleEvaluator.evaluate(sampleText, config);
        return SemanticValidateResult.builder()
                .valid(true)
                .message(base.getMessage())
                .sampleTriggered(match.isTriggered())
                .sampleMatchReason(match.getReason())
                .sampleMatchLayer(match.getLayer())
                .build();
    }

    @Override
    public Map<String, List<String>> expandKeywords(SemanticRuleConfig config) {
        SemanticValidateResult validation = semanticRuleValidator.validate(config);
        if (!validation.isValid()) {
            throw new AtelierException(validation.getMessage());
        }
        Map<String, List<String>> expandedByField = new LinkedHashMap<>();
        SemanticLlmConfig llmConfig = llmConfigLoader.load();
        for (SemanticFieldCheck check : SemanticRuleConfigSupport.flattenChecks(config)) {
            if (check.getFieldCode() == null) {
                continue;
            }
            String mode = check.getMatchMode() != null ? check.getMatchMode().trim().toUpperCase() : "HYBRID";
            if ("LLM".equals(mode)) {
                continue;
            }
            expandedByField.put(check.getFieldCode(),
                    keywordExpansionService.expandKeywords(
                            SemanticRuleConfigSupport.toProbeConfig(check), llmConfig));
        }
        return expandedByField;
    }

    @Override
    public WarningRulePreviewResult previewRule(String id, int pageIndex, int pageSize,
                                                List<FilterCondition> filters,
                                                List<FilterGroup> filterGroups) {
        return previewRule(id, pageIndex, pageSize, filters, filterGroups, false);
    }

    @Override
    public WarningRulePreviewResult previewRule(String id, int pageIndex, int pageSize,
                                                List<FilterCondition> filters,
                                                List<FilterGroup> filterGroups,
                                                boolean keywordOnly) {
        WarningRule rule = getRule(id)
                .orElseThrow(() -> new AtelierException("预警规则不存在: " + id));
        com.example.atelier.warning.evaluator.SemanticEvaluationOptions options = keywordOnly
                ? com.example.atelier.warning.evaluator.SemanticEvaluationOptions.keywordOnly()
                : com.example.atelier.warning.evaluator.SemanticEvaluationOptions.defaults();
        return previewService.preview(rule, pageIndex, pageSize, filters, filterGroups, options);
    }

    private void validateAndNormalizeRule(WarningRule rule, WarningRuleType ruleType) {
        switch (ruleType) {
            case METRIC:
                validateMetricExpression(rule);
                rule.setRuleConfig(null);
                break;
            case SEMANTIC:
                validateSemanticConfig(requireSemantic(rule));
                rule.setMetricCodes(Collections.emptyList());
                rule.setExpression(null);
                break;
            case COMPOSITE:
                validateMetricExpression(rule);
                validateSemanticConfig(requireSemantic(rule));
                if (rule.getRuleConfig().getTriggerLogic() == null
                        || rule.getRuleConfig().getTriggerLogic().trim().isEmpty()) {
                    rule.getRuleConfig().setTriggerLogic("AND");
                }
                break;
            default:
                throw new AtelierException("不支持的规则类型: " + ruleType);
        }
    }

    private void validateMetricExpression(WarningRule rule) {
        ExpressionValidateResult validation = expressionValidator.validate(
                rule.getExpression(), rule.getMetricCodes());
        if (!validation.isValid()) {
            throw new AtelierException(validation.getMessage());
        }
        if (validation.getNormalizedExpression() != null) {
            rule.setExpression(validation.getNormalizedExpression());
        }
    }

    private void validateSemanticConfig(SemanticRuleConfig semantic) {
        SemanticValidateResult validation = semanticRuleValidator.validate(semantic);
        if (!validation.isValid()) {
            throw new AtelierException(validation.getMessage());
        }
        for (SemanticFieldCheck check : SemanticRuleConfigSupport.flattenChecks(semantic)) {
            if (check.getMatchMode() == null || check.getMatchMode().trim().isEmpty()) {
                check.setMatchMode("HYBRID");
            }
        }
    }

    private SemanticRuleConfig requireSemantic(WarningRule rule) {
        if (rule.getRuleConfig() == null || rule.getRuleConfig().getSemantic() == null) {
            throw new AtelierException("语义配置不能为空");
        }
        return rule.getRuleConfig().getSemantic();
    }

    private void maybeExpandKeywords(WarningRule rule, WarningRuleType ruleType) {
        if (ruleType == WarningRuleType.METRIC) {
            return;
        }
        SemanticRuleConfig semantic = requireSemantic(rule);
        SemanticLlmConfig llmConfig = llmConfigLoader.load();
        for (SemanticFieldCheck check : SemanticRuleConfigSupport.flattenChecks(semantic)) {
            String mode = check.getMatchMode() != null ? check.getMatchMode().trim().toUpperCase() : "HYBRID";
            if ("LLM".equals(mode)) {
                continue;
            }
            check.setExpandedKeywords(keywordExpansionService.expandKeywords(
                    SemanticRuleConfigSupport.toProbeConfig(check), llmConfig));
        }
    }

    private WarningRuleEntity newEntity(WarningRule rule) {
        return WarningRuleEntity.builder()
                .pkWarningRule(rule.getId() != null ? rule.getId() : UUID.randomUUID().toString())
                .createTime(LocalDateTime.now())
                .build();
    }

    private WarningRule toDomain(WarningRuleEntity entity) {
        WarningRuleType ruleType = parseRuleType(entity.getRuleType());
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
                .ruleType(ruleType)
                .ruleConfig(RuleConfigMapper.fromJson(entity.getRuleConfig()))
                .comments(entity.getComments())
                .build();
    }

    private WarningRuleType parseRuleType(String ruleType) {
        if (ruleType == null || ruleType.trim().isEmpty()) {
            return WarningRuleType.METRIC;
        }
        return WarningRuleType.valueOf(ruleType);
    }

    private String joinCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        return String.join(",", codes);
    }

    private List<String> splitCodes(String codes) {
        if (codes == null || codes.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(codes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<SemanticSampleCheckResult> buildSampleChecks(SemanticRuleConfig config,
                                                              SemanticGroupMatchResult groupResult) {
        List<SemanticSampleCheckResult> results = new ArrayList<>();
        for (SemanticFieldCheck check : SemanticRuleConfigSupport.flattenChecks(config)) {
            String fieldCode = check.getFieldCode();
            if (fieldCode == null) {
                continue;
            }
            SemanticMatchResult match = groupResult.getCheckResults().get(fieldCode);
            Boolean subMet = groupResult.getCheckTriggered().get(fieldCode);
            results.add(SemanticSampleCheckResult.builder()
                    .fieldCode(fieldCode)
                    .checkMode(check.getCheckMode() != null ? check.getCheckMode() : SemanticCheckMode.VIOLATION)
                    .subConditionMet(Boolean.TRUE.equals(subMet))
                    .reason(match != null ? match.getReason() : null)
                    .layer(match != null ? match.getLayer() : "none")
                    .llmInvoked(match != null && match.isLlmInvoked())
                    .build());
        }
        return results;
    }

    private String buildSampleMessage(boolean triggered, List<SemanticSampleCheckResult> sampleChecks) {
        StringBuilder message = new StringBuilder(triggered ? "样例将触发预警" : "样例不会触发预警");
        if (sampleChecks.isEmpty()) {
            return message.toString();
        }
        message.append("；");
        for (int i = 0; i < sampleChecks.size(); i++) {
            SemanticSampleCheckResult check = sampleChecks.get(i);
            if (i > 0) {
                message.append("，");
            }
            String modeLabel = check.getCheckMode() == SemanticCheckMode.REQUIREMENT ? "必须符合" : "违规检测";
            message.append(check.getFieldCode())
                    .append('(')
                    .append(modeLabel)
                    .append("):")
                    .append(check.isSubConditionMet() ? "满足" : "不满足");
        }
        return message.toString();
    }
}
