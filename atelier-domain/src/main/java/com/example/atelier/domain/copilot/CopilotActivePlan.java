package com.example.atelier.domain.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 进行中的多步任务计划。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotActivePlan {

    private String planId;

    /** 若从已沉淀技能加载 */
    private String playbookId;

    private String playbookName;

    @Builder.Default
    private List<CopilotPlanStep> steps = new ArrayList<>();

    /** 下一步待执行索引（0-based） */
    @Builder.Default
    private Integer currentStepIndex = 0;

    private Boolean completed;
}
