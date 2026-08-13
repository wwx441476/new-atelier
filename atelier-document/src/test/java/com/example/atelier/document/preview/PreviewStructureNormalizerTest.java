package com.example.atelier.document.preview;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class PreviewStructureNormalizerTest {

    private final PreviewStructureNormalizer normalizer = new PreviewStructureNormalizer();

    @Test
    public void dropsAdjacentDuplicateTitlePreferringHeading() {
        PreviewBlock plain = para("资产净值预警管理办法");
        PreviewBlock heading = PreviewBlock.builder()
                .type(PreviewBlockType.HEADING)
                .level(1)
                .text("资产净值预警管理办法")
                .runs(Collections.singletonList(PreviewRun.builder()
                        .text("资产净值预警管理办法")
                        .marks(Collections.singletonList(PreviewInlineMark.BOLD))
                        .build()))
                .build();
        PreviewDocument doc = PreviewDocument.builder()
                .blocks(Arrays.asList(plain, heading, para("第一章 总则")))
                .build();

        normalizer.normalize(doc);

        assertEquals(2, doc.getBlocks().size());
        assertEquals(PreviewBlockType.HEADING, doc.getBlocks().get(0).getType());
        assertEquals("资产净值预警管理办法", doc.getBlocks().get(0).getText());
        assertEquals("第一章 总则", doc.getBlocks().get(1).getText());
    }

    private static PreviewBlock para(String text) {
        return PreviewBlock.builder()
                .type(PreviewBlockType.PARAGRAPH)
                .text(text)
                .runs(Collections.singletonList(PreviewRun.builder().text(text).build()))
                .build();
    }
}
