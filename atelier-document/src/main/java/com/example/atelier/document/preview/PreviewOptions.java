package com.example.atelier.document.preview;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewOptions {
    /** PDF / DOCX / Markdown 是否启用 LLM 样式增强（标题/加粗/斜体） */
    @Builder.Default
    private boolean enableLlmStyle = true;
    /**
     * PDF / DOCX / Markdown 保真闭环：对比原文，由 LLM 输出 DROP/MERGE/SET 并迭代修补。
     */
    @Builder.Default
    private boolean enableLlmRefine = true;
    private String llmProfileId;
}
