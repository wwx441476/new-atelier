package com.example.atelier.warning.service;

import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;
import com.example.atelier.warning.evaluator.HybridSemanticMatcher;
import org.springframework.stereotype.Service;

@Service
public class SemanticRuleEvaluator {

    private final SemanticLlmConfigLoader llmConfigLoader;

    public SemanticRuleEvaluator(SemanticLlmConfigLoader llmConfigLoader) {
        this.llmConfigLoader = llmConfigLoader;
    }

    public SemanticMatchResult evaluate(String text, SemanticRuleConfig config) {
        HybridSemanticMatcher matcher = new HybridSemanticMatcher(llmConfigLoader.load());
        return matcher.match(text, config);
    }
}
