package com.example.atelier.domain.warning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语义匹配结果 — 记录是否触发及匹配层级。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticMatchResult {

    private boolean triggered;

    private String reason;

    /** 匹配层级：keyword / llm / none */
    private String layer;

    /** 是否实际调用了 LLM API（词库命中或未配置 LLM 时为 false） */
    private boolean llmInvoked;
}
