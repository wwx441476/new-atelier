package com.example.atelier.document.preview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将 LLM 返回的「段落索引标注」应用到原文块上，不改写正文，保证保真。
 * 兼容 i/index/idx、对象包装、截断 JSON salvage、以及无索引时按数组顺序落点。
 */
public class StyleAnnotationApplier {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * @param annotations JSON 数组或带 annotations/items/blocks 的对象
     * @return 标注后的块列表；完全无法应用时返回 null
     */
    public List<PreviewBlock> apply(List<PreviewBlock> originals, JsonNode annotations) {
        if (originals == null || originals.isEmpty() || annotations == null || annotations.isNull()) {
            return null;
        }
        JsonNode arr = unwrapArray(annotations);
        if (arr == null || !arr.isArray() || arr.size() == 0) {
            return null;
        }
        return applyArray(originals, arr);
    }

    /**
     * 从可能截断/夹杂的模型原文中抢救标注对象（流式丢头时常见）。
     */
    public List<PreviewBlock> applyFromRaw(List<PreviewBlock> originals, String raw) {
        if (originals == null || originals.isEmpty() || raw == null || raw.trim().isEmpty()) {
            return null;
        }
        // 1) 正常 JSON
        try {
            JsonNode root = MAPPER.readTree(extractAnnotationJson(raw));
            List<PreviewBlock> applied = apply(originals, root);
            if (applied != null) {
                return applied;
            }
        } catch (Exception ignored) {
            // fall through to salvage
        }
        // 2) 从文本中扫描 { ... } 对象
        ArrayNode salvaged = salvageAnnotationObjects(raw);
        if (salvaged.size() == 0) {
            return null;
        }
        return applyArray(originals, salvaged);
    }

    private List<PreviewBlock> applyArray(List<PreviewBlock> originals, JsonNode arr) {
        List<PreviewBlock> out = new ArrayList<>(originals.size());
        for (PreviewBlock src : originals) {
            out.add(copyOf(src));
        }
        int applied = applyWithIndex(out, arr);
        if (applied == 0) {
            applied = applyPositional(out, arr);
        }
        if (applied == 0) {
            return null;
        }
        return out;
    }

    private int applyWithIndex(List<PreviewBlock> out, JsonNode arr) {
        int applied = 0;
        Set<Integer> seen = new HashSet<>();
        for (JsonNode node : arr) {
            if (node == null || !node.isObject()) {
                continue;
            }
            Integer index = readIndex(node);
            if (index == null || index < 0 || index >= out.size() || !seen.add(index)) {
                continue;
            }
            decorate(out.get(index), node);
            applied++;
        }
        return applied;
    }

    private int applyPositional(List<PreviewBlock> out, JsonNode arr) {
        int applied = 0;
        int n = Math.min(out.size(), arr.size());
        for (int i = 0; i < n; i++) {
            JsonNode node = arr.get(i);
            if (node == null || !node.isObject()) {
                continue;
            }
            if (!node.has("type") && !node.has("level") && !node.has("marks") && readIndex(node) == null) {
                continue;
            }
            decorate(out.get(i), node);
            applied++;
        }
        return applied;
    }

    private static void decorate(PreviewBlock target, JsonNode node) {
        String rawType = node.path("type").asText("PARAGRAPH").trim();
        String typeStr = rawType.toUpperCase(Locale.ROOT);
        if ("标题".equals(rawType) || "TITLE".equals(typeStr)) {
            typeStr = "HEADING";
        } else if ("正文".equals(rawType) || "BODY".equals(typeStr) || "TEXT".equals(typeStr)) {
            typeStr = "PARAGRAPH";
        } else if ("列表".equals(rawType) || "LIST".equals(typeStr)) {
            typeStr = "LIST_ITEM";
        }
        PreviewBlockType type;
        try {
            type = PreviewBlockType.valueOf(typeStr);
        } catch (Exception e) {
            type = PreviewBlockType.PARAGRAPH;
        }
        if (type != PreviewBlockType.HEADING
                && type != PreviewBlockType.PARAGRAPH
                && type != PreviewBlockType.LIST_ITEM) {
            type = PreviewBlockType.PARAGRAPH;
        }
        int level = node.path("level").asInt(0);
        if (type == PreviewBlockType.HEADING && level <= 0) {
            level = 1;
        }
        if (type != PreviewBlockType.HEADING) {
            level = 0;
        }
        List<PreviewInlineMark> marks = new ArrayList<>();
        JsonNode marksNode = node.get("marks");
        if (marksNode != null && marksNode.isArray()) {
            for (JsonNode m : marksNode) {
                String mark = m.asText("").toUpperCase(Locale.ROOT);
                if ("BOLD".equals(mark) || "加粗".equals(m.asText(""))) {
                    marks.add(PreviewInlineMark.BOLD);
                } else if ("ITALIC".equals(mark) || "斜体".equals(m.asText(""))) {
                    marks.add(PreviewInlineMark.ITALIC);
                }
            }
        } else if (node.path("bold").asBoolean(false)) {
            marks.add(PreviewInlineMark.BOLD);
        }
        if (node.path("italic").asBoolean(false)) {
            marks.add(PreviewInlineMark.ITALIC);
        }
        String text = PreviewTextNormalize.blockPlainText(target);
        target.setType(type);
        target.setLevel(level);
        target.setText(text);
        target.setRuns(Collections.singletonList(
                PreviewRun.builder().text(text).marks(marks).build()));
    }

