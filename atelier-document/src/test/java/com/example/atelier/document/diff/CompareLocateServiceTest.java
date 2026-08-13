package com.example.atelier.document.diff;

import com.example.atelier.document.model.CompareOptions;
import com.example.atelier.document.model.CompareResult;
import com.example.atelier.document.model.DiffOpType;
import com.example.atelier.document.model.ParagraphOp;
import com.example.atelier.document.model.TextHunk;
import com.example.atelier.document.preview.PreviewBlock;
import com.example.atelier.document.preview.PreviewBlockType;
import com.example.atelier.document.preview.PreviewDocument;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompareLocateServiceTest {

    private final CompareLocateService service = new CompareLocateService(new TextDiffComputer());

    @Test
    public void attachesBlockIdsFromPreviewPlainText() {
        PreviewDocument a = PreviewDocument.builder()
                .blocks(Collections.singletonList(
                        PreviewBlock.builder().id("pa").type(PreviewBlockType.PARAGRAPH)
                                .text("hello world").build()))
                .build();
        PreviewDocument b = PreviewDocument.builder()
                .blocks(Collections.singletonList(
                        PreviewBlock.builder().id("pb").type(PreviewBlockType.PARAGRAPH)
                                .text("hello moon").build()))
                .build();
        CompareResult result = CompareResult.builder()
                .paragraphOps(Collections.singletonList(
                        ParagraphOp.builder()
                                .type(DiffOpType.MODIFIED)
                                .oldText("hello world")
                                .newText("hello moon")
                                .build()))
                .build();

        service.attach(result, a, b, CompareOptions.builder().ignoreWhitespace(true).build());

        assertEquals(a, result.getPreviewA());
        assertEquals(b, result.getPreviewB());
        assertFalse(result.getTextHunks().isEmpty());
        TextHunk hunk = result.getTextHunks().get(0);
        assertTrue(hunk.getBlockIdsA().contains("pa"));
        assertTrue(hunk.getBlockIdsB().contains("pb"));
        assertEquals(Collections.singletonList("pa"), result.getParagraphOps().get(0).getBlockIdsA());
        assertEquals(Collections.singletonList("pb"), result.getParagraphOps().get(0).getBlockIdsB());
    }
}
