package com.yonyougov.atelier.metrics.spi;

import com.yonyougov.atelier.domain.metric.MetricDefinition;

import java.util.List;
import java.util.Optional;

/**
 * 指标定义仓储 SPI。
 */
public interface MetricDefinitionRepository {

    Optional<MetricDefinition> findByCode(String code);

    List<MetricDefinition> findByCodes(List<String> codes);
}
