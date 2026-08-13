package com.example.atelier.document.preview;

public final class PreviewTextNormalize {

    private PreviewTextNormalize() {
    }

    /** 去除全部空白，用于保真校验与哈希。 */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replaceAll("\\s+", "");
    }

    public static String joinRuns(java.util.List<PreviewRun> runs) {
        if (runs == null || runs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (PreviewRun run : runs) {
            if (run != null && run.getText() != null) {
                sb.append(run.getText());
            }
        }
        return sb.toString();
    }

    public static String blockPlainText(PreviewBlock block) {
        if (block == null) {
            return "";
        }
        if (block.getRuns() != null && !block.getRuns().isEmpty()) {
            return joinRuns(block.getRuns());
        }
        return block.getText() == null ? "" : block.getText();
    }
}
