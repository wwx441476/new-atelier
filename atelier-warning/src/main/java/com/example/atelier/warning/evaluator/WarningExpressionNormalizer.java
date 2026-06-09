package com.example.atelier.warning.evaluator;

/**
 * 预警表达式预处理：支持中文逻辑符，统一空白。
 */
final class WarningExpressionNormalizer {

    private WarningExpressionNormalizer() {
    }

    static String normalize(String expression) {
        if (expression == null) {
            return "";
        }
        String normalized = expression.trim();
        normalized = normalized.replace("或者", " || ");
        normalized = normalized.replace("并且", " && ");
        normalized = normalized.replace("或", " || ");
        normalized = normalized.replace("且", " && ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }
}
