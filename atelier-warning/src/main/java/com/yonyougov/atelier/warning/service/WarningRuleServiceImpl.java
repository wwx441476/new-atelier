package com.yonyougov.atelier.warning.service;

import com.yonyougov.atelier.domain.warning.WarningRule;
import com.yonyougov.atelier.infra.exception.AtelierException;
import com.yonyougov.atelier.infra.persistence.entity.WarningRuleEntity;
import com.yonyougov.atelier.infra.persistence.jpa.WarningRuleJpaRepository;
import com.yonyougov.atelier.warning.evaluator.WarningExpressionEvaluator;
import com.yonyougov.atelier.warning.spi.WarningRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WarningRuleServiceImpl implements WarningRuleService {

    private final WarningRuleJpaRepository repository;
    private final WarningExpressionEvaluator evaluator = new WarningExpressionEvaluator();

    public WarningRuleServiceImpl(WarningRuleJpaRepository repository) {
        this.repository = repository;
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
