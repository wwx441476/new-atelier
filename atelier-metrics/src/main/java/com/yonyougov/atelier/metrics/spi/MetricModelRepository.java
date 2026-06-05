package com.yonyougov.atelier.metrics.spi;

import com.yonyougov.atelier.domain.model.MetricModel;

import java.util.Optional;

/**
 * 模型仓储 SPI — 由 infra 层实现，metrics 模块不依赖具体 DAO。
 */
public interface MetricModelRepository {

    Optional<MetricModel> findByCode(String modelCode);
}
