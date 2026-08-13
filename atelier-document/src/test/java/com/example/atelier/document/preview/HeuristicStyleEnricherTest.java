package com.example.atelier.document.preview;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class HeuristicStyleEnricherTest {

    private final HeuristicStyleEnricher enricher = new HeuristicStyleEnricher();

    @Test
    public void detectsTitleChapterAndArticle() {
        List<PreviewBlock> in = Arrays.asList(
                para("资产净值预警管理办法"),
                para("第一章 总则"),
                para("第一条 为加强管理，制定本办法。"),
                para("普通正文内容继续。"));

        List<PreviewBlock> out = enricher.enrichPage(in);

        assertEquals(PreviewBlockType.HEADING, out.get(0).getType());
        assertEquals(1, out.get(0).getLevel());
        assertEquals(PreviewBlockType.HEADING, out.get(1).getType());
        assertEquals(2, out.get(1).getLevel());
        assertEquals(PreviewBlockType.HEADING, out.get(2).getType());
        assertEquals(3, out.get(2).getLevel());
        assertEquals(PreviewBlockType.PARAGRAPH, out.get(3).getType());
        assertEquals("第一章 总则", out.get(1).getText());
    }

    private static PreviewBlock para(String text) {
        return PreviewBlock.builder()
                .type(PreviewBlockType.PARAGRAPH)
                .text(text)
                .runs(Collections.singletonList(PreviewRun.builder().text(text).build()))
                .build();
    }
}
