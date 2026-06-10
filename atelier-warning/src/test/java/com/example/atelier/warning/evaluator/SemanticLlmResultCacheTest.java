package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.warning.SemanticMatchResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SemanticLlmResultCacheTest {

    @Before
    public void setUp() {
        SemanticLlmResultCache.getInstance().clear();
    }

    @Test
    public void shouldCacheAndRetrieveResult() {
        SemanticMatchResult match = SemanticMatchResult.builder()
                .triggered(true)
                .layer("llm")
                .reason("test")
                .llmInvoked(true)
                .build();
        SemanticLlmResultCache cache = SemanticLlmResultCache.getInstance();
        cache.put("VIOLATION", "不得含烟酒", "商务接待礼盒", match);

        Assert.assertTrue(cache.get("VIOLATION", "不得含烟酒", "商务接待礼盒").isPresent());
        Assert.assertEquals("test", cache.get("VIOLATION", "不得含烟酒", "商务接待礼盒").get().getReason());
        Assert.assertFalse(cache.get("VIOLATION", "不得含烟酒", "其他文本").isPresent());
    }
}
