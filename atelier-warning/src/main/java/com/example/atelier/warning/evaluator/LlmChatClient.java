package com.example.atelier.warning.evaluator;

import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.infra.exception.AtelierException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LLM HTTP 客户端（Java 8 兼容）。
 * 支持 OpenAI Chat Completions 与 Anthropic Messages（Kimi Coding / cc switch 同款）。
 */
public class LlmChatClient {

    private static final Logger log = LoggerFactory.getLogger(LlmChatClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 语义判定仅需简短 JSON，降低生成 token 以缩短耗时 */
    static final int SEMANTIC_MAX_TOKENS = 128;
    /** 智能体对话需要更长回复 */
    public static final int AGENT_MAX_TOKENS = 2048;

    public String chat(SemanticLlmConfig config, String systemPrompt, String userPrompt) {
        return chat(config, systemPrompt, userPrompt, SEMANTIC_MAX_TOKENS);
    }

    public String chat(SemanticLlmConfig config, String systemPrompt, String userPrompt, int maxTokens) {
        return chat(config, systemPrompt, userPrompt, null, maxTokens);
    }

    public String chat(SemanticLlmConfig config, String systemPrompt, String userPrompt,
            List<String> imageDataUrls, int maxTokens) {
        if (config == null || config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new AtelierException("LLM API Key 未配置");
        }
        int resolvedMaxTokens = maxTokens > 0 ? maxTokens : SEMANTIC_MAX_TOKENS;
        List<String> images = imageDataUrls != null ? imageDataUrls : Collections.emptyList();
        return LlmConcurrencyLimiter.withPermit(() -> {
            SemanticLlmConfig resolved = config;
            boolean hasImages = !images.isEmpty();
            if (hasImages) {
                resolved = buildVisionConfig(config);
                log.info("LLM multimodal 请求: images={}, model={}, baseUrl={}",
                        images.size(), resolved.getModel(), resolved.getBaseUrl());
            }
            boolean anthropic = !hasImages
                    && KimiEndpointSupport.useAnthropicProtocol(resolved.getBaseUrl(), resolved.getProvider());
            if (anthropic) {
                return chatAnthropic(resolved, systemPrompt, userPrompt, images, resolvedMaxTokens);
            }
            return chatOpenAi(resolved, systemPrompt, userPrompt, images, resolvedMaxTokens);
        });
    }

    private SemanticLlmConfig buildVisionConfig(SemanticLlmConfig config) {
        SemanticLlmConfig copy = SemanticLlmConfig.builder()
                .enabled(config.isEnabled())
                .provider(config.getProvider())
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .model(config.getModel())
                .timeoutSeconds(config.getTimeoutSeconds())
                .build();
        SemanticLlmProviders.applyProviderDefaults(copy);
        if (KimiEndpointSupport.useAnthropicProtocol(copy.getBaseUrl(), copy.getProvider())) {
            copy.setBaseUrl(KimiEndpointSupport.CODING_OPENAI_BASE_URL);
        }
        copy.setModel(KimiEndpointSupport.resolveVisionModel(
                copy.getBaseUrl(), copy.getProvider(), copy.getModel()));
        return copy;
    }

    private String chatAnthropic(SemanticLlmConfig config, String systemPrompt, String userPrompt,
            List<String> imageDataUrls, int maxTokens) {
        String model = KimiEndpointSupport.resolveModel(config.getBaseUrl(), config.getProvider(), config.getModel());
        int timeout = timeoutSeconds(config);
        String endpoint = KimiEndpointSupport.buildAnthropicMessagesUrl(config.getBaseUrl());
        log.info("LLM HTTP 请求: protocol=anthropic, endpoint={}, model={}, provider={}, timeoutSec={}",
                endpoint, model, config.getProvider(), timeout);
        long startedAt = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                body.put("system", systemPrompt);
            }
            ArrayNode messages = body.putArray("messages");
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            if (imageDataUrls != null && !imageDataUrls.isEmpty()) {
                ArrayNode content = user.putArray("content");
                for (String dataUrl : imageDataUrls) {
                    ParsedDataUrl parsed = parseDataUrl(dataUrl);
                    ObjectNode image = content.addObject();
                    image.put("type", "image");
                    ObjectNode source = image.putObject("source");
                    source.put("type", "base64");
                    source.put("media_type", parsed.mediaType);
                    source.put("data", parsed.base64Data);
                }
                content.addObject().put("type", "text").put("text", userPrompt);
            } else {
                user.put("content", userPrompt);
            }

            byte[] payload = MAPPER.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            connection = openConnection(endpoint, timeout);
            String apiKey = config.getApiKey().trim();
            connection.setRequestProperty("x-api-key", apiKey);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("anthropic-version", KimiEndpointSupport.ANTHROPIC_VERSION);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Content-Length", String.valueOf(payload.length));
            connection.setRequestProperty("User-Agent", KimiEndpointSupport.CODING_USER_AGENT);
            connection.setDoOutput(true);

            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
            }

