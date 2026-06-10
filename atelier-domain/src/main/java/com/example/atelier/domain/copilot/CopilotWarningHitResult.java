package com.example.atelier.domain.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Copilot 展示的预警命中行结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotWarningHitResult {

    private String jobId;

    private String ruleId;

    private String ruleCode;

    private String ruleName;

    private String expression;

    private long total;

    private long pageMatchedCount;

    private int pageIndex;

    private int pageSize;

    private List<Map<String, Object>> matchedRows;

    private Map<String, String> headers;
}
