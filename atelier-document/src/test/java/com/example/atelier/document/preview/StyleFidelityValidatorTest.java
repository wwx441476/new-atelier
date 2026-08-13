package com.example.atelier.document.preview;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StyleFidelityValidatorTest {

    private final StyleFidelityValidator validator = new StyleFidelityValidator();

    @Test
    public void matchesWhenWhitespaceDiffersOnly() {
        PreviewBlock block = PreviewBlock.builder()
                .type(PreviewBlockType.PARAGRAPH)
                .runs(Arrays.asList(
                        PreviewRun.builder().text("Hello").marks(Collections.singletonList(PreviewInlineMark.BOLD)).build(),
                        PreviewRun.builder().text(" World").build()))
                .build();
        assertTrue(validator.matches("Hello   World", Collections.singletonList(block)));
    }

    @Test
    public void failsWhenTextMutated() {
        PreviewBlock block = PreviewBlock.builder()
                .type(PreviewBlockType.PARAGRAPH)
                .runs(Collections.singletonList(PreviewRun.builder().text("Hello World!").build()))
                .build();
        assertFalse(validator.matches("Hello World", Collections.singletonList(block)));
    }

    @Test
    public void matchesAcrossMultipleBlocks() {
        PreviewBlock h = PreviewBlock.builder()
                .type(PreviewBlockType.HEADING)
                .level(1)
                .runs(Collections.singletonList(PreviewRun.builder().text("Title").build()))
                .build();
        PreviewBlock p = PreviewBlock.builder()
                .type(PreviewBlockType.PARAGRAPH)
                .runs(Collections.singletonList(PreviewRun.builder().text("Body text").build()))
                .build();
        assertTrue(validator.matches("Title\nBody text", Arrays.asList(h, p)));
    }
}
