package com.example.atelier.warning.evaluator;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class RowMetricContextResolverTest {

    @Test
    public void resolveProfit_fromAmountAndCost() {
        Map<String, Object> row = new HashMap<>();
        row.put("amount", new BigDecimal("500.00"));
        row.put("cost_amount", new BigDecimal("300.00"));

        Map<String, Object> context = RowMetricContextResolver.buildContext(row, Arrays.asList("profit"));

        Assert.assertEquals(200D, ((Number) context.get("profit")).doubleValue(), 0.001);
    }
}
