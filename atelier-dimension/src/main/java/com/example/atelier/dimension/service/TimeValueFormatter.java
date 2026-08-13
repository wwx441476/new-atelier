package com.example.atelier.dimension.service;

/**
 * 时间维度值格式模板渲染。
 * <p>
 * 支持占位符（按最长匹配从左到右扫描；连续 Y 时取末尾 4 位为年，以支持 {@code FYYYYY} → {@code FY2024}）：
 * {@code YYYY}/{@code YY}/{@code MM}/{@code M}/{@code QN}/{@code Q}。
 */
final class TimeValueFormatter {

    private TimeValueFormatter() {
    }

    static String format(String template, int year, int month, int quarter) {
        if (template == null || template.isEmpty()) {
            return String.valueOf(year);
        }
        StringBuilder sb = new StringBuilder(template.length() + 8);
        int i = 0;
        int n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            if (c == 'Y') {
                int start = i;
                while (i < n && template.charAt(i) == 'Y') {
                    i++;
                }
                int count = i - start;
                if (count >= 4) {
                    for (int k = 0; k < count - 4; k++) {
                        sb.append('Y');
                    }
                    sb.append(year);
                } else if (count >= 2) {
                    sb.append(String.format("%02d", year % 100));
                    for (int k = 2; k < count; k++) {
                        sb.append('Y');
                    }
                } else {
                    sb.append('Y');
                }
                continue;
            }
            if (c == 'M' && i + 1 < n && template.charAt(i + 1) == 'M') {
                sb.append(String.format("%02d", month));
                i += 2;
                continue;
            }
            if (c == 'Q' && i + 1 < n && template.charAt(i + 1) == 'N') {
                sb.append('Q').append(quarter);
                i += 2;
                continue;
            }
            if (c == 'M') {
                sb.append(month);
                i++;
                continue;
            }
            if (c == 'Q') {
                sb.append(quarter);
                i++;
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }
}
