package com.example.atelier.document.preview;

import java.util.List;

/**
 * 校验 LLM 输出 runs 拼接后（空白归一化）是否与原文一致。
 */
public class StyleFidelityValidator {

    public boolean matches(String sourceText, List<PreviewBlock> llmBlocks) {
        String expected = PreviewTextNormalize.normalize(sourceText);
        if (expected.isEmpty()) {
            return llmBlocks == null || llmBlocks.isEmpty()
                    || PreviewTextNormalize.normalize(plainFromBlocks(llmBlocks)).isEmpty();
        }
        String actual = PreviewTextNormalize.normalize(plainFromBlocks(llmBlocks));
        return expected.equals(actual);
    }

    private static String plainFromBlocks(List<PreviewBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (PreviewBlock block : blocks) {
            sb.append(PreviewTextNormalize.blockPlainText(block));
        }
        return sb.toString();
    }
}
