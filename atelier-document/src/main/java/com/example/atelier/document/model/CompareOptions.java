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
    @Builder.Default
    private boolean enableLlm = true;
    private String llmProfileId;
}
