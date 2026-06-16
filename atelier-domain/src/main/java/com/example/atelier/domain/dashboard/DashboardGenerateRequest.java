package com.example.atelier.domain.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardGenerateRequest {

    /** 自然语言描述或复刻需求 */
    private String prompt;

    /** 参考截图 data URL，最多 4 张 */
    private List<String> images;

    /** 指定 LLM 档案 id；为空时使用工作区激活档案 */
    private String llmProfileId;

    /** 生成后是否自动保存，默认 true */
    @Builder.Default
    private boolean autoSave = true;
}
