package com.example.atelier.document.llm;

import com.example.atelier.document.preview.PreviewBlock;
import com.example.atelier.document.preview.PreviewBlockType;
import com.example.atelier.document.preview.PreviewDocument;
import com.example.atelier.document.preview.PreviewRun;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LlmPreviewRefineServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void applyDropAndMergeAndSet() throws Exception {
        PreviewDocument doc = PreviewDocument.builder()
                .blocks(Arrays.asList(
                        para("标题"),
                        para("标题"),
                        para("上半"),
                        para("下半"),
                        para("第三条")))
                .build();
        List<JsonNode> ops = Arrays.asList(
                mapper.readTree("{\"op\":\"DROP\",\"i\":1}"),
                mapper.readTree("{\"op\":\"MERGE\",\"i\":2}"),
                mapper.readTree("{\"op\":\"SET\",\"i\":0,\"type\":\"HEADING\",\"level\":1,\"marks\":[\"BOLD\"]}")
        );
        // indices relative to original; applyOps sorts descending
        // DROP 1, MERGE 2 (上半+下半), SET 0 — after DESC: SET0, MERGE2, DROP1
        // Wait: after MERGE2 first (desc): blocks become [标题,标题,上半下半,第三条] size 4? 
        // Original: 0标题 1标题 2上半 3下半 4第三条
        // Desc order: SET0, MERGE2, DROP1
        // SET0: heading
        // MERGE2: merge 上半+下半 -> [标题H, 标题, 上半下半, 第三条]
        // DROP1: drop second 标题 -> [标题H, 上半下半, 第三条]
        
        int n = LlmPreviewRefineService.applyOps(doc, ops);
        assertEquals(3, n);
        assertEquals(3, doc.getBlocks().size());
        assertEquals(PreviewBlockType.HEADING, doc.getBlocks().get(0).getType());
        assertEquals("上半下半", doc.getBlocks().get(1).getText());
        assertEquals("第三条", doc.getBlocks().get(2).getText());
    }

    @Test
    public void parseOpsFromFencedJson() {
        String raw = "```json\n[{\"op\":\"DROP\",\"i\":0}]\n```";
        List<JsonNode> ops = LlmPreviewRefineService.parseOps(raw);
        assertEquals(1, ops.size());
        assertEquals("DROP", ops.get(0).path("op").asText());
    }

    @Test
    public void buildSourceDigestTruncates() {
        assertTrue(LlmPreviewRefineService.buildUserPrompt(1,
                Collections.singletonList(para("hi")), "source").contains("当前预览块"));
        assertTrue(LlmPreviewRefineService.buildUserPrompt(1, "docx",
                Collections.singletonList(para("hi")), "source").contains("格式: docx"));
    }

    @Test
    public void applyOpsDoesNotDropTable() throws Exception {
        PreviewBlock table = PreviewBlock.builder()
                .type(PreviewBlockType.TABLE)
                .text("[table]")
                .table(com.example.atelier.document.model.TableData.builder()
                        .rows(Collections.singletonList(Collections.singletonList("a")))
                        .build())
                .build();
        PreviewDocument doc = PreviewDocument.builder()
                .blocks(Arrays.asList(para("x"), table, para("y")))
                .build();
        int n = LlmPreviewRefineService.applyOps(doc, Collections.singletonList(
                mapper.readTree("{\"op\":\"DROP\",\"i\":1}")));
        assertEquals(0, n);
        assertEquals(3, doc.getBlocks().size());
        assertEquals(PreviewBlockType.TABLE, doc.getBlocks().get(1).getType());
    }

    private static PreviewBlock para(String text) {
        return PreviewBlock.builder()
                .type(PreviewBlockType.PARAGRAPH)
                .text(text)
                .runs(Collections.singletonList(PreviewRun.builder().text(text).build()))
                .build();
    }
}
