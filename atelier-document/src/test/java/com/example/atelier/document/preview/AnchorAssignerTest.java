package com.example.atelier.document.preview;

import com.example.atelier.document.model.BlockMeta;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AnchorAssignerTest {

    private final AnchorAssigner assigner = new AnchorAssigner();

    @Test
    public void assignsStableIdsAndOccurrences() {
        PreviewBlock a = PreviewBlock.builder()
                .type(PreviewBlockType.PARAGRAPH)
                .text("Same")
                .runs(Collections.singletonList(PreviewRun.builder().text("Same").build()))
                .meta(BlockMeta.builder().page(1).build())
                .build();
        PreviewBlock b = PreviewBlock.builder()
                .type(PreviewBlockType.PARAGRAPH)
                .text("Same")
                .runs(Collections.singletonList(PreviewRun.builder().text("Same").build()))
                .meta(BlockMeta.builder().page(1).build())
                .build();
        PreviewDocument doc = PreviewDocument.builder()
                .sourceType("pdf")
                .blocks(Arrays.asList(a, b))
                .build();

        assigner.assign(doc);

        assertNotNull(a.getId());
        assertNotNull(b.getId());
        assertNotEquals(a.getId(), b.getId());
        assertEquals(1, a.getAnchor().getOccurrence());
        assertEquals(2, b.getAnchor().getOccurrence());
        assertEquals(a.getAnchor().getTextHash(), b.getAnchor().getTextHash());
        assertTrue(a.getId().startsWith("p1-"));
        assertTrue(a.getId().endsWith("-1"));
        assertTrue(b.getId().endsWith("-2"));
        assertEquals(Integer.valueOf(0), a.getAnchor().getSourceStart());
        assertTrue(a.getAnchor().getSourceEnd() > 0);
        assertEquals(a.getAnchor().getSourceEnd(), b.getAnchor().getSourceStart());
    }

    @Test
    public void skipsSectionBlocks() {
        PreviewBlock section = PreviewBlock.builder()
                .id("section-1")
                .type(PreviewBlockType.SECTION)
                .text("幻灯片 1")
                .build();
        PreviewBlock para = PreviewBlock.builder()
                .type(PreviewBlockType.PARAGRAPH)
                .text("Hi")
                .meta(BlockMeta.builder().page(2).build())
                .build();
        PreviewDocument doc = PreviewDocument.builder()
                .blocks(Arrays.asList(section, para))
                .build();

        assigner.assign(doc);

        assertEquals("section-1", section.getId());
        assertNotNull(para.getAnchor());
        assertEquals(Integer.valueOf(2), para.getAnchor().getPage());
        assertNotNull(para.getRuns());
        assertEquals(1, para.getRuns().size());
    }
}
