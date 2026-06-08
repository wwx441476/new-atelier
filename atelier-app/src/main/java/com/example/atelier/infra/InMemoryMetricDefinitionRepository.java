package com.example.atelier.infra;

import com.example.atelier.domain.metric.AggregationType;
import com.example.atelier.domain.metric.DimensionBinding;
import com.example.atelier.domain.metric.MetricDefinition;
import com.example.atelier.domain.metric.MetricType;
import com.example.atelier.metrics.spi.MetricDefinitionRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存指标仓储 — 演示用，后续替换为 JPA 实现。
 */
public class InMemoryMetricDefinitionRepository implements MetricDefinitionRepository {

    private final Map<String, MetricDefinition> store = new ConcurrentHashMap<>();

    public InMemoryMetricDefinitionRepository() {
        seedDemoData();
    }

    private void seedDemoData() {
        store.put("revenue", MetricDefinition.builder()
                .code("revenue")
                .name("营业收入")
                .type(MetricType.TABLE)
                .datasourceId("ds-demo")
                .modelCode("finance_model")
                .tableCode("orders")
                .fieldCode("amount")
                .aggregation(AggregationType.SUM)
                .alias("revenue")
                .dimensions(Arrays.asList(
                        DimensionBinding.builder().dimensionCode("dept").fieldCode("dept_code").fieldName("部门").sort(1).build(),
                        DimensionBinding.builder().dimensionCode("year").fieldCode("fiscal_year").fieldName("年度").sort(2).build()
                ))
                .build());

        store.put("cost", MetricDefinition.builder()
                .code("cost")
                .name("营业成本")
                .type(MetricType.TABLE)
                .datasourceId("ds-demo")
                .modelCode("finance_model")
                .tableCode("orders")
                .fieldCode("cost_amount")
                .aggregation(AggregationType.SUM)
                .alias("cost")
                .dimensions(Arrays.asList(
                        DimensionBinding.builder().dimensionCode("dept").fieldCode("dept_code").fieldName("部门").sort(1).build(),
                        DimensionBinding.builder().dimensionCode("year").fieldCode("fiscal_year").fieldName("年度").sort(2).build()
                ))
                .build());

        store.put("profit", MetricDefinition.builder()
                .code("profit")
                .name("利润")
                .type(MetricType.COMPOSITE)
                .datasourceId("ds-demo")
                .formula("revenue - cost")
                .alias("profit")
                .dimensions(Arrays.asList(
                        DimensionBinding.builder().dimensionCode("dept").fieldCode("dept_code").fieldName("部门").sort(1).build(),
                        DimensionBinding.builder().dimensionCode("year").fieldCode("fiscal_year").fieldName("年度").sort(2).build()
                ))
                .build());
    }

    @Override
    public Optional<MetricDefinition> findByCode(String code) {
        return Optional.ofNullable(store.get(code));
    }

    @Override
    public List<MetricDefinition> findByCodes(List<String> codes) {
        return codes.stream()
                .map(store::get)
                .filter(def -> def != null)
                .collect(Collectors.toList());
    }

    public void save(MetricDefinition definition) {
        store.put(definition.getCode(), definition);
    }
}
