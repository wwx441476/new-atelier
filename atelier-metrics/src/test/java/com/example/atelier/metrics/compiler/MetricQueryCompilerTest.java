package com.example.atelier.metrics.compiler;

import com.example.atelier.domain.metric.AggregationType;
import com.example.atelier.domain.metric.DimensionBinding;
import com.example.atelier.domain.metric.FilterCondition;
import com.example.atelier.domain.metric.FilterGroup;
import com.example.atelier.domain.metric.FilterOperator;
import com.example.atelier.domain.metric.MetricDefinition;
import com.example.atelier.domain.metric.MetricType;
import com.example.atelier.domain.model.MetricModel;
import com.example.atelier.domain.query.CompiledQuery;
import com.example.atelier.domain.query.MetricQueryRequest;
import com.example.atelier.metrics.spi.MetricDefinitionRepository;
import com.example.atelier.metrics.spi.MetricModelRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MetricQueryCompilerTest {

    private MetricQueryCompiler compiler;

    @Before
    public void setUp() {
        compiler = new MetricQueryCompiler(new StubDefinitionRepo(), new StubModelRepo());
    }

    @Test
    public void shouldCompileTableMetricWithQueryTimeFilter() {
        MetricQueryRequest request = MetricQueryRequest.builder()
                .metricCodes(Collections.singletonList("revenue"))
                .filters(Collections.singletonList(FilterCondition.builder()
                        .field("dept_code")
                        .operator(FilterOperator.IN)
                        .values(Arrays.asList("001", "002"))
                        .build()))
                .build();

        CompiledQuery result = compiler.compile(request);

        Assert.assertTrue(result.getSql().contains("SUM(orders.amount)"));
        Assert.assertTrue(result.getSql().contains("dept_code IN ('001', '002')"));
    }

    @Test
    public void shouldCompileTableMetricWithOrFilterGroups() {
        MetricQueryRequest request = MetricQueryRequest.builder()
                .metricCodes(Collections.singletonList("revenue"))
                .filterGroups(Arrays.asList(
                        FilterGroup.builder().conditions(Arrays.asList(
                                FilterCondition.builder().field("dept_code").operator(FilterOperator.IN)
                                        .values(Collections.singletonList("001")).build(),
                                FilterCondition.builder().field("fiscal_year").operator(FilterOperator.IN)
                                        .values(Collections.singletonList("2024")).build()
                        )).build(),
                        FilterGroup.builder().conditions(Arrays.asList(
                                FilterCondition.builder().field("dept_code").operator(FilterOperator.IN)
                                        .values(Collections.singletonList("002")).build(),
                                FilterCondition.builder().field("fiscal_year").operator(FilterOperator.IN)
                                        .values(Collections.singletonList("2025")).build()
                        )).build()
                ))
                .build();

        CompiledQuery result = compiler.compile(request);

        Assert.assertTrue(result.getSql().contains(
                "(dept_code IN ('001') AND fiscal_year IN ('2024')) OR (dept_code IN ('002') AND fiscal_year IN ('2025'))"));
    }

    @Test
    public void shouldCompileMultipleMetricsWithSharedDimensions() {
        MetricQueryRequest request = MetricQueryRequest.builder()
                .metricCodes(Arrays.asList("profit", "cost"))
                .build();

        CompiledQuery result = compiler.compile(request);

        Assert.assertTrue(result.getSql().contains("M0.dept_code"));
        Assert.assertTrue(result.getSql().contains("M0.profit"));
        Assert.assertTrue(result.getSql().contains("M1.cost"));
        Assert.assertTrue(result.getSql().contains("M0.dept_code = M1.dept_code"));
        Assert.assertTrue(result.getColumnLabels().containsKey("dept_code"));
        Assert.assertTrue(result.getColumnLabels().containsKey("profit"));
        Assert.assertTrue(result.getColumnLabels().containsKey("cost"));
    }

    @Test
    public void shouldCompileRevenueCostProfitTogether() {
        MetricQueryRequest request = MetricQueryRequest.builder()
                .metricCodes(Arrays.asList("revenue", "cost", "profit"))
                .build();

        CompiledQuery result = compiler.compile(request);
        String sql = result.getSql();

        Assert.assertTrue(sql.contains("M0.revenue"));
        Assert.assertTrue(sql.contains("M1.cost"));
        Assert.assertTrue(sql.contains("M2.profit"));
        Assert.assertTrue(sql.contains("M0.dept_code = M2.dept_code"));
        Assert.assertTrue(sql.contains("M0.fiscal_year = M2.fiscal_year"));
        Assert.assertFalse(sql.contains("M2.year"));
    }

    @Test
    public void shouldCompileCompositeMetricWithFilterOnlyInDependencies() {
        MetricQueryRequest request = MetricQueryRequest.builder()
                .metricCodes(Collections.singletonList("profit"))
                .filters(Collections.singletonList(FilterCondition.builder()
                        .field("fiscal_year")
                        .operator(FilterOperator.IN)
                        .values(Collections.singletonList("2024"))
                        .build()))
                .build();

        CompiledQuery result = compiler.compile(request);
        String sql = result.getSql();

        int filterCount = 0;
        int idx = 0;
        String needle = "fiscal_year IN ('2024')";
        while ((idx = sql.indexOf(needle, idx)) != -1) {
            filterCount++;
            idx += needle.length();
        }
        Assert.assertEquals(2, filterCount);
        // 过滤应落在依赖子查询内，而非复合指标外层 JOIN 之后
        int joinOnEnd = sql.lastIndexOf(" ON ");
        int outerWhere = sql.indexOf(" WHERE ", joinOnEnd);
        Assert.assertTrue(outerWhere < 0);
    }

    @Test
    public void shouldCompileCompositeMetricByCode() {
        MetricQueryRequest request = MetricQueryRequest.builder()
                .metricCodes(Collections.singletonList("profit"))
                .build();

        CompiledQuery result = compiler.compile(request);

        Assert.assertTrue(result.getSql().contains("T0.revenue - T1.cost"));
        Assert.assertFalse(result.getSql().matches("(?s).*SELECT revenue - cost.*"));
        Assert.assertTrue(result.getSql().contains("INNER JOIN"));
    }

    private static class StubDefinitionRepo implements MetricDefinitionRepository {
        private final List<DimensionBinding> tableDimensions = Arrays.asList(
                DimensionBinding.builder().dimensionCode("dept").fieldCode("dept_code")
                        .fieldName("部门").sort(1).build(),
                DimensionBinding.builder().dimensionCode("year").fieldCode("fiscal_year")
                        .fieldName("年度").sort(2).build()
        );

        private final MetricDefinition revenue = MetricDefinition.builder()
                .code("revenue").name("收入").type(MetricType.TABLE)
                .datasourceId("ds1").modelCode("m1")
                .tableCode("orders").fieldCode("amount")
                .aggregation(AggregationType.SUM).alias("revenue")
                .dimensions(tableDimensions)
                .build();

        private final MetricDefinition cost = MetricDefinition.builder()
                .code("cost").name("成本").type(MetricType.TABLE)
                .datasourceId("ds1").modelCode("m1")
                .tableCode("orders").fieldCode("cost_amount")
                .aggregation(AggregationType.SUM).alias("cost")
                .dimensions(tableDimensions)
                .build();

        private final MetricDefinition profit = MetricDefinition.builder()
                .code("profit").name("利润").type(MetricType.COMPOSITE)
                .datasourceId("ds1").formula("revenue - cost").alias("profit")
                .dimensions(tableDimensions)
                .build();

        @Override
        public Optional<MetricDefinition> findByCode(String code) {
            switch (code) {
                case "revenue": return Optional.of(revenue);
                case "cost": return Optional.of(cost);
                case "profit": return Optional.of(profit);
                default: return Optional.empty();
            }
        }

        @Override
        public List<MetricDefinition> findByCodes(List<String> codes) {
            return codes.stream().map(c -> findByCode(c).orElse(null)).filter(d -> d != null)
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    private static class StubModelRepo implements MetricModelRepository {
        @Override
        public Optional<MetricModel> findByCode(String modelCode) {
            return Optional.of(MetricModel.builder()
                    .modelCode("m1").mainTableCode("orders").datasourceId("ds1")
                    .build());
        }
    }
}
