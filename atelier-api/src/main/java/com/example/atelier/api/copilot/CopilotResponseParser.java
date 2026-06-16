package com.example.atelier.api.copilot;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 Copilot LLM 响应，尽量从非标准/截断 JSON 中提取 reply 与 actions。
 */
public final class CopilotResponseParser {

    private static final Logger log = LoggerFactory.getLogger(CopilotResponseParser.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build();

    private static final Pattern REPLY_PATTERN =
            Pattern.compile("\"reply\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);

    private CopilotResponseParser() {
    }

    public static CopilotParsedResponse parse(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new CopilotParsedResponse("已完成。", new ArrayList<JsonNode>());
        }
        String trimmed = unwrapMarkdownCodeFence(content.trim());
        for (String candidate : buildCandidates(trimmed)) {
            try {
                JsonNode root = MAPPER.readTree(candidate);
                return buildFromRoot(root);
            } catch (Exception ignored) {
                // try next candidate
            }
        }
        return recoverPartial(trimmed);
    }

    private static List<String> buildCandidates(String trimmed) {
        List<String> candidates = new ArrayList<>();
        candidates.add(trimmed);
        candidates.add(com.example.atelier.warning.evaluator.LlmChatClient.extractJsonObject(trimmed));
        candidates.add(repairTruncatedJson(trimmed));
        candidates.add(repairTruncatedJson(
                com.example.atelier.warning.evaluator.LlmChatClient.extractJsonObject(trimmed)));
        return candidates;
    }

    private static CopilotParsedResponse buildFromRoot(JsonNode root) {
        String reply = root.path("reply").asText("").trim();
        if (reply.isEmpty()) {
            throw new IllegalArgumentException("reply 为空");
        }
        List<JsonNode> actions = new ArrayList<>();
        JsonNode actionsNode = root.path("actions");
        if (actionsNode.isArray()) {
            for (JsonNode node : actionsNode) {
                actions.add(node);
            }
        }
        return new CopilotParsedResponse(reply, actions);
    }

    private static CopilotParsedResponse recoverPartial(String trimmed) {
        String reply = extractReplyText(trimmed);
        List<JsonNode> actions = extractActions(trimmed);
        if (reply == null || reply.isEmpty()) {
            reply = actions.isEmpty()
                    ? "助手响应格式异常，请简化描述后重试，或点击「继续」。"
                    : "已解析配置方案，正在执行下方操作…";
        }
        log.warn("Copilot 响应采用容错解析: replyLen={}, actions={}", reply.length(), actions.size());
        return new CopilotParsedResponse(reply, actions);
    }

    private static String extractReplyText(String content) {
        Matcher matcher = REPLY_PATTERN.matcher(content);
        if (matcher.find()) {
            return unescapeJsonString(matcher.group(1));
        }
        return null;
    }

    private static List<JsonNode> extractActions(String content) {
        List<JsonNode> actions = new ArrayList<>();
        int actionsIndex = content.indexOf("\"actions\"");
        if (actionsIndex < 0) {
            return actions;
        }
        int arrayStart = content.indexOf('[', actionsIndex);
        if (arrayStart < 0) {
            return actions;
        }
        String arrayPart = extractBalancedArray(content, arrayStart);
        if (arrayPart == null) {
            arrayPart = repairTruncatedJson(content.substring(arrayStart));
            if (arrayPart.startsWith("[")) {
                // already array fragment
            } else {
                arrayPart = "[" + arrayPart;
            }
        }
        try {
            JsonNode node = MAPPER.readTree(arrayPart);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    actions.add(item);
                }
            }
        } catch (Exception e) {
            log.debug("actions 数组容错解析失败: {}", e.getMessage());
        }
        return actions;
    }

    private static String extractBalancedArray(String content, int start) {
        int depth = 0;
        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return content.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static String repairTruncatedJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        StringBuilder builder = new StringBuilder(json.trim());
        int brace = 0;
        int bracket = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < builder.length(); i++) {
            char c = builder.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                brace++;
            } else if (c == '}') {
                brace--;
            } else if (c == '[') {
                bracket++;
            } else if (c == ']') {
                bracket--;
            }
        }
        if (inString) {
            builder.append('"');
        }
        while (bracket > 0) {
            builder.append(']');
            bracket--;
        }
        while (brace > 0) {
            builder.append('}');
            brace--;
        }
        return builder.toString();
    }

    private static String unescapeJsonString(String value) {
        if (value == null) {
            return "";
        }
        try {
            return MAPPER.readValue("\"" + value + "\"", String.class);
        } catch (Exception e) {
            return value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"");
        }
    }

    private static String unwrapMarkdownCodeFence(String content) {
        if (!content.startsWith("```")) {
            return content;
        }
        int firstLineEnd = content.indexOf('\n');
        if (firstLineEnd < 0) {
            return content;
        }
        String body = content.substring(firstLineEnd + 1);
        if (body.endsWith("```")) {
            body = body.substring(0, body.length() - 3);
        }
        return body.trim();
    }

    public static final class CopilotParsedResponse {
        private final String reply;
        private final List<JsonNode> actions;

        public CopilotParsedResponse(String reply, List<JsonNode> actions) {
            this.reply = reply;
            this.actions = actions;
        }

        public String getReply() {
            return reply;
        }

        public List<JsonNode> getActions() {
            return actions;
        }
    }
}
