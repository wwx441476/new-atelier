package com.example.atelier.document.preview;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class StyleAnnotationApplierTest {

    private final StyleAnnotationApplier applier = new StyleAnnotationApplier();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void appliesTypeAndMarksWithoutChangingText() throws Exception {
        List<PreviewBlock> originals = sampleParagraphs();
        String json = "[{\"i\":0,\"type\":\"HEADING\",\"level\":1,\"marks\":[\"BOLD\"]},"
                + "{\"i\":1,\"type\":\"HEADING\",\"level\":3,\"marks\":[]}]";

        List<PreviewBlock> out = applier.apply(originals, mapper.readTree(json));

        assertNotNull(out);
        assertEquals(PreviewBlockType.HEADING, out.get(0).getType());
        assertTrue(out.get(0).getRuns().get(0).getMarks().contains(PreviewInlineMark.BOLD));
    }

    @Test
    public void acceptsIndexFieldAndWrappedObject() throws Exception {
        List<PreviewBlock> originals = sampleParagraphs();
        String json = "{\"annotations\":[{\"index\":0,\"type\":\"heading\",\"level\":1,\"bold\":true},"
                + "{\"index\":1,\"type\":\"PARAGRAPH\",\"level\":0}]}";

        List<PreviewBlock> out = applier.apply(originals, mapper.readTree(json));

        assertNotNull(out);
        assertEquals(PreviewBlockType.HEADING, out.get(0).getType());
        assertEquals(PreviewBlockType.PARAGRAPH, out.get(1).getType());
    }

    @Test
    public void fallsBackToPositionalWhenNoIndex() throws Exception {
        List<PreviewBlock> originals = sampleParagraphs();
        String json = "[{\"type\":\"HEADING\",\"level\":1,\"marks\":[\"BOLD\"]},"
                + "{\"type\":\"PARAGRAPH\",\"level\":0,\"marks\":[]}]";

        List<PreviewBlock> out = applier.apply(originals, mapper.readTree(json));

        assertNotNull(out);
        assertEquals(PreviewBlockType.HEADING, out.get(0).getType());
    }

    @Test
    public void salvagesTruncatedStreamMissingArrayHead() {
        // 真实日志形态：流式丢了开头 [{"i":0,"type":"HEAD
        String truncated = "ING\",\"level\":1,\"marks\":[\"BOLD\"]},{\"i\":1,\"type\":\"HEADING\",\"level\":3,\"marks\":[\"BOLD\"]},"
                + "{\"i\":2,\"type\":\"PARAGRAPH\",\"level\":0,\"marks\":[]}]";

        List<PreviewBlock> out = applier.applyFromRaw(sampleParagraphs(), truncated);

        assertNotNull(out);
        // i:0 残缺被跳过，i:1 / i:2 仍可应用
        assertEquals(PreviewBlockType.HEADING, out.get(1).getType());
        assertEquals(3, out.get(1).getLevel());
        assertEquals(PreviewBlockType.PARAGRAPH, out.get(2).getType());
    }

    @Test
    public void extractAnnotationJsonSkipsMarksEmptyArray() {
        String raw = "[{\"i\":0,\"type\":\"PARAGRAPH\",\"level\":0,\"marks\":[]},{\"i\":1,\"type\":\"HEADING\",\"level\":1,\"marks\":[]}]";
        String extracted = StyleAnnotationApplier.extractAnnotationJson(raw);
        assertTrue(extracted.startsWith("[{"));
        assertTrue(extracted.contains("\"i\":0"));
    }

    private static List<PreviewBlock> sampleParagraphs() {
        return Arrays.asList(
                PreviewBlock.builder().type(PreviewBlockType.PARAGRAPH).text("标题")
                        .runs(Collections.singletonList(PreviewRun.builder().text("标题").build())).build(),
                PreviewBlock.builder().type(PreviewBlockType.PARAGRAPH).text("第一条")
                        .runs(Collections.singletonList(PreviewRun.builder().text("第一条").build())).build(),
                PreviewBlock.builder().type(PreviewBlockType.PARAGRAPH).text("正文")
                        .runs(Collections.singletonList(PreviewRun.builder().text("正文").build())).build());
    }
}
