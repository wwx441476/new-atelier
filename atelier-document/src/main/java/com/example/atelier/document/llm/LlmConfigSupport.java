package com.example.atelier.document.llm;

import com.example.atelier.domain.settings.SemanticLlmConfig;

/**
 * 文档对比侧 LLM 可用性判定：以 API Key 为准。
 * 档案上的 enabled 开关主要服务语义预警；对比页自身另有「AI 解读」开关。
 */
final class LlmConfigSupport {

    private LlmConfigSupport() {
    }

    static boolean hasApiKey(SemanticLlmConfig config) {
        return config != null
                && config.getApiKey() != null
                && !config.getApiKey().trim().isEmpty();
    }

    /** @return null 表示可用；否则为拒绝原因 */
    static String rejectReason(SemanticLlmConfig config) {
        if (config == null) {
            return "LLM 未配置";
        }
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            return "LLM API Key 未配置";
        }
        if (config.getBaseUrl() == null || config.getBaseUrl().trim().isEmpty()) {
            return "LLM Base URL 未配置";
        }
        if (config.getModel() == null || config.getModel().trim().isEmpty()) {
            return "LLM 模型未配置";
        }
        return null;
    }
}
