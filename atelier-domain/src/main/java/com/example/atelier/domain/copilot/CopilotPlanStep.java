package com.example.atelier.domain.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务计划中的单步。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotPlanStep {

    private String id;

    /** 步骤标题，面向用户展示 */
    private String title;

    /** 关联工具名，可选 */
    private String tool;

    /** pending / running / done / failed */
    private String status;

    /** 步骤说明 */
    private String description;
}
