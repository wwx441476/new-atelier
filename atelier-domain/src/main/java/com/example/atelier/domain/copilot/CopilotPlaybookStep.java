package com.example.atelier.domain.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 技能中的步骤模板。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotPlaybookStep {

    private String title;

    private String tool;

    private String description;

    private Integer order;
}
