package com.example.atelier.dimension.service;

/**
 * 时间维度值格式模板渲染。
 */
final class TimeValueFormatter {

    private TimeValueFormatter() {
    }

    static String format(String template, int year, int month, int quarter) {
        if (template == null || template.isEmpty()) {
            return String.valueOf(year);
        }
        String result = template;
        result = result.replace("YYYY", String.valueOf(year));
        result = result.replace("YY", String.format("%02d", year % 100));
        result = result.replace("MM", String.format("%02d", month));
        result = result.replace("QN", "Q" + quarter);
        result = result.replace("M", String.valueOf(month));
        result = result.replace("Q", String.valueOf(quarter));
        return result;
    }
}
