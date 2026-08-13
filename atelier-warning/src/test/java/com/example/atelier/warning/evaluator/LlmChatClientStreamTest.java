package com.example.atelier.warning.evaluator;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LlmChatClientStreamTest {

    @Test
    public void accumulateOpenAiSseJoinsDeltas() throws Exception {
        String sse = ""
                + "data: {\"choices\":[{\"delta\":{\"content\":\"[{\\\"i\\\":0\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\",\\\"type\\\":\\\"HEADING\\\"}\"}}]}\n\n"
                + "data: [DONE]\n\n";
        String text = LlmChatClient.accumulateOpenAiSse(
                new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));
        assertTrue(text.contains("HEADING"));
        assertTrue(text.startsWith("[{\"i\":0"));
    }

    @Test
    public void accumulateAnthropicSseJoinsTextDeltas() throws Exception {
        String sse = ""
                + "event: content_block_delta\n"
                + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}\n\n"
                + "event: content_block_delta\n"
                + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" World\"}}\n\n"
                + "event: message_stop\n"
                + "data: {\"type\":\"message_stop\"}\n\n";
        String text = LlmChatClient.accumulateAnthropicSse(
                new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));
        assertEquals("Hello World", text);
    }

    @Test
    public void normalizeProtocolCustomBlankDefaultsAnthropic() {
        assertEquals("anthropic",
                KimiEndpointSupport.normalizeProtocol(null, "https://aitoken.example/v1", "custom"));
        assertEquals("anthropic",
                KimiEndpointSupport.normalizeProtocol("anthropic", "https://aitoken.example/v1", "custom"));
        assertEquals("openai",
                KimiEndpointSupport.normalizeProtocol("openai", "https://aitoken.example/v1", "custom"));
        assertEquals("openai",
                KimiEndpointSupport.normalizeProtocol(null, "https://api.openai.com/v1", "openai"));
    }
}
