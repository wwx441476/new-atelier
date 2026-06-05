package com.yonyougov.atelier.infra.persistence;

import com.yonyougov.atelier.domain.metric.MetricDefinition;
import com.yonyougov.atelier.infra.exception.AtelierException;
import com.yonyougov.atelier.infra.persistence.entity.MetricDefinitionEntity;
import com.yonyougov.atelier.infra.persistence.jpa.MetricDefinitionJpaRepository;
import com.yonyougov.atelier.infra.persistence.mapper.MetricDefinitionJsonMapper;
import com.yonyougov.atelier.metrics.spi.MetricDefinitionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA 指标仓储 — 替代 InMemoryMetricDefinitionRepository。
 */
@Repository
public class JpaMetricDefinitionRepository implements MetricDefinitionRepository {

    private final MetricDefinitionJpaRepository jpaRepository;

    public JpaMetricDefinitionRepository(MetricDefinitionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<MetricDefinition> findByCode(String code) {
        return jpaRepository.findByMetricCode(code)
                .filter(e -> e.getEnabled() == null || e.getEnabled() == 1)
                .map(this::toDomain);
    }

    @Override
    public List<MetricDefinition> findByCodes(List<String> codes) {
        return codes.stream()
                .map(this::findByCode)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    public List<MetricDefinition> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Transactional
    public MetricDefinition save(MetricDefinition definition) {
        if (definition == null || definition.getCode() == null) {
            throw new AtelierException("指标 code 不能为空");
        }
        MetricDefinitionEntity entity = jpaRepository.findByMetricCode(definition.getCode())
                .orElse(MetricDefinitionEntity.builder()
                        .pkMetric(UUID.randomUUID().toString())
                        .createTime(LocalDateTime.now())
                        .enabled(1)
                        .build());
        entity.setMetricCode(definition.getCode());
        entity.setMetricName(definition.getName());
        entity.setCatalogCode(definition.getCatalogCode());
        entity.setMetricType(definition.getType() != null ? definition.getType().name() : null);
        entity.setPkDatasource(definition.getDatasourceId());
        entity.setDefinitionJson(MetricDefinitionJsonMapper.toJson(definition));
        entity.setModifyTime(LocalDateTime.now());
        jpaRepository.save(entity);
        return definition;
    }

    @Transactional
    public void deleteByCode(String code) {
        jpaRepository.deleteByMetricCode(code);
    }

    private MetricDefinition toDomain(MetricDefinitionEntity entity) {
        MetricDefinition definition = MetricDefinitionJsonMapper.fromJson(entity.getDefinitionJson());
        if (definition == null) {
            definition = new MetricDefinition();
        }
        if (definition.getCode() == null) {
            definition.setCode(entity.getMetricCode());
        }
        if (definition.getName() == null) {
            definition.setName(entity.getMetricName());
        }
        if (definition.getCatalogCode() == null) {
            definition.setCatalogCode(entity.getCatalogCode());
        }
        if (definition.getDatasourceId() == null) {
            definition.setDatasourceId(entity.getPkDatasource());
        }
        return definition;
    }
}
