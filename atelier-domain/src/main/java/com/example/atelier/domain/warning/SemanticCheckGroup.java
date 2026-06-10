package com.example.atelier.domain.warning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义条件组 — 组内 checks AND，多组之间 OR。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticCheckGroup {

    @Builder.Default
    private List<SemanticFieldCheck> checks = new ArrayList<>();
}
