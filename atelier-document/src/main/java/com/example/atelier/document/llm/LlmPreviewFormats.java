package com.example.atelier.document.llm;

import java.util.Locale;

/**
 * 支持 LLM 样式增强与保真闭环的预览源类型。
 */
public final class LlmPreviewFormats {

    private LlmPreviewFormats() {
    }

    public static boolean supportsLlmEnhance(String sourceType) {
        if (sourceType == null || sourceType.isEmpty()) {
            return false;
        }
        String t = sourceType.trim().toLowerCase(Locale.ROOT);
        return "pdf".equals(t) || "docx".equals(t) || "markdown".equals(t);
    }

    public static boolean isPdf(String sourceType) {
        return sourceType != null && "pdf".equalsIgnoreCase(sourceType.trim());
    }
}
