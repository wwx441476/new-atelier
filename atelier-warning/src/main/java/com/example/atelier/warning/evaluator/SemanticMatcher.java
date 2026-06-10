package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;

public interface SemanticMatcher {

    SemanticMatchResult match(String text, SemanticRuleConfig config);
}