            return parseResponse(connection, endpoint, model, true, startedAt);
        } catch (AtelierException e) {
            log.warn("LLM HTTP 失败: protocol=anthropic, endpoint={}, model={}, error={}",
                    endpoint, model, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("LLM HTTP 失败: protocol=anthropic, endpoint={}, model={}, error={}",
                    endpoint, model, e.getMessage());
            throw new AtelierException("LLM 调用失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String chatOpenAi(SemanticLlmConfig config, String systemPrompt, String userPrompt,
            List<String> imageDataUrls, int maxTokens) {
        String baseUrl = KimiEndpointSupport.normalizeOpenAiBaseUrl(config.getBaseUrl(), config.getProvider());
        String model = KimiEndpointSupport.resolveModel(baseUrl, config.getProvider(), config.getModel());
        int timeout = timeoutSeconds(config);
        String endpoint = KimiEndpointSupport.buildChatCompletionsUrl(baseUrl);
        log.info("LLM HTTP 请求: protocol=openai, endpoint={}, model={}, provider={}, timeoutSec={}",
                endpoint, model, config.getProvider(), timeout);
        long startedAt = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            ArrayNode messages = body.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                ObjectNode system = messages.addObject();
                system.put("role", "system");
                system.put("content", systemPrompt);
            }
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            if (imageDataUrls != null && !imageDataUrls.isEmpty()) {
                ArrayNode content = user.putArray("content");
                for (String dataUrl : imageDataUrls) {
                    ObjectNode image = content.addObject();
                    image.put("type", "image_url");
                    ObjectNode imageUrl = image.putObject("image_url");
                    imageUrl.put("url", dataUrl);
                    imageUrl.put("detail", "high");
                }
                content.addObject().put("type", "text").put("text", userPrompt);
            } else {
                user.put("content", userPrompt);
            }

            byte[] payload = MAPPER.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            connection = openConnection(endpoint, timeout);
            connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey().trim());
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Content-Length", String.valueOf(payload.length));
            if (KimiEndpointSupport.isKimiCodingEndpoint(baseUrl)) {
                connection.setRequestProperty("User-Agent", KimiEndpointSupport.CODING_USER_AGENT);
            }
            connection.setDoOutput(true);

            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
            }

            return parseResponse(connection, endpoint, model, false, startedAt);
        } catch (AtelierException e) {
            log.warn("LLM HTTP 失败: protocol=openai, endpoint={}, model={}, error={}",
                    endpoint, model, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("LLM HTTP 失败: protocol=openai, endpoint={}, model={}, error={}",
                    endpoint, model, e.getMessage());
            throw new AtelierException("LLM 调用失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String parseResponse(HttpURLConnection connection, String endpoint, String model, boolean anthropic,
            long startedAt)
            throws Exception {
        int status = connection.getResponseCode();
        long elapsedMs = System.currentTimeMillis() - startedAt;
        String responseBody = readBody(status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream());
        if (status < 200 || status >= 300) {
            log.warn("LLM HTTP 响应: status={}, elapsedMs={}, protocol={}, endpoint={}, model={}, body={}",
                    status, elapsedMs, anthropic ? "anthropic" : "openai", endpoint, model,
                    truncate(responseBody, 200));
            throw new AtelierException("LLM 请求失败: HTTP " + status
                    + " — " + truncate(responseBody, 200)
                    + "（请求: " + endpoint + ", model=" + model
                    + ", protocol=" + (anthropic ? "anthropic" : "openai") + "）");
        }
        log.info("LLM HTTP 响应: status={}, elapsedMs={}, protocol={}, endpoint={}, model={}, contentLen={}",
                status, elapsedMs, anthropic ? "anthropic" : "openai", endpoint, model, responseBody.length());
        JsonNode root = MAPPER.readTree(responseBody);
        if (anthropic) {
            JsonNode contentBlocks = root.path("content");
            if (contentBlocks.isArray()) {
                for (JsonNode block : contentBlocks) {
                    if ("text".equals(block.path("type").asText())) {
                        String text = block.path("text").asText("").trim();
                        if (!text.isEmpty()) {
                            return text;
                        }
                    }
                }
            }
            throw new AtelierException("LLM 响应为空");
        }
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().trim().isEmpty()) {
            throw new AtelierException("LLM 响应为空");
        }
        return content.asText().trim();
    }

    private static int timeoutSeconds(SemanticLlmConfig config) {
        return config.getTimeoutSeconds() != null && config.getTimeoutSeconds() > 0
                ? config.getTimeoutSeconds()
                : 30;
    }

    private static HttpURLConnection openConnection(String url, int timeoutSeconds) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(timeoutSeconds * 1000);
        connection.setReadTimeout(timeoutSeconds * 1000);
        return connection;
    }

    private static String readBody(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private static ParsedDataUrl parseDataUrl(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:image/")) {
            throw new AtelierException("无效的图片格式");
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            throw new AtelierException("无效的图片 data URL");
        }
        String header = dataUrl.substring(5, comma);
        if (!header.endsWith(";base64")) {
            throw new AtelierException("仅支持 base64 编码的图片");
        }
        String mediaType = header.substring(0, header.length() - ";base64".length());
        String base64Data = dataUrl.substring(comma + 1).trim();
        if (base64Data.isEmpty()) {
            throw new AtelierException("图片内容为空");
        }
        return new ParsedDataUrl(mediaType, base64Data);
    }

    private static final class ParsedDataUrl {
        private final String mediaType;
        private final String base64Data;

        private ParsedDataUrl(String mediaType, String base64Data) {
            this.mediaType = mediaType;
            this.base64Data = base64Data;
        }
    }

    public static String extractJsonObject(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    public static List<String> parseKeywordList(String content) {
        try {
            JsonNode root = MAPPER.readTree(extractJsonObject(content));
            JsonNode keywords = root.path("keywords");
            List<String> result = new ArrayList<>();
            if (keywords.isArray()) {
                for (JsonNode node : keywords) {
                    String value = node.asText("").trim();
                    if (!value.isEmpty()) {
                        result.add(value);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            throw new AtelierException("LLM 词库响应解析失败: " + e.getMessage(), e);
        }
    }
}
