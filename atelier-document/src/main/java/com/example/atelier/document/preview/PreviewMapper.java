package com.example.atelier.document.preview;

import com.example.atelier.document.model.BlockMeta;
import com.example.atelier.document.model.BlockType;
import com.example.atelier.document.model.DocumentBlock;
import com.example.atelier.document.model.DocumentModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PreviewMapper {

    public PreviewDocument toPreview(DocumentModel model) {
        if (model == null) {
            return PreviewDocument.builder()
                    .layoutMode("flow")
                    .styleMode("canonical")
                    .blocks(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .build();
        }
        List<String> warnings = model.getWarnings() == null
                ? new ArrayList<>()
                : new ArrayList<>(model.getWarnings());
        List<PreviewBlock> blocks = new ArrayList<>();
        AtomicInteger sectionSeq = new AtomicInteger(0);
        flatten(model.getBlocks(), blocks, sectionSeq);

        return PreviewDocument.builder()
                .fileName(model.getFileName())
                .sourceType(resolveSourceType(model.getFileName(), model.getMimeType()))
                .layoutMode("flow")
                .styleMode("canonical")
                .ocrUsed(model.isOcrUsed())
                .llmStyleUsed(false)
                .warnings(warnings)
                .blocks(blocks)
                .build();
    }

    private void flatten(List<DocumentBlock> source, List<PreviewBlock> out, AtomicInteger sectionSeq) {
        if (source == null) {
            return;
        }
        for (DocumentBlock block : source) {
            if (block == null) {
                continue;
            }
            if (block.getType() == BlockType.SLIDE) {
                int slideNo;
                if (block.getMeta() != null && block.getMeta().getSlideIndex() != null) {
                    slideNo = block.getMeta().getSlideIndex();
                    sectionSeq.set(Math.max(sectionSeq.get(), slideNo));
                } else {
                    slideNo = sectionSeq.incrementAndGet();
                }
                String sectionText = "幻灯片 " + slideNo;
                out.add(PreviewBlock.builder()
                        .id("section-slide-" + slideNo)
                        .type(PreviewBlockType.SECTION)
                        .level(1)
                        .text(sectionText)
                        .runs(singleRun(sectionText))
                        .meta(BlockMeta.builder().slideIndex(slideNo).page(slideNo).build())
                        .build());
            } else if (block.getType() == BlockType.SHEET) {
                String sheetName = resolveSheetName(block);
                out.add(PreviewBlock.builder()
                        .id("section-sheet-" + sectionSeq.incrementAndGet())
                        .type(PreviewBlockType.SECTION)
                        .level(1)
                        .text(sheetName)
                        .runs(singleRun(sheetName))
                        .meta(BlockMeta.builder().sheet(sheetName).build())
                        .build());
            }
            out.add(copyBlock(block));
            flatten(block.getChildren(), out, sectionSeq);
        }
    }

    private PreviewBlock copyBlock(DocumentBlock block) {
        String text = block.getText() == null ? "" : block.getText();
        return PreviewBlock.builder()
                .id(block.getId())
                .type(mapType(block.getType()))
                .level(block.getLevel())
                .text(text)
                .runs(singleRun(text))
                .table(block.getTable())
                .imageDataUrl(block.getImageDataUrl())
                .meta(block.getMeta())
                .build();
    }

    private static List<PreviewRun> singleRun(String text) {
        List<PreviewRun> runs = new ArrayList<>();
        runs.add(PreviewRun.builder().text(text == null ? "" : text).marks(new ArrayList<>()).build());
        return runs;
    }

    private static PreviewBlockType mapType(BlockType type) {
        if (type == null) {
            return PreviewBlockType.PARAGRAPH;
        }
        switch (type) {
            case HEADING:
                return PreviewBlockType.HEADING;
            case LIST_ITEM:
                return PreviewBlockType.LIST_ITEM;
            case TABLE:
                return PreviewBlockType.TABLE;
            case IMAGE:
                return PreviewBlockType.IMAGE;
            case IMAGE_CAPTION:
                return PreviewBlockType.IMAGE_CAPTION;
            case CODE:
                return PreviewBlockType.CODE;
            case SLIDE:
                return PreviewBlockType.SLIDE;
            case SHEET:
                return PreviewBlockType.SHEET;
            case PARAGRAPH:
            default:
                return PreviewBlockType.PARAGRAPH;
        }
    }

    private static String resolveSheetName(DocumentBlock block) {
        if (block.getTable() != null && block.getTable().getSheetName() != null
                && !block.getTable().getSheetName().trim().isEmpty()) {
            return block.getTable().getSheetName().trim();
        }
        if (block.getMeta() != null && block.getMeta().getSheet() != null
                && !block.getMeta().getSheet().trim().isEmpty()) {
            return block.getMeta().getSheet().trim();
        }
        if (block.getText() != null && block.getText().startsWith("[Sheet: ") && block.getText().endsWith("]")) {
            return block.getText().substring(8, block.getText().length() - 1);
        }
        return "工作表";
    }

    static String resolveSourceType(String fileName, String mimeType) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (name.endsWith(".docx") || mime.contains("wordprocessingml")) {
            return "docx";
        }
        if (name.endsWith(".xlsx") || mime.contains("spreadsheetml")) {
            return "xlsx";
        }
        if (name.endsWith(".pptx") || mime.contains("presentationml")) {
            return "pptx";
        }
        if (name.endsWith(".pdf") || mime.contains("application/pdf")) {
            return "pdf";
        }
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp")
                || mime.startsWith("image/")) {
            return "image";
        }
        if (name.endsWith(".md") || name.endsWith(".markdown")) {
            return "markdown";
        }
        if (name.endsWith(".json")) {
            return "json";
        }
        if (name.endsWith(".csv")) {
            return "csv";
        }
        if (name.endsWith(".xml")) {
            return "xml";
        }
        if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            return "yaml";
        }
        if (name.endsWith(".sql") || name.endsWith(".java") || name.endsWith(".ts")
                || name.endsWith(".tsx") || name.endsWith(".js") || name.endsWith(".py")
                || name.endsWith(".log")) {
            return "code";
        }
        if (name.endsWith(".txt") || mime.startsWith("text/")) {
            return "text";
        }
        return "unknown";
    }
}
