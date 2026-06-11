package com.example.atelier.warning.evaluator;

import org.junit.Assert;
import org.junit.Test;

public class KimiEndpointSupportTest {

    @Test
    public void shouldBuildAnthropicMessagesUrlFromCcSwitchBase() {
        String url = KimiEndpointSupport.buildAnthropicMessagesUrl("https://api.kimi.com/coding");
        Assert.assertEquals("https://api.kimi.com/coding/v1/messages", url);
    }

    @Test
    public void shouldKeepKimiK26OnAnthropicCoding() {
        String model = KimiEndpointSupport.resolveModel(
                "https://api.kimi.com/coding", "kimi-coding", "kimi-k2.6");
        Assert.assertEquals("kimi-k2.6", model);
    }

    @Test
    public void shouldUseAnthropicProtocolForCodingProvider() {
        Assert.assertTrue(KimiEndpointSupport.useAnthropicProtocol(
                "https://api.kimi.com/coding", "kimi-coding"));
    }

    @Test
    public void shouldKeepKimiK26OnOpenPlatform() {
        String model = KimiEndpointSupport.resolveModel(
                "https://api.moonshot.cn/v1", "kimi", "kimi-k2.6");
        Assert.assertEquals("kimi-k2.6", model);
    }

    @Test
    public void shouldUseKimiK26ForCodingVisionWhenModelIsTextOnly() {
        String model = KimiEndpointSupport.resolveVisionModel(
                "https://api.kimi.com/coding", "kimi-coding", "kimi-for-coding");
        Assert.assertEquals("kimi-k2.6", model);
    }
}
