package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class HybridSemanticMatcherTest {

    @Test
    public void shouldUseKeywordLayerWhenKeywordMatches() {
        SemanticRuleConfig config = SemanticRuleConfig.builder()
                .hintKeywords(Arrays.asList("茅台"))
                .matchMode("HYBRID")
                .build();
        HybridSemanticMatcher matcher = new HybridSemanticMatcher(disabledLlm());
        SemanticMatchResult result = matcher.match("采购茅台", config);
        Assert.assertTrue(result.isTriggered());
        Assert.assertEquals("keyword", result.getLayer());
    }

    @Test
    public void shouldSkipLlmWhenKeywordModeOnly() {
        SemanticRuleConfig config = SemanticRuleConfig.builder()
                .hintKeywords(Arrays.asList("茅台"))
                .matchMode("KEYWORD")
                .build();
        HybridSemanticMatcher matcher = new HybridSemanticMatcher(disabledLlm());
        SemanticMatchResult result = matcher.match("宴请酒水", config);
        Assert.assertFalse(result.isTriggered());
    }

    private SemanticLlmConfig disabledLlm() {
        return SemanticLlmConfig.builder().enabled(false).build();
    }
}
