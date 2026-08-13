package com.example.atelier.document.model;

import com.example.atelier.document.preview.PreviewDocument;
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
public class CompareResult {
    private String fileNameA;
    private String fileNameB;
    @Builder.Default
    private List<TextHunk> textHunks = new ArrayList<>();
    @Builder.Default
    private List<ParagraphOp> paragraphOps = new ArrayList<>();
    @Builder.Default
    private List<StructureOp> structureOps = new ArrayList<>();
    private CompareStats stats;
    private CompareQuality quality;
    private LlmInterpretation interpretation;
    /** 用于前端左右对照的规范化全文（可能截断；P0 起与预览拼接明文一致） */
    private String plainTextA;
    private String plainTextB;
    /** A/B 最终预览 IR（含稳定 blockId，供点击 Diff 定位） */
    private PreviewDocument previewA;
    private PreviewDocument previewB;
}
