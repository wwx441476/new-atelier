package com.yonyougov.atelier.infra.persistence.service;

import com.yonyougov.atelier.domain.metric.MetricDefinition;
import com.yonyougov.atelier.infra.persistence.JpaMetricDefinitionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 指标定义管理服务 — CRUD 封装，供 API 层调用。
 */
@Service
public class MetricDefinitionService {

    private final JpaMetricDefinitionRepository repository;

    public MetricDefinitionService(JpaMetricDefinitionRepository repository) {
        this.repository = repository;
    }

    public List<MetricDefinition> listAll() {
        return repository.findAll();
    }

    public Optional<MetricDefinition> getByCode(String code) {
        return repository.findByCode(code);
    }

    public MetricDefinition save(MetricDefinition definition) {
        return repository.save(definition);
    }

    public void deleteByCode(String code) {
        repository.deleteByCode(code);
    }
}
