package com.example.atelier.domain.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 配置导入选项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigImportOptions {

    /** 是否导入数据源（默认 true） */
    @Builder.Default
    private boolean importDatasources = true;

    /** 是否导入元数据表与字段 */
    @Builder.Default
    private boolean importMetadata = true;

    /** 是否导入维度及手动维护的维度值 */
    @Builder.Default
    private boolean importDimensions = true;

    /** 是否导入指标定义 */
    @Builder.Default
    private boolean importMetrics = true;

    /** 是否导入预警规则 */
    @Builder.Default
    private boolean importWarningRules = true;

    /** 是否导入语义检测 LLM 配置 */
    @Builder.Default
    private boolean importSemanticLlm = true;
}
