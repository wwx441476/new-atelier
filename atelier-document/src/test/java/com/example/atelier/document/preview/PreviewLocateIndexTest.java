package com.example.atelier.document.preview;

import com.example.atelier.document.model.TableData;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PreviewLocateIndexTest {

    @Test
    public void mapsLineRangeToBlockIds() {
        PreviewDocument doc = PreviewDocument.builder()
                .blocks(Arrays.asList(
                        PreviewBlock.builder().id("a1").type(PreviewBlockType.PARAGRAPH)
                                .text("line one").build(),
                        PreviewBlock.builder().id("a2").type(PreviewBlockType.PARAGRAPH)
                                .text("line two\nline three").build()))
                .build();
        PreviewLocateIndex index = PreviewLocateIndex.from(doc);
        assertEquals(3, index.getLines().size());
        assertEquals(Collections.singletonList("a1"), index.blockIdsForLineRange(0, 1));
        assertEquals(Collections.singletonList("a2"), index.blockIdsForLineRange(1, 2));
        assertEquals(Arrays.asList("a1", "a2"), index.blockIdsForLineRange(0, 2));
    }

    @Test
    public void findsBlockBySnippet() {
        PreviewDocument doc = PreviewDocument.builder()
                .blocks(Collections.singletonList(
                        PreviewBlock.builder().id("x").type(PreviewBlockType.HEADING)
                                .text("Quarterly Report").build()))
                .build();
        PreviewLocateIndex index = PreviewLocateIndex.from(doc);
        List<String> ids = index.blockIdsForSnippet("Quarterly Report");
        assertEquals(Collections.singletonList("x"), ids);
        assertTrue(index.blockIdsForSnippet("missing").isEmpty());
    }

    @Test
    public void findsTableRowJoinedByPipeAgainstTabFlattenedPreview() {
        PreviewDocument doc = PreviewDocument.builder()
                .blocks(Collections.singletonList(
                        PreviewBlock.builder().id("tbl")
                                .type(PreviewBlockType.TABLE)
                                .table(TableData.builder()
                                        .rows(Arrays.asList(
                                                Arrays.asList("指标", "传统方式"),
                                                Arrays.asList("数据库", "手工核对")))
                                        .build())
                                .build()))
                .build();
        PreviewLocateIndex index = PreviewLocateIndex.from(doc);
        List<String> ids = index.blockIdsForSnippet("数据库 | 手工核对");
        assertEquals(Collections.singletonList("tbl"), ids);
    }
}
