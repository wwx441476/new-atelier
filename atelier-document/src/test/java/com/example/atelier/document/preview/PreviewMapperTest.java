package com.example.atelier.document.preview;

import com.example.atelier.document.model.BlockMeta;
import com.example.atelier.document.model.BlockType;
import com.example.atelier.document.model.DocumentBlock;
import com.example.atelier.document.model.DocumentModel;
import com.example.atelier.document.model.TableData;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PreviewMapperTest {

    private final PreviewMapper mapper = new PreviewMapper();

    @Test
    public void mapsFlowBlocksForWordLikeDocument() {
        DocumentModel model = DocumentModel.builder()
                .fileName("spec.docx")
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .blocks(Arrays.asList(
                        DocumentBlock.builder().id("h1").type(BlockType.HEADING).level(1).text("Title").build(),
                        DocumentBlock.builder().id("p1").type(BlockType.PARAGRAPH).text("Intro").build(),
                        DocumentBlock.builder().id("t1").type(BlockType.TABLE)
                                .table(TableData.builder()
                                        .rows(Collections.singletonList(Arrays.asList("a", "b")))
                                        .build())
                                .build()))
                .warnings(Collections.singletonList("note"))
                .build();

        PreviewDocument preview = mapper.toPreview(model);

        assertEquals("spec.docx", preview.getFileName());
        assertEquals("docx", preview.getSourceType());
        assertEquals("flow", preview.getLayoutMode());
        assertEquals(1, preview.getWarnings().size());
        assertEquals(3, preview.getBlocks().size());
        assertEquals(PreviewBlockType.HEADING, preview.getBlocks().get(0).getType());
        assertEquals(1, preview.getBlocks().get(0).getLevel());
        assertEquals(PreviewBlockType.PARAGRAPH, preview.getBlocks().get(1).getType());
        assertEquals(PreviewBlockType.TABLE, preview.getBlocks().get(2).getType());
        assertFalse(preview.isOcrUsed());
    }

    @Test
    public void insertsSectionBeforeEachSlide() {
        DocumentModel model = DocumentModel.builder()
                .fileName("deck.pptx")
                .mimeType("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                .blocks(Arrays.asList(
                        DocumentBlock.builder().id("s1").type(BlockType.SLIDE).text("Hello")
                                .meta(BlockMeta.builder().slideIndex(1).page(1).build()).build(),
                        DocumentBlock.builder().id("s2").type(BlockType.SLIDE).text("World")
                                .meta(BlockMeta.builder().slideIndex(2).page(2).build()).build()))
                .build();

        PreviewDocument preview = mapper.toPreview(model);
        List<PreviewBlock> blocks = preview.getBlocks();

        assertEquals("pptx", preview.getSourceType());
        assertEquals(4, blocks.size());
        assertEquals(PreviewBlockType.SECTION, blocks.get(0).getType());
        assertEquals("幻灯片 1", blocks.get(0).getText());
        assertEquals(PreviewBlockType.SLIDE, blocks.get(1).getType());
        assertEquals("Hello", blocks.get(1).getText());
        assertEquals(PreviewBlockType.SECTION, blocks.get(2).getType());
        assertEquals("幻灯片 2", blocks.get(2).getText());
        assertEquals(PreviewBlockType.SLIDE, blocks.get(3).getType());
    }

    @Test
    public void insertsSectionBeforeEachSheet() {
        DocumentModel model = DocumentModel.builder()
                .fileName("data.xlsx")
                .mimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .blocks(Arrays.asList(
                        DocumentBlock.builder().id("sh1").type(BlockType.SHEET).text("[Sheet: Sales]")
                                .meta(BlockMeta.builder().sheet("Sales").build())
                                .table(TableData.builder().sheetName("Sales")
                                        .rows(Collections.singletonList(Collections.singletonList("1")))
                                        .build())
                                .build(),
                        DocumentBlock.builder().id("sh2").type(BlockType.SHEET).text("[Sheet: Cost]")
                                .meta(BlockMeta.builder().sheet("Cost").build())
                                .table(TableData.builder().sheetName("Cost")
                                        .rows(Collections.singletonList(Collections.singletonList("2")))
                                        .build())
                                .build()))
                .build();

        PreviewDocument preview = mapper.toPreview(model);
        List<PreviewBlock> blocks = preview.getBlocks();

        assertEquals("xlsx", preview.getSourceType());
        assertEquals(4, blocks.size());
        assertEquals(PreviewBlockType.SECTION, blocks.get(0).getType());
        assertEquals("Sales", blocks.get(0).getText());
        assertEquals(PreviewBlockType.SHEET, blocks.get(1).getType());
        assertEquals(PreviewBlockType.SECTION, blocks.get(2).getType());
        assertEquals("Cost", blocks.get(2).getText());
        assertTrue(blocks.get(3).getTable() != null);
    }

    @Test
    public void flattensNestedChildrenInOrder() {
        DocumentBlock child = DocumentBlock.builder()
                .id("c1").type(BlockType.PARAGRAPH).text("child").build();
        DocumentBlock parent = DocumentBlock.builder()
                .id("p1").type(BlockType.PARAGRAPH).text("parent")
                .children(Collections.singletonList(child))
                .build();
        DocumentModel model = DocumentModel.builder()
                .fileName("nested.txt")
                .blocks(Collections.singletonList(parent))
                .build();

        PreviewDocument preview = mapper.toPreview(model);
        assertEquals(2, preview.getBlocks().size());
        assertEquals("parent", preview.getBlocks().get(0).getText());
        assertEquals("child", preview.getBlocks().get(1).getText());
        assertEquals("text", preview.getSourceType());
    }
}
