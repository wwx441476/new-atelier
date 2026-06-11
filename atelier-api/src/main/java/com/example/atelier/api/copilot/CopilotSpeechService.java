package com.example.atelier.api.copilot;

import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.warning.evaluator.SemanticLlmProviders;
import com.example.atelier.warning.service.SemanticLlmConfigLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class CopilotSpeechService {

    private static final Logger log = LoggerFactory.getLogger(CopilotSpeechService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SemanticLlmConfigLoader configLoader;

    public CopilotSpeechService(SemanticLlmConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    public String transcribe(String audioDataUrl, String llmProfileId) {
        if (audioDataUrl == null || audioDataUrl.trim().isEmpty()) {
            throw new AtelierException("音频内容为空");
        }
        SemanticLlmConfig config = llmProfileId != null && !llmProfileId.trim().isEmpty()
                ? configLoader.loadProfile(llmProfileId.trim())
                : configLoader.load();
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new AtelierException("请先在「语义检测设置」中配置 API Key");
        }
        SemanticLlmProviders.applyProviderDefaults(config);
        String provider = config.getProvider() != null ? config.getProvider().trim().toLowerCase() : "";
        if (SemanticLlmProviders.DASHSCOPE.equals(provider)
                || isDashScopeCompatible(config.getBaseUrl())) {
            return transcribeWithQwenAsr(config, audioDataUrl.trim());
        }
        if (SemanticLlmProviders.OPENAI.equals(provider)) {
            return transcribeWithOpenAiWhisper(config, audioDataUrl.trim());
        }
        throw new AtelierException("当前 LLM 配置不支持语音转写，请使用通义千问或 OpenAI 配置");
    }

    private String transcribeWithQwenAsr(SemanticLlmConfig config, String audioDataUrl) {
        String endpoint = normalizeCompatibleEndpoint(config.getBaseUrl()) + "/chat/completions";
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", "qwen3-asr-flash");
            ArrayNode messages = body.putArray("messages");
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            ArrayNode content = user.putArray("content");
            ObjectNode audioContent = content.addObject();
            audioContent.put("type", "input_audio");
            audioContent.putObject("input_audio").put("data", audioDataUrl);
            ObjectNode asrOptions = body.putObject("asr_options");
            asrOptions.put("enable_itn", false);
            asrOptions.put("language", "zh");

            String response = postJson(endpoint, config.getApiKey().trim(), body);
            JsonNode root = MAPPER.readTree(response);
            String text = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (text.isEmpty()) {
                throw new AtelierException("未识别到语音内容，请靠近麦克风重试");
            }
            return text;
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Qwen ASR 失败: {}", e.getMessage());
            throw new AtelierException("语音转写失败: " + e.getMessage(), e);
        }
    }

    private String transcribeWithOpenAiWhisper(SemanticLlmConfig config, String audioDataUrl) {
        try {
            ParsedDataUrl parsed = parseDataUrl(audioDataUrl);
            byte[] audioBytes = Base64.getDecoder().decode(parsed.base64Data);
            if (audioBytes.length == 0) {
                throw new AtelierException("音频内容为空");
            }
            String endpoint = normalizeCompatibleEndpoint(config.getBaseUrl()) + "/audio/transcriptions";
            String boundary = "----AtelierBoundary" + System.currentTimeMillis();
            HttpURLConnection connection = openConnection(endpoint, config);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (OutputStream out = connection.getOutputStream()) {
                writeMultipartField(out, boundary, "model", "whisper-1");
                writeMultipartFile(out, boundary, "file", "speech." + parsed.extension, parsed.mimeType, audioBytes);
                out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }

            String response = readResponse(connection);
            JsonNode root = MAPPER.readTree(response);
            String text = root.path("text").asText("").trim();
            if (text.isEmpty()) {
                throw new AtelierException("未识别到语音内容，请靠近麦克风重试");
            }
            return text;
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Whisper 转写失败: {}", e.getMessage());
            throw new AtelierException("语音转写失败: " + e.getMessage(), e);
        }
    }

    private static boolean isDashScopeCompatible(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase().contains("dashscope.aliyuncs.com");
    }

    private static String normalizeCompatibleEndpoint(String baseUrl) {
        String url = baseUrl != null ? baseUrl.trim() : "https://dashscope.aliyuncs.com/compatible-mode/v1";
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.endsWith("/v1")) {
            url = url + "/v1";
        }
        return url;
    }

    private String postJson(String endpoint, String apiKey, ObjectNode body) throws Exception {
        byte[] payload = MAPPER.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setDoOutput(true);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(payload);
        }
        return readResponse(connection);
    }

    private HttpURLConnection openConnection(String endpoint, SemanticLlmConfig config) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        int timeout = config.getTimeoutSeconds() != null && config.getTimeoutSeconds() > 0
                ? config.getTimeoutSeconds()
                : 60;
        connection.setConnectTimeout(timeout * 1000);
        connection.setReadTimeout(timeout * 1000);
        connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey().trim());
        connection.setDoOutput(true);
        return connection;
    }

    private static String readResponse(HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readBody(stream);
        if (status < 200 || status >= 300) {
            throw new AtelierException("语音转写请求失败: HTTP " + status + " — " + truncate(body, 200));
        }
        return body;
    }

    private static void writeMultipartField(OutputStream out, String boundary, String name, String value)
            throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static void writeMultipartFile(OutputStream out, String boundary, String fieldName, String filename,
            String mimeType, byte[] bytes) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static ParsedDataUrl parseDataUrl(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        if (comma < 0 || !dataUrl.startsWith("data:")) {
            throw new AtelierException("无效的音频格式");
        }
        String header = dataUrl.substring(5, comma);
        if (!header.contains("base64")) {
            throw new AtelierException("仅支持 base64 编码的音频");
        }
        String mimeType = header.substring(0, header.indexOf(";base64"));
        String base64Data = dataUrl.substring(comma + 1).trim();
        String extension = "webm";
        if (mimeType.contains("wav")) {
            extension = "wav";
        } else if (mimeType.contains("mpeg") || mimeType.contains("mp3")) {
            extension = "mp3";
        } else if (mimeType.contains("ogg")) {
            extension = "ogg";
        }
        return new ParsedDataUrl(mimeType, base64Data, extension);
    }

    private static String readBody(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
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

    private static final class ParsedDataUrl {
        private final String mimeType;
        private final String base64Data;
        private final String extension;

        private ParsedDataUrl(String mimeType, String base64Data, String extension) {
            this.mimeType = mimeType;
            this.base64Data = base64Data;
            this.extension = extension;
        }
    }
}
