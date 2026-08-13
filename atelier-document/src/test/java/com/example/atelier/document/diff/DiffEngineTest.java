package com.example.atelier.document.diff;

import com.example.atelier.document.model.BlockType;
import com.example.atelier.document.model.CompareOptions;
import com.example.atelier.document.model.CompareResult;
import com.example.atelier.document.model.DiffOpType;
import com.example.atelier.document.model.DocumentBlock;
import com.example.atelier.document.model.DocumentModel;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiffEngineTest {

    private final DiffEngine engine = new DiffEngine(
            new TextDiffComputer(),
            new ParagraphDiffComputer(),
            new StructureDiffComputer());

    @Test
    public void comparesTextParagraphAndStructure() {
        DocumentModel a = DocumentModel.builder()
                .fileName("a.txt")
                .blocks(Arrays.asList(
                        DocumentBlock.builder().id("1").type(BlockType.PARAGRAPH)
                                .text("The quarterly revenue report for Q1").build(),
                        DocumentBlock.builder().id("2").type(BlockType.PARAGRAPH).text("keep me").build()))
                .build();
        DocumentModel b = DocumentModel.builder()
                .fileName("b.txt")
                .blocks(Arrays.asList(
                        DocumentBlock.builder().id("1").type(BlockType.PARAGRAPH)
                                .text("The quarterly revenue report for Q2 with notes").build(),
                        DocumentBlock.builder().id("2").type(BlockType.PARAGRAPH).text("keep me").build(),
                        DocumentBlock.builder().id("3").type(BlockType.PARAGRAPH).text("new para").build()))
                .build();

        CompareResult result = engine.compare(a, b, CompareOptions.builder().ignoreWhitespace(true).build());
        assertFalse(result.getTextHunks().isEmpty());
        assertTrue(result.getParagraphOps().stream().anyMatch(op -> op.getType() == DiffOpType.ADDED));
        assertTrue(result.getParagraphOps().stream().anyMatch(op -> op.getType() == DiffOpType.MODIFIED));
        assertFalse(result.getStructureOps().isEmpty());
        assertEquals("a.txt", result.getFileNameA());
        assertTrue(result.getStats().getAdded() >= 1);
    }

    @Test
    public void identicalDocumentsHaveNoParagraphChanges() {
        DocumentModel a = DocumentModel.builder()
                .fileName("a.txt")
                .blocks(Collections.singletonList(
                        DocumentBlock.builder().id("1").type(BlockType.PARAGRAPH).text("same").build()))
                .build();
        DocumentModel b = DocumentModel.builder()
                .fileName("b.txt")
                .blocks(Collections.singletonList(
                        DocumentBlock.builder().id("1").type(BlockType.PARAGRAPH).text("same").build()))
                .build();
        CompareResult result = engine.compare(a, b, CompareOptions.builder().build());
        assertTrue(result.getParagraphOps().isEmpty());
        assertTrue(result.getTextHunks().isEmpty());
    }
}
