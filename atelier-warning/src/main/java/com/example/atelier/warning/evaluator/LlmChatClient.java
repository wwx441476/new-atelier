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
 * 支持 OpenAI Chat Completions 与 Anthropic Messages；支持 SSE 流式（降低整包等待超时风险）。
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
        return chat(config, systemPrompt, userPrompt, null, maxTokens, false);
    }

    public String chat(SemanticLlmConfig config, String systemPrompt, String userPrompt,
            List<String> imageDataUrls, int maxTokens) {
        return chat(config, systemPrompt, userPrompt, imageDataUrls, maxTokens, false);
    }

    /**
     * @param stream true 时使用 SSE 流式读取；首包后按块续读，避免现场环境整包 Read timed out
     */
    public String chat(SemanticLlmConfig config, String systemPrompt, String userPrompt,
            List<String> imageDataUrls, int maxTokens, boolean stream) {
        if (config == null || config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new AtelierException("LLM API Key 未配置");
        }
        int resolvedMaxTokens = maxTokens > 0 ? maxTokens : SEMANTIC_MAX_TOKENS;
        List<String> images = imageDataUrls != null ? imageDataUrls : Collections.emptyList();
        return LlmConcurrencyLimiter.withPermit(() -> {
            SemanticLlmConfig resolved = copyConfig(config);
            resolved.setProtocol(KimiEndpointSupport.normalizeProtocol(
                    resolved.getProtocol(), resolved.getBaseUrl(), resolved.getProvider()));
            boolean hasImages = !images.isEmpty();
            boolean anthropic = KimiEndpointSupport.shouldUseAnthropic(resolved);
            if (hasImages) {
                resolved = buildVisionConfig(resolved, anthropic);
                anthropic = KimiEndpointSupport.shouldUseAnthropic(resolved);
                log.info("LLM multimodal 请求: images={}, model={}, baseUrl={}, protocol={}, stream={}",
                        images.size(), resolved.getModel(), resolved.getBaseUrl(),
                        anthropic ? "anthropic" : "openai", stream);
            }
            if (anthropic) {
                return chatAnthropic(resolved, systemPrompt, userPrompt, images, resolvedMaxTokens, stream);
            }
            return chatOpenAi(resolved, systemPrompt, userPrompt, images, resolvedMaxTokens, stream);
        });
    }

    private SemanticLlmConfig buildVisionConfig(SemanticLlmConfig config, boolean anthropicPreferred) {
        SemanticLlmConfig copy = copyConfig(config);
        SemanticLlmProviders.applyProviderDefaults(copy);
        if (anthropicPreferred && !KimiEndpointSupport.shouldRewriteVisionToOfficialCoding(copy.getBaseUrl())) {
            copy.setProtocol(KimiEndpointSupport.PROTOCOL_ANTHROPIC);
            copy.setModel(KimiEndpointSupport.resolveVisionModel(
                    copy.getBaseUrl(), copy.getProvider(), copy.getModel()));
            return copy;
        }
        if (KimiEndpointSupport.shouldRewriteVisionToOfficialCoding(copy.getBaseUrl())) {
            copy.setBaseUrl(KimiEndpointSupport.CODING_OPENAI_BASE_URL);
            copy.setProtocol(KimiEndpointSupport.PROTOCOL_OPENAI);
        } else {
            copy.setBaseUrl(KimiEndpointSupport.normalizeOpenAiBaseUrl(
                    copy.getBaseUrl(), copy.getProvider()));
            copy.setProtocol(KimiEndpointSupport.PROTOCOL_OPENAI);
        }
        copy.setModel(KimiEndpointSupport.resolveVisionModel(
                copy.getBaseUrl(), copy.getProvider(), copy.getModel()));
        return copy;
    }

    private String chatAnthropic(SemanticLlmConfig config, String systemPrompt, String userPrompt,
            List<String> imageDataUrls, int maxTokens, boolean stream) {
        String model = KimiEndpointSupport.resolveModel(config.getBaseUrl(), config.getProvider(), config.getModel());
        int timeout = timeoutSeconds(config);
        String endpoint = KimiEndpointSupport.buildAnthropicMessagesUrl(config.getBaseUrl());
        log.info("LLM HTTP 请求: protocol=anthropic, stream={}, endpoint={}, model={}, provider={}, timeoutSec={}",
                stream, endpoint, model, config.getProvider(), timeout);
        long startedAt = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            body.put("stream", stream);
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
            connection = openConnection(endpoint, timeout, stream);
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

            if (stream) {
                return readAnthropicStream(connection, endpoint, model, startedAt);
            }
            return parseResponse(connection, endpoint, model, true, startedAt);
        } catch (AtelierException e) {
            log.warn("LLM HTTP 失败: protocol=anthropic, stream={}, endpoint={}, model={}, error={}",
                    stream, endpoint, model, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("LLM HTTP 失败: protocol=anthropic, stream={}, endpoint={}, model={}, error={}",
                    stream, endpoint, model, e.getMessage());
            throw new AtelierException("LLM 调用失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String chatOpenAi(SemanticLlmConfig config, String systemPrompt, String userPrompt,
            List<String> imageDataUrls, int maxTokens, boolean stream) {
        String baseUrl = KimiEndpointSupport.normalizeOpenAiBaseUrl(config.getBaseUrl(), config.getProvider());
        String model = KimiEndpointSupport.resolveModel(baseUrl, config.getProvider(), config.getModel());
        int timeout = timeoutSeconds(config);
        String endpoint = KimiEndpointSupport.buildChatCompletionsUrl(baseUrl);
        log.info("LLM HTTP 请求: protocol=openai, stream={}, endpoint={}, model={}, provider={}, timeoutSec={}",
                stream, endpoint, model, config.getProvider(), timeout);
        long startedAt = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            body.put("stream", stream);
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
                    imageUrl.put("detail", "low");
                }
                content.addObject().put("type", "text").put("text", userPrompt);
            } else {
                user.put("content", userPrompt);
            }

            byte[] payload = MAPPER.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            connection = openConnection(endpoint, timeout, stream);
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

            if (stream) {
                return readOpenAiStream(connection, endpoint, model, startedAt);
            }
            return parseResponse(connection, endpoint, model, false, startedAt);
        } catch (AtelierException e) {
            log.warn("LLM HTTP 失败: protocol=openai, stream={}, endpoint={}, model={}, error={}",
                    stream, endpoint, model, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("LLM HTTP 失败: protocol=openai, stream={}, endpoint={}, model={}, error={}",
                    stream, endpoint, model, e.getMessage());
            throw new AtelierException("LLM 调用失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readOpenAiStream(HttpURLConnection connection, String endpoint, String model, long startedAt)
            throws Exception {
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (status < 200 || status >= 300) {
            String err = readBody(stream);
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.warn("LLM HTTP 流式失败: status={}, elapsedMs={}, protocol=openai, endpoint={}, model={}, body={}",
                    status, elapsedMs, endpoint, model, truncate(err, 200));
            throw new AtelierException("LLM 请求失败: HTTP " + status + " — " + truncate(err, 200)
                    + "（请求: " + endpoint + ", model=" + model + ", protocol=openai, stream=true）");
        }
        String text = accumulateOpenAiSse(stream);
        long elapsedMs = System.currentTimeMillis() - startedAt;
        log.info("LLM HTTP 响应: status={}, elapsedMs={}, protocol=openai, stream=true, endpoint={}, model={}, contentLen={}",
                status, elapsedMs, endpoint, model, text.length());
        if (text.trim().isEmpty()) {
            throw new AtelierException("LLM 流式响应为空");
        }
        return text.trim();
    }

    private String readAnthropicStream(HttpURLConnection connection, String endpoint, String model, long startedAt)
            throws Exception {
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (status < 200 || status >= 300) {
            String err = readBody(stream);
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.warn("LLM HTTP 流式失败: status={}, elapsedMs={}, protocol=anthropic, endpoint={}, model={}, body={}",
                    status, elapsedMs, endpoint, model, truncate(err, 200));
            throw new AtelierException("LLM 请求失败: HTTP " + status + " — " + truncate(err, 200)
                    + "（请求: " + endpoint + ", model=" + model + ", protocol=anthropic, stream=true）");
        }
        String text = accumulateAnthropicSse(stream);
        long elapsedMs = System.currentTimeMillis() - startedAt;
        log.info("LLM HTTP 响应: status={}, elapsedMs={}, protocol=anthropic, stream=true, endpoint={}, model={}, contentLen={}",
                status, elapsedMs, endpoint, model, text.length());
        if (text.trim().isEmpty()) {
            throw new AtelierException("LLM 流式响应为空");
        }
        return text.trim();
    }

    /** 供单测：解析 OpenAI SSE data 行并拼接文本 */
    static String accumulateOpenAiSse(InputStream stream) throws Exception {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    continue;
                }
                appendOpenAiDelta(content, data);
            }
        }
        return content.toString();
    }

    /** 供单测：解析 Anthropic SSE 并拼接 text_delta */
    static String accumulateAnthropicSse(InputStream stream) throws Exception {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("event:")) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                appendAnthropicDelta(content, data);
            }
        }
        return content.toString();
    }

    static void appendOpenAiDelta(StringBuilder content, String dataJson) throws Exception {
        JsonNode root = MAPPER.readTree(dataJson);
        JsonNode delta = root.path("choices").path(0).path("delta");
        String piece = extractOpenAiContentText(delta.path("content"));
        if (piece == null || piece.isEmpty()) {
            piece = delta.path("text").asText("");
        }
        if (piece != null && !piece.isEmpty()) {
            content.append(piece);
        }
    }

    static void appendAnthropicDelta(StringBuilder content, String dataJson) throws Exception {
        JsonNode root = MAPPER.readTree(dataJson);
        String type = root.path("type").asText("");
        if ("content_block_delta".equals(type)) {
            JsonNode delta = root.path("delta");
            if ("text_delta".equals(delta.path("type").asText()) || delta.has("text")) {
                content.append(delta.path("text").asText(""));
            }
            return;
        }
        if ("content_block_start".equals(type)) {
            JsonNode block = root.path("content_block");
            if ("text".equals(block.path("type").asText()) && block.has("text")) {
                content.append(block.path("text").asText(""));
            }
            return;
        }
        if ("error".equals(type)) {
            throw new AtelierException("LLM 流式错误: " + truncate(root.path("error").toString(), 200));
        }
        // 部分网关在 /v1/messages 上仍推 OpenAI 风格 choices.delta
        if (root.path("choices").isArray() && root.path("choices").size() > 0) {
            appendOpenAiDelta(content, dataJson);
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
        log.info("LLM HTTP 响应: status={}, elapsedMs={}, protocol={}, stream=false, endpoint={}, model={}, contentLen={}",
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
        JsonNode choice0 = root.path("choices").path(0);
        JsonNode content = choice0.path("message").path("content");
        String text = extractOpenAiContentText(content);
        if (text == null || text.trim().isEmpty()) {
            text = choice0.path("message").path("text").asText("");
            if (text.trim().isEmpty()) {
                text = choice0.path("text").asText("");
            }
        }
        if (text == null || text.trim().isEmpty()) {
            String finish = choice0.path("finish_reason").asText("");
            String refusal = choice0.path("message").path("refusal").asText("");
            throw new AtelierException("LLM 响应为空"
                    + (finish.isEmpty() ? "" : "（finish_reason=" + finish + "）")
                    + (refusal.isEmpty() ? "" : " refusal=" + truncate(refusal, 120)));
        }
        return text.trim();
    }

    private static String extractOpenAiContentText(JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText("");
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                if (part == null) {
                    continue;
                }
                if (part.isTextual()) {
                    sb.append(part.asText(""));
                } else if ("text".equals(part.path("type").asText()) || part.has("text")) {
                    sb.append(part.path("text").asText(""));
                }
            }
            return sb.toString();
        }
        if (content.isObject() && content.has("text")) {
            return content.path("text").asText("");
        }
        return content.asText("");
    }

    private static SemanticLlmConfig copyConfig(SemanticLlmConfig config) {
        return SemanticLlmConfig.builder()
                .enabled(config.isEnabled())
                .provider(config.getProvider())
                .protocol(config.getProtocol())
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .model(config.getModel())
                .timeoutSeconds(config.getTimeoutSeconds())
                .build();
    }

    private static int timeoutSeconds(SemanticLlmConfig config) {
        return config.getTimeoutSeconds() != null && config.getTimeoutSeconds() > 0
                ? config.getTimeoutSeconds()
                : 30;
    }

    private static HttpURLConnection openConnection(String url, int timeoutSeconds, boolean stream) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(Math.min(30, timeoutSeconds) * 1000);
        // 流式：超时表示「两次读之间」的空闲上限；非流式：整包读超时
        connection.setReadTimeout(timeoutSeconds * 1000);
        if (stream) {
            connection.setRequestProperty("Accept", "text/event-stream");
        }
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
