package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.warning.ExpressionValidateResult;
import com.example.atelier.infra.exception.AtelierException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 预警表达式校验：变量引用、语法试算。
 */
public class WarningExpressionValidator {

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "and", "or", "not", "true", "false", "null"
    ));

    private static final Pattern IDENTIFIER = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");

    private final WarningExpressionEvaluator evaluator = new WarningExpressionEvaluator();

    public ExpressionValidateResult validate(String expression, List<String> metricCodes) {
        List<String> codes = metricCodes != null ? metricCodes : Collections.<String>emptyList();
        String normalized = WarningExpressionNormalizer.normalize(expression);
        List<String> errors = new ArrayList<>();

        if (normalized.isEmpty()) {
            return ExpressionValidateResult.builder()
                    .valid(false)
                    .normalizedExpression(normalized)
                    .usedVariables(Collections.<String>emptyList())
                    .unknownVariables(Collections.<String>emptyList())
                    .unusedMetrics(codes)
                    .message("表达式不能为空")
                    .build();
        }

        List<String> usedVariables = extractVariables(normalized);
        Set<String> codeSet = new HashSet<>(codes);
        List<String> unknownVariables = usedVariables.stream()
                .filter(variable -> !codeSet.contains(variable))
                .collect(Collectors.toList());
        List<String> unusedMetrics = codes.stream()
                .filter(code -> !usedVariables.contains(code))
                .collect(Collectors.toList());

        if (usedVariables.isEmpty()) {
            errors.add("表达式中未引用任何指标变量，请使用关联指标的 code（如 profit）");
        }
        if (!unknownVariables.isEmpty()) {
            errors.add("未关联的指标变量: " + String.join(", ", unknownVariables)
                    + "，请先在「关联指标」中选择或在表达式中移除");
        }
        if (codes.isEmpty()) {
            errors.add("请至少选择一个关联指标");
        }
        validateParentheses(normalized, errors);

        Boolean sampleEvaluated = null;
        Boolean sampleTriggered = null;
        if (errors.isEmpty()) {
            try {
                Map<String, Object> sampleValues = usedVariables.stream()
                        .collect(Collectors.toMap(code -> code, code -> 0));
                sampleTriggered = evaluator.evaluate(normalized, sampleValues);
                sampleEvaluated = true;
            } catch (AtelierException e) {
                errors.add("语法错误: " + e.getMessage());
                sampleEvaluated = false;
            } catch (Exception e) {
                errors.add("表达式无法求值: " + e.getMessage());
                sampleEvaluated = false;
            }
        }

        boolean valid = errors.isEmpty();
        String message = valid
                ? buildSuccessMessage(unusedMetrics, sampleTriggered)
                : String.join("；", errors);

        return ExpressionValidateResult.builder()
                .valid(valid)
                .normalizedExpression(normalized)
                .usedVariables(usedVariables)
                .unknownVariables(unknownVariables)
                .unusedMetrics(unusedMetrics)
                .message(message)
                .sampleEvaluated(sampleEvaluated)
                .sampleTriggered(sampleTriggered)
                .build();
    }

    private static List<String> extractVariables(String normalizedExpression) {
        Set<String> variables = new LinkedHashSet<>();
        Matcher matcher = IDENTIFIER.matcher(normalizedExpression);
        while (matcher.find()) {
            String identifier = matcher.group(1);
            if (!KEYWORDS.contains(identifier.toLowerCase())) {
                variables.add(identifier);
            }
        }
        return new ArrayList<>(variables);
    }

    private static void validateParentheses(String expression, List<String> errors) {
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char ch = expression.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth < 0) {
                    errors.add("括号不匹配：存在多余的右括号 )");
                    return;
                }
            }
        }
        if (depth > 0) {
            errors.add("括号不匹配：存在 " + depth + " 个未闭合的左括号 (");
        }
    }

    private static String buildSuccessMessage(List<String> unusedMetrics, Boolean sampleTriggered) {
        StringBuilder message = new StringBuilder("表达式语法正确");
        if (!unusedMetrics.isEmpty()) {
            message.append("；未使用的关联指标: ").append(String.join(", ", unusedMetrics));
        }
        if (sampleTriggered != null) {
            message.append("；样例值(均为0)试算结果: ").append(sampleTriggered ? "触发" : "未触发");
        }
        return message.toString();
    }
}
