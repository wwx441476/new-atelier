package com.example.atelier.domain.warning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单字段语义子条件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticFieldCheck {

    private String fieldCode;

    @Builder.Default
    private SemanticCheckMode checkMode = SemanticCheckMode.VIOLATION;

    private String policy;

    private List<String> hintKeywords;

    /** KEYWORD / LLM / HYBRID */
    private String matchMode;

    private List<String> expandedKeywords;
}
