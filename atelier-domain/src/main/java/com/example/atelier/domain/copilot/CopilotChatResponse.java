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
public class CopilotChatResponse {

    private String reply;

    private List<CopilotActionResult> actions;

    private String workspaceSummary;

    /** 当前任务计划 */
    private CopilotActivePlan plan;

    /** 计划是否已全部完成 */
    private Boolean planCompleted;

    /** 匹配到的可复用技能（供用户选择） */
    private List<CopilotPlaybook> matchedPlaybooks;

    /** 是否建议保存为技能 */
    private Boolean suggestSavePlaybook;
}
