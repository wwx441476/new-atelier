package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.settings.SemanticLlmConfig;

/**
 * Kimi 端点 URL 与模型解析。
 * <p>
 * Kimi Coding Plan（api.kimi.com/coding）与 cc switch 一致，走 Anthropic Messages API；
 * Kimi 开放平台（api.moonshot.cn）走 OpenAI Chat Completions API。
 * 自定义网关可通过 {@link SemanticLlmConfig#getProtocol()} 显式指定 openai / anthropic。
 */
public final class KimiEndpointSupport {

    public static final String PROTOCOL_OPENAI = "openai";
    public static final String PROTOCOL_ANTHROPIC = "anthropic";

    /** cc switch / Claude Code 同款 Coding 根地址 */
    public static final String CODING_ANTHROPIC_BASE_URL = "https://api.kimi.com/coding";
    /** OpenAI 兼容 Coding 地址（部分场景） */
    public static final String CODING_OPENAI_BASE_URL = "https://api.kimi.com/coding/v1";
    public static final String OPEN_PLATFORM_BASE_URL = "https://api.moonshot.cn/v1";
    public static final String CODING_ANTHROPIC_MODEL = "kimi-k2.6";
    public static final String CODING_OPENAI_MODEL = "kimi-for-coding";
    public static final String OPEN_PLATFORM_MODEL = "kimi-k2.6";
    public static final String CODING_USER_AGENT = "claude-code/1.0";
    public static final String ANTHROPIC_VERSION = "2023-06-01";

    private KimiEndpointSupport() {
    }

    public static boolean isKimiCodingEndpoint(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase().contains("kimi.com/coding");
    }

    /** Kimi Coding 默认走 Anthropic 协议（与 cc switch 一致） */
    public static boolean useAnthropicProtocol(String baseUrl, String provider) {
        return isKimiCodingEndpoint(baseUrl) || "kimi-coding".equalsIgnoreCase(provider);
    }

    /**
     * 是否走 Anthropic Messages。
     * 显式 protocol 优先；否则按 provider / baseUrl 推断。
     */
    public static boolean shouldUseAnthropic(SemanticLlmConfig config) {
        if (config == null) {
            return false;
        }
        String protocol = config.getProtocol();
        if (protocol != null && !protocol.trim().isEmpty()) {
            return PROTOCOL_ANTHROPIC.equalsIgnoreCase(protocol.trim());
        }
        return useAnthropicProtocol(config.getBaseUrl(), config.getProvider());
    }

    public static String normalizeProtocol(String protocol, String baseUrl, String provider) {
        if (protocol != null && !protocol.trim().isEmpty()) {
            String p = protocol.trim().toLowerCase();
            if (PROTOCOL_ANTHROPIC.equals(p) || PROTOCOL_OPENAI.equals(p)) {
                return p;
            }
        }
        if (useAnthropicProtocol(baseUrl, provider)) {
            return PROTOCOL_ANTHROPIC;
        }
        // 与前端 custom 默认一致：空 protocol 不再静默落到 openai（否则 aitoken 等网关会打错 /chat/completions）
        if (provider != null && SemanticLlmProviders.CUSTOM.equalsIgnoreCase(provider.trim())) {
            return PROTOCOL_ANTHROPIC;
        }
        return PROTOCOL_OPENAI;
    }

    /**
     * OpenAI 兼容 Base URL，确保以 /v1 结尾。
     */
    public static String normalizeOpenAiBaseUrl(String baseUrl, String provider) {
        String url = baseUrl != null && !baseUrl.trim().isEmpty()
                ? baseUrl.trim()
                : defaultOpenAiBaseUrl(provider);
        url = stripSuffix(url, "/chat/completions");
        url = stripSuffix(url, "/messages");
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/coding")) {
            url = url + "/v1";
        } else if (!url.endsWith("/v1") && !url.contains("/v1/")) {
            // 自定义网关常只填域名，补全 /v1 以命中 /v1/chat/completions
            url = url + "/v1";
        }
        return url;
    }

    /**
     * 多模态是否应改写到官方 Kimi Coding OpenAI 地址。
     * 仅当 baseUrl 本身就是 kimi.com/coding 时改写；自定义代理（如 aitoken）必须保留原地址。
     */
    public static boolean shouldRewriteVisionToOfficialCoding(String baseUrl) {
        return isKimiCodingEndpoint(baseUrl);
    }

    /**
     * Anthropic Messages API 完整 URL。
     * 输入 https://api.kimi.com/coding → https://api.kimi.com/coding/v1/messages
     */
    public static String buildAnthropicMessagesUrl(String baseUrl) {
        String url = baseUrl != null ? baseUrl.trim() : CODING_ANTHROPIC_BASE_URL;
        url = stripSuffix(url, "/messages");
        url = stripSuffix(url, "/chat/completions");
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/v1")) {
            return url + "/messages";
        }
        if (url.endsWith("/coding")) {
            return url + "/v1/messages";
        }
        return url + "/v1/messages";
    }

    public static String buildChatCompletionsUrl(String normalizedBaseUrl) {
        String base = normalizedBaseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        return base + "/chat/completions";
    }

    /**
     * 截图识读场景下的模型：Kimi Coding 的 kimi-for-coding 仅文本，需切到支持多模态的模型。
     */
    public static String resolveVisionModel(String baseUrl, String provider, String model) {
        String trimmed = model != null ? model.trim() : "";
        if (isKimiCodingEndpoint(baseUrl) || "kimi-coding".equalsIgnoreCase(provider)) {
            if (trimmed.isEmpty() || CODING_OPENAI_MODEL.equals(trimmed)) {
                return CODING_ANTHROPIC_MODEL;
            }
            return trimmed;
        }
        if (SemanticLlmProviders.OPENAI.equalsIgnoreCase(provider)) {
            return trimmed.isEmpty() ? "gpt-4o" : trimmed;
        }
        if (SemanticLlmProviders.DASHSCOPE.equalsIgnoreCase(provider)) {
            return trimmed.isEmpty() ? "qwen-vl-max" : trimmed;
        }
        if (SemanticLlmProviders.KIMI.equalsIgnoreCase(provider)) {
            return trimmed.isEmpty() ? OPEN_PLATFORM_MODEL : trimmed;
        }
        return trimmed.isEmpty() ? "gpt-4o" : trimmed;
    }

    public static String resolveModel(String baseUrl, String provider, String model) {
        String trimmed = model != null ? model.trim() : "";
        if (useAnthropicProtocol(baseUrl, provider)) {
            return trimmed.isEmpty() ? CODING_ANTHROPIC_MODEL : trimmed;
        }
        if (isKimiCodingEndpoint(baseUrl)) {
            if (trimmed.isEmpty()) {
                return CODING_OPENAI_MODEL;
            }
            return trimmed;
        }
        if (!trimmed.isEmpty()) {
            return trimmed;
        }
        if (SemanticLlmProviders.KIMI.equalsIgnoreCase(provider) || "kimi-coding".equalsIgnoreCase(provider)) {
            return OPEN_PLATFORM_MODEL;
        }
        return "gpt-4o-mini";
    }

    private static String defaultOpenAiBaseUrl(String provider) {
        if (SemanticLlmProviders.KIMI.equalsIgnoreCase(provider) || "kimi-coding".equalsIgnoreCase(provider)) {
            return OPEN_PLATFORM_BASE_URL;
        }
        return "https://api.openai.com/v1";
    }

    private static String stripSuffix(String url, String suffix) {
        if (url != null && url.endsWith(suffix)) {
            return url.substring(0, url.length() - suffix.length());
        }
        return url;
    }
}
