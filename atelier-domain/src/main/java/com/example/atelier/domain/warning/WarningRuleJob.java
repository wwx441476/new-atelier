package com.example.atelier.domain.warning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 预警规则异步执行任务。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarningRuleJob {

    private String id;

    private String ruleId;

    private String ruleCode;

    private String ruleName;

    private WarningRuleJobStatus status;

    private WarningRuleJobSource source;

    private int progress;

    private String errorMessage;

    /** 全表总行数 */
    private Long total;

    /** 当前页命中行数（非全表命中总数） */
    private Long matchedCount;

    private WarningRuleJobParams params;

    private WarningRulePreviewResult result;

    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}
