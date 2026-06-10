package com.example.atelier.warning.evaluator;

final class SemanticLogSupport {

    private SemanticLogSupport() {
    }

    static String previewText(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= 40 ? trimmed : trimmed.substring(0, 40) + "...";
    }
}
