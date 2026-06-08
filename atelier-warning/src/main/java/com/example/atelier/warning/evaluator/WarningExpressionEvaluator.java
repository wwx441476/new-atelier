package com.example.atelier.warning.evaluator;

import com.ql.util.express.DefaultContext;
import com.ql.util.express.ExpressRunner;
import com.example.atelier.infra.exception.AtelierException;

import java.util.Map;

/**
 * 预警表达式评估 — 基于 QLExpress 的简化桩实现。
 */
public class WarningExpressionEvaluator {

    private final ExpressRunner runner = new ExpressRunner();

    public boolean evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }
        try {
            DefaultContext<String, Object> qlContext = new DefaultContext<>();
            if (context != null) {
                context.forEach(qlContext::put);
            }
            Object result = runner.execute(expression, qlContext, null, true, false);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            if (result instanceof Number) {
                return ((Number) result).doubleValue() != 0;
            }
            return result != null;
        } catch (Exception e) {
            throw new AtelierException("表达式评估失败: " + e.getMessage(), e);
        }
    }
}
