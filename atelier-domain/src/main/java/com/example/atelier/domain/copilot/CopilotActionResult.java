package com.example.atelier.domain.copilot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotActionResult {

    private String tool;

    private boolean success;

    /** 仅规划模式下的待执行动作，未真正执行 */
    private boolean planned;

    private String message;

    private Object result;
}
