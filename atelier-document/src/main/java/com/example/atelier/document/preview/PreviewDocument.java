package com.example.atelier.document.preview;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewDocument {
    private String fileName;
    private String sourceType;
    /** 固定为 flow：流式结构阅读，不分页 */
    @Builder.Default
    private String layoutMode = "flow";
    /** 规范皮肤，非原文字号/字体 */
    @Builder.Default
    private String styleMode = "canonical";
    private boolean ocrUsed;
    private boolean llmStyleUsed;
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    @Builder.Default
    private List<PreviewBlock> blocks = new ArrayList<>();
}
