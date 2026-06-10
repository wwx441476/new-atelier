package com.example.atelier.domain.warning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticValidateResult {

    private boolean valid;

    private String message;

    private Boolean sampleTriggered;

    private String sampleMatchReason;

    private String sampleMatchLayer;

    /** 样例试跑时各字段子条件明细 */
    @Builder.Default
    private List<SemanticSampleCheckResult> sampleChecks = new ArrayList<>();
}
