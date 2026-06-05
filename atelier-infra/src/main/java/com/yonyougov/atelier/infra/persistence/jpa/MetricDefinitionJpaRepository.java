package com.yonyougov.atelier.infra.persistence.jpa;

import com.yonyougov.atelier.infra.persistence.entity.MetricDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MetricDefinitionJpaRepository extends JpaRepository<MetricDefinitionEntity, String> {

    Optional<MetricDefinitionEntity> findByMetricCode(String metricCode);

    List<MetricDefinitionEntity> findByEnabled(Integer enabled);

    void deleteByMetricCode(String metricCode);
}
