package com.example.atelier.domain.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Copilot 提交的预警异步任务摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotWarningJobResult {

    private String jobId;

    private String status;

    private String ruleId;

    private String ruleCode;

    private String ruleName;

    private int pageIndex;

    private int pageSize;

    private boolean keywordOnly;
}
