package com.example.atelier.document.llm;

import com.example.atelier.document.preview.PreviewBlock;
import com.example.atelier.document.preview.PreviewBlockType;
import com.example.atelier.document.preview.PreviewDocument;
import com.example.atelier.document.preview.PreviewRun;
import com.example.atelier.document.model.BlockMeta;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LlmPreviewFormatsTest {

    @Test
    public void supportsPdfDocxMarkdownOnly() {
        assertTrue(LlmPreviewFormats.supportsLlmEnhance("pdf"));
        assertTrue(LlmPreviewFormats.supportsLlmEnhance("docx"));
        assertTrue(LlmPreviewFormats.supportsLlmEnhance("markdown"));
        assertFalse(LlmPreviewFormats.supportsLlmEnhance("xlsx"));
        assertFalse(LlmPreviewFormats.supportsLlmEnhance("pptx"));
        assertFalse(LlmPreviewFormats.supportsLlmEnhance("text"));
    }

    @Test
    public void docxChunksByBlockCountNotSingleBucket() {
        List<PreviewBlock> blocks = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            blocks.add(PreviewBlock.builder()
                    .type(PreviewBlockType.PARAGRAPH)
                    .text("p" + i)
                    .runs(Collections.singletonList(PreviewRun.builder().text("p" + i).build()))
                    .build());
        }
        PreviewDocument doc = PreviewDocument.builder().sourceType("docx").blocks(blocks).build();
        List<List<PreviewBlock>> chunks = LlmStyleEnrichService.toStyleChunks(doc);
        assertEquals(2, chunks.size());
        assertEquals(80, chunks.get(0).size());
        assertEquals(10, chunks.get(1).size());
    }

    @Test
    public void pdfChunksByPage() {
        List<PreviewBlock> blocks = new ArrayList<>();
        blocks.add(blockOnPage("a", 1));
        blocks.add(blockOnPage("b", 1));
        blocks.add(blockOnPage("c", 2));
        PreviewDocument doc = PreviewDocument.builder().sourceType("pdf").blocks(blocks).build();
        List<List<PreviewBlock>> chunks = LlmStyleEnrichService.toStyleChunks(doc);
        assertEquals(2, chunks.size());
        assertEquals(2, chunks.get(0).size());
        assertEquals(1, chunks.get(1).size());
    }

    @Test
    public void mergePreservesTableBetweenParagraphs() {
        PreviewBlock p1 = para("intro");
        PreviewBlock table = PreviewBlock.builder()
                .type(PreviewBlockType.TABLE)
                .text("[table 2 rows]")
                .table(com.example.atelier.document.model.TableData.builder()
                        .rows(java.util.Arrays.asList(
                                java.util.Arrays.asList("痛点", "具体表现"),
                                java.util.Arrays.asList("规则配置门槛高", "周期长")))
                        .build())
                .build();
        PreviewBlock p2 = para("next");
        PreviewBlock styled1 = PreviewBlock.builder()
                .type(PreviewBlockType.HEADING)
                .level(1)
                .text("intro")
                .runs(Collections.singletonList(PreviewRun.builder().text("intro").build()))
                .build();
        PreviewBlock styled2 = para("next");

        List<PreviewBlock> merged = LlmStyleEnrichService.mergePreservingNonStyleable(
                java.util.Arrays.asList(p1, table, p2),
                java.util.Arrays.asList(styled1, styled2));

        assertEquals(3, merged.size());
        assertEquals(PreviewBlockType.HEADING, merged.get(0).getType());
        assertEquals(PreviewBlockType.TABLE, merged.get(1).getType());
        assertEquals(2, merged.get(1).getTable().getRows().size());
        assertEquals("next", merged.get(2).getText());
    }

    private static PreviewBlock para(String text) {
        return PreviewBlock.builder()
                .type(PreviewBlockType.PARAGRAPH)
                .text(text)
                .runs(Collections.singletonList(PreviewRun.builder().text(text).build()))
                .build();
    }

    private static PreviewBlock blockOnPage(String text, int page) {
        return PreviewBlock.builder()
                .type(PreviewBlockType.PARAGRAPH)
                .text(text)
                .runs(Collections.singletonList(PreviewRun.builder().text(text).build()))
                .meta(BlockMeta.builder().page(page).build())
                .build();
    }
}
