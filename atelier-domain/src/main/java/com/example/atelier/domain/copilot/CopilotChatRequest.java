package com.example.atelier.domain.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotChatRequest {

    private List<CopilotChatMessage> messages;

    /** 当前页面路径，如 /metadata，用于上下文提示 */
    private String currentPage;

    /** true 时只规划不执行 */
    @Builder.Default
    private boolean dryRun = false;

    /** 指定 Copilot 使用的 LLM 档案 id；为空时使用工作区激活档案 */
    private String llmProfileId;

    /** 进行中的多步计划（继续执行时传入） */
    private CopilotActivePlan activePlan;

    /** 指定使用的技能 id */
    private String playbookId;
}
