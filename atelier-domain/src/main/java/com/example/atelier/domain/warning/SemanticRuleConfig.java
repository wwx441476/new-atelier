package com.example.atelier.domain.warning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 语义预警规则配置 — 描述对元数据表字段的语义匹配策略。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticRuleConfig {

    private String metaTableId;

    private String fieldCode;

    private String policy;

    /** 用户提示关键词 */
    private List<String> hintKeywords;

    /** 匹配模式：KEYWORD / LLM / HYBRID */
    private String matchMode;

    /** 词库扩展后的关键词 */
    private List<String> expandedKeywords;
}
