package com.example.atelier.warning.evaluator;

import lombok.Builder;
import lombok.Value;

/**
 * 语义求值选项（预览 keyword-only、生产默认全量混合等）。
 */
@Value
@Builder
public class SemanticEvaluationOptions {

    @Builder.Default
    boolean keywordOnly = false;

    public static SemanticEvaluationOptions defaults() {
        return SemanticEvaluationOptions.builder().build();
    }

    public static SemanticEvaluationOptions keywordOnly() {
        return SemanticEvaluationOptions.builder().keywordOnly(true).build();
    }
}
