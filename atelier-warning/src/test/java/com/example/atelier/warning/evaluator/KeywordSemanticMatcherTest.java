package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class KeywordSemanticMatcherTest {

    @Test
    public void shouldMatchKeywordInText() {
        SemanticRuleConfig config = SemanticRuleConfig.builder()
                .hintKeywords(Arrays.asList("茅台", "五粮液"))
                .expandedKeywords(Arrays.asList("飞天"))
                .build();
        KeywordSemanticMatcher matcher = new KeywordSemanticMatcher();
        SemanticMatchResult result = matcher.match("本次采购茅台两瓶", config);
        Assert.assertTrue(result.isTriggered());
        Assert.assertEquals("keyword", result.getLayer());
        Assert.assertTrue(result.getReason().contains("茅台"));
    }

    @Test
    public void shouldMatchExpandedKeyword() {
        SemanticRuleConfig config = SemanticRuleConfig.builder()
                .hintKeywords(Collections.singletonList("茅台"))
                .expandedKeywords(Collections.singletonList("飞天"))
                .build();
        KeywordSemanticMatcher matcher = new KeywordSemanticMatcher();
        SemanticMatchResult result = matcher.match("采购飞天两瓶", config);
        Assert.assertTrue(result.isTriggered());
        Assert.assertEquals("keyword", result.getLayer());
        Assert.assertTrue(result.getReason().contains("飞天"));
    }

    @Test
    public void shouldNotMatchCleanText() {
        SemanticRuleConfig config = SemanticRuleConfig.builder()
                .hintKeywords(Arrays.asList("茅台", "五粮液"))
                .expandedKeywords(Collections.singletonList("飞天"))
                .build();
        KeywordSemanticMatcher matcher = new KeywordSemanticMatcher();
        SemanticMatchResult result = matcher.match("办公用品采购", config);
        Assert.assertFalse(result.isTriggered());
        Assert.assertEquals("none", result.getLayer());
    }

    @Test
    public void shouldBeCaseInsensitive() {
        SemanticRuleConfig config = SemanticRuleConfig.builder()
                .hintKeywords(Collections.singletonList("VIP"))
                .build();
        KeywordSemanticMatcher matcher = new KeywordSemanticMatcher();
        SemanticMatchResult result = matcher.match("vip客户采购", config);
        Assert.assertTrue(result.isTriggered());
        Assert.assertEquals("keyword", result.getLayer());
        Assert.assertTrue(result.getReason().toLowerCase().contains("vip"));
    }
}
