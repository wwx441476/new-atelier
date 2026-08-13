package com.example.atelier.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompareOptions {
    @Builder.Default
    private boolean ignoreWhitespace = true;
    /** Excel 行对齐：true 时用首列作为 key */
    @Builder.Default
    private boolean excelKeyColumn = false;
    /** AI 解读（基于 Diff 摘要） */
    @Builder.Default
    private boolean enableLlm = true;
    /** 对比侧 A/B 预览：LLM 样式增强（默认开） */
    @Builder.Default
    private boolean enableLlmStyle = true;
    /** 对比侧 A/B 预览：保真闭环（默认开） */
    @Builder.Default
    private boolean enableLlmRefine = true;
    private String llmProfileId;
}
