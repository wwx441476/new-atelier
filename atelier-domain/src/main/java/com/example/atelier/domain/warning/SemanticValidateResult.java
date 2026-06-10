package com.example.atelier.domain.warning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
