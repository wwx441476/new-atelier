package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.warning.SemanticMatchResult;
import com.example.atelier.domain.warning.SemanticRuleConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class KeywordSemanticMatcher implements SemanticMatcher {

    @Override
    public SemanticMatchResult match(String text, SemanticRuleConfig config) {
        if (text == null || config == null) {
            return noMatch();
        }

        String normalizedText = text.toLowerCase(Locale.ROOT);
        for (String keyword : collectKeywords(config)) {
            if (keyword == null || keyword.isEmpty()) {
                continue;
            }
            if (normalizedText.contains(keyword.toLowerCase(Locale.ROOT))) {
                return SemanticMatchResult.builder()
                        .triggered(true)
                        .layer("keyword")
                        .reason("匹配关键词: " + keyword)
                        .build();
            }
        }
        return noMatch();
    }

    private List<String> collectKeywords(SemanticRuleConfig config) {
        List<String> keywords = new ArrayList<>();
        if (config.getHintKeywords() != null) {
            keywords.addAll(config.getHintKeywords());
        }
        if (config.getExpandedKeywords() != null) {
            keywords.addAll(config.getExpandedKeywords());
        }
        return keywords;
    }

    private SemanticMatchResult noMatch() {
        return SemanticMatchResult.builder()
                .triggered(false)
                .layer("none")
                .build();
    }
}
