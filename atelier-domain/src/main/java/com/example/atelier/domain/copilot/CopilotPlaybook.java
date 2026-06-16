package com.example.atelier.domain.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Copilot 可复用技能（Playbook）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotPlaybook {

    private String id;

    private String code;

    private String name;

    private String description;

    /** 触发关键词，用于匹配用户意图 */
    @Builder.Default
    private List<String> triggerKeywords = new ArrayList<>();

    @Builder.Default
    private List<CopilotPlaybookStep> steps = new ArrayList<>();

    private Boolean enabled;

    private Integer usageCount;
}
