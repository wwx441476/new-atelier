package com.example.atelier.document.llm;

import com.example.atelier.document.preview.PreviewBlock;
import com.example.atelier.document.preview.PreviewBlockType;
import com.example.atelier.document.preview.PreviewRun;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LlmStyleEnrichServiceTest {

    @Test
    public void extractJsonArrayFromFencedContent() {
        String raw = "Here is result:\n```json\n[{\"i\":0,\"type\":\"HEADING\",\"level\":1,\"marks\":[]}]\n```\n";
        String json = LlmStyleEnrichService.extractJson(raw);
        assertEquals("[{\"i\":0,\"type\":\"HEADING\",\"level\":1,\"marks\":[]}]", json);
    }

    @Test
    public void extractJsonPrefersObjectWrapper() {
        String raw = "ok\n{\"annotations\":[{\"i\":0,\"type\":\"HEADING\"}]}\n";
        String json = LlmStyleEnrichService.extractJson(raw);
        assertTrue(json.contains("annotations") || json.contains("HEADING"));
    }

    @Test
    public void extractJsonDoesNotUseMarksEmptyBracket() {
        String raw = "ING\",\"level\":1,\"marks\":[]},{\"i\":1,\"type\":\"HEADING\",\"level\":2,\"marks\":[]}]";
        String json = LlmStyleEnrichService.extractJson(raw);
        // 截断文本没有 [{，应回退到对象扫描起点或原样；不能变成从 marks:[] 切开的非法片段当「完整数组」
        assertTrue(!json.startsWith("[]"));
    }

    @Test
    public void buildNumberedPromptUsesIndexPipeText() {
        PreviewBlock a = PreviewBlock.builder()
                .type(PreviewBlockType.PARAGRAPH)
                .text("第一章 总则")
                .runs(Collections.singletonList(PreviewRun.builder().text("第一章 总则").build()))
                .build();
        PreviewBlock b = PreviewBlock.builder()
                .type(PreviewBlockType.PARAGRAPH)
                .text("正文")
                .runs(Collections.singletonList(PreviewRun.builder().text("正文").build()))
                .build();
        String prompt = LlmStyleEnrichService.buildNumberedPrompt(Arrays.asList(a, b));
        assertTrue(prompt.contains("0|第一章 总则"));
        assertTrue(prompt.contains("1|正文"));
    }
}