    static Integer readIndex(JsonNode node) {
        for (String key : new String[]{"i", "index", "idx", "paragraphIndex", "paragraph_index"}) {
            if (node.has(key) && !node.get(key).isNull()) {
                JsonNode v = node.get(key);
                if (v.isInt() || v.isLong() || v.isNumber()) {
                    return v.asInt();
                }
                if (v.isTextual()) {
                    try {
                        return Integer.parseInt(v.asText().trim());
                    } catch (NumberFormatException ignored) {
                        // continue
                    }
                }
            }
        }
        return null;
    }

    static JsonNode unwrapArray(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            return node;
        }
        if (node.isObject()) {
            for (String key : new String[]{"annotations", "items", "blocks", "result", "data", "styles"}) {
                JsonNode child = node.get(key);
                if (child != null && child.isArray()) {
                    return child;
                }
            }
            if (readIndex(node) != null || node.has("type")) {
                ArrayNode arr = JsonNodeFactory.instance.arrayNode();
                arr.add(node);
                return arr;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                JsonNode child = fields.next().getValue();
                if (child != null && child.isArray()) {
                    return child;
                }
            }
        }
        return null;
    }

    /**
     * 提取标注 JSON：优先 {@code [{...}]}（跳过 marks:[] 这类内部方括号）。
     */
    public static String extractAnnotationJson(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "[]";
        }
        String trimmed = content.trim();
        int bestStart = -1;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) != '[') {
                continue;
            }
            int j = i + 1;
            while (j < trimmed.length() && Character.isWhitespace(trimmed.charAt(j))) {
                j++;
            }
            // 真正的标注数组以 [{ 开头，而不是 marks:[]
            if (j < trimmed.length() && trimmed.charAt(j) == '{') {
                bestStart = i;
                break;
            }
        }
        if (bestStart >= 0) {
            int end = findMatchingBracket(trimmed, bestStart, '[', ']');
            if (end > bestStart) {
                return trimmed.substring(bestStart, end + 1);
            }
        }
        int objStart = trimmed.indexOf('{');
        int objEnd = trimmed.lastIndexOf('}');
        if (objStart >= 0 && objEnd > objStart) {
            return trimmed.substring(objStart, objEnd + 1);
        }
        return trimmed;
    }

    static ArrayNode salvageAnnotationObjects(String content) {
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        if (content == null) {
            return arr;
        }
        int pos = 0;
        while (pos < content.length()) {
            int brace = content.indexOf('{', pos);
            if (brace < 0) {
                break;
            }
            int end = findMatchingBracket(content, brace, '{', '}');
            if (end < 0) {
                pos = brace + 1;
                continue;
            }
            String slice = content.substring(brace, end + 1);
            try {
                JsonNode node = MAPPER.readTree(slice);
                if (node.isObject() && (readIndex(node) != null || node.has("type"))) {
                    // 跳过只有 marks 的 run 碎片
                    if (readIndex(node) != null || node.has("level") || isStyleType(node.path("type").asText())) {
                        arr.add(node);
                    }
                }
            } catch (Exception ignored) {
                // skip invalid slice
            }
            pos = end + 1;
        }
        return arr;
    }

    private static boolean isStyleType(String type) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        String t = type.trim().toUpperCase(Locale.ROOT);
        return "HEADING".equals(t) || "PARAGRAPH".equals(t) || "LIST_ITEM".equals(t)
                || "标题".equals(type) || "正文".equals(type) || "列表".equals(type);
    }

    static int findMatchingBracket(String s, int openIdx, char open, char close) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static PreviewBlock copyOf(PreviewBlock src) {
        return PreviewBlock.builder()
                .id(src.getId())
                .type(src.getType())
                .level(src.getLevel())
                .text(src.getText())
                .runs(src.getRuns() == null ? new ArrayList<>() : new ArrayList<>(src.getRuns()))
                .anchor(src.getAnchor())
                .table(src.getTable())
                .imageDataUrl(src.getImageDataUrl())
                .meta(src.getMeta())
                .build();
    }
}
