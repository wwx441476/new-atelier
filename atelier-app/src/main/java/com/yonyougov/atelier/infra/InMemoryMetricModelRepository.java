package com.yonyougov.atelier.infra;

import com.yonyougov.atelier.domain.model.MetricModel;
import com.yonyougov.atelier.domain.model.TableJoin;
import com.yonyougov.atelier.metrics.spi.MetricModelRepository;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存模型仓储 — 演示用。
 */
public class InMemoryMetricModelRepository implements MetricModelRepository {

    private final Map<String, MetricModel> store = new ConcurrentHashMap<>();

    public InMemoryMetricModelRepository() {
        store.put("finance_model", MetricModel.builder()
                .modelCode("finance_model")
                .modelName("财务事实模型")
                .datasourceId("ds-demo")
                .mainTableCode("orders")
                .joins(Collections.singletonList(TableJoin.builder()
                        .joinType("LEFT JOIN")
                        .tableCode("dept")
                        .leftTableCode("orders")
                        .joinFields(Collections.singletonList(
                                TableJoin.JoinField.builder().leftField("dept_id").rightField("id").build()))
                        .build()))
                .build());
    }

    @Override
    public Optional<MetricModel> findByCode(String modelCode) {
        return Optional.ofNullable(store.get(modelCode));
    }
}
