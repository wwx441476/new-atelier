package com.example.atelier.document.llm;

import com.example.atelier.document.model.BlockType;
import com.example.atelier.document.model.DocumentBlock;
import com.example.atelier.document.model.DocumentModel;
import com.example.atelier.document.preview.PreviewBlock;
import com.example.atelier.document.preview.PreviewBlockType;
import com.example.atelier.document.preview.PreviewDocument;
import com.example.atelier.document.preview.PreviewInlineMark;
import com.example.atelier.document.preview.PreviewOptions;
import com.example.atelier.document.preview.PreviewRun;
import com.example.atelier.document.preview.PreviewStructureNormalizer;
import com.example.atelier.document.preview.PreviewTextNormalize;
import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.warning.evaluator.LlmChatClient;
import com.example.atelier.warning.service.SemanticLlmConfigLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * 预览保真闭环：对比当前预览块与原文（PDF 另附首页图），由 LLM 输出结构化修补并迭代。
 * 支持 PDF / DOCX / Markdown。
 */
@Service
public class LlmPreviewRefineService {

    private static final Logger log = LoggerFactory.getLogger(LlmPreviewRefineService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_ROUNDS = 10;
    private static final int MAX_BLOCKS_IN_PROMPT = 120;
    private static final int MAX_SOURCE_CHARS = 6000;
    private static final int MAX_PAGE_IMAGES = 2;
    private static final int REFINE_TIMEOUT_SECONDS = 120;
    private static final int REFINE_MAX_TOKENS = 1536;

    private static final String SYSTEM = "你是文档预览保真审校助手。"
            + "对比「当前预览块列表」与「原文摘录」（PDF 可能附带页面图），找出预览相对原文的结构问题。"
            + "适用于 PDF、Word(docx)、Markdown。"
            + "只输出 JSON 数组，不要解释、不要复述正文。"
            + "元素格式："
            + "{\"op\":\"DROP|MERGE|SET\",\"i\":块下标,"
            + "\"type\":\"HEADING|PARAGRAPH|LIST_ITEM\",\"level\":0,\"marks\":[\"BOLD\"]}。"
            + "DROP=删除该块；MERGE=将该块与下一块文本合并后删下一块；SET=只改 type/level/marks，不改文字。"
            + "常见问题：标题重复、软换行误切、列表标记错误、章条/# 标题 level 不对、docx 标题未识别。"
            + "禁止 DROP/MERGE/SET 表格(TABLE)、工作表(SHEET)、分区(SECTION)、图片(IMAGE)、代码/树图(CODE)。"
            + "若原文有图而预览缺少 IMAGE 块，在 warnings 思路上优先保留已有图片块，不要删除。"
            + "单列树图应是 CODE 而非 TABLE；不要把 CODE 改成 TABLE。"
            + "若已足够接近原文结构，输出 []。禁止编造原文没有的句子。";

    private final SemanticLlmConfigLoader configLoader;
    private final LlmChatClient chatClient = new LlmChatClient();
    private final PreviewStructureNormalizer normalizer = new PreviewStructureNormalizer();

    public LlmPreviewRefineService(SemanticLlmConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    public PreviewDocument refine(PreviewDocument document, DocumentModel source,
                                  Path pdfPath, PreviewOptions options) {
        return refine(document, source, pdfPath, options, null);
    }

    public PreviewDocument refine(PreviewDocument document, DocumentModel source,
                                  Path sourcePath, PreviewOptions options,
                                  BooleanSupplier cancelled) {
        if (document == null) {
            return null;
        }
        document = normalizer.normalize(document);
        String sourceType = document.getSourceType();
        if (!LlmPreviewFormats.supportsLlmEnhance(sourceType)) {
            return document;
        }
        if (options != null && !options.isEnableLlmRefine()) {
            return document;
        }

        SemanticLlmConfig config;
        try {
            config = configLoader.loadProfile(options == null ? null : options.getLlmProfileId());
            String reject = LlmConfigSupport.rejectReason(config);
            if (reject != null) {
                document.getWarnings().add(reject + "，跳过保真闭环");
                return document;
            }
            if (!config.isEnabled()) {
                config.setEnabled(true);
            }
            config.setTimeoutSeconds(REFINE_TIMEOUT_SECONDS);
        } catch (Exception e) {
            document.getWarnings().add("保真闭环读取 LLM 配置失败，已跳过");
            return document;
        }

        throwIfCancelled(cancelled);
        List<String> pageImages = LlmPreviewFormats.isPdf(sourceType)
                ? renderPageImages(sourcePath, MAX_PAGE_IMAGES)
                : Collections.emptyList();
        String sourceText = buildSourceDigest(source);
        int appliedOps = 0;
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            throwIfCancelled(cancelled);
            List<PreviewBlock> blocks = document.getBlocks();
            if (blocks == null || blocks.isEmpty()) {
                break;
            }
            String userPrompt = buildUserPrompt(round, sourceType, blocks, sourceText);
            String raw;
            try {
                raw = chatClient.chat(config, SYSTEM, userPrompt, pageImages, REFINE_MAX_TOKENS, true);
            } catch (Exception e) {
                throwIfCancelled(cancelled);
                log.warn("Preview refine round {} failed: {}", round, e.getMessage());
                document.getWarnings().add("保真闭环第 " + round + " 轮调用失败: " + e.getMessage());
                break;
            }
            List<JsonNode> ops = parseOps(raw);
            if (ops.isEmpty()) {
                if (round == 1) {
                    document.getWarnings().add("保真闭环：模型认为结构已可接受（无修补）");
                }
                break;
            }
            int n = applyOps(document, ops);
            appliedOps += n;
            document = normalizer.normalize(document);
            log.info("Preview refine round {} applied {} ops ({})", round, n, sourceType);
            if (n == 0) {
                break;
            }
        }
        if (appliedOps > 0) {
            document.getWarnings().add("保真闭环已自动修补 " + appliedOps + " 处（相对原文结构，格式="
                    + sourceType + "）");
        }
        return document;
    }

    private static void throwIfCancelled(BooleanSupplier cancelled) {
        if (cancelled != null && cancelled.getAsBoolean()) {
            throw new CancellationException("用户已停止预览");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("用户已停止预览");
        }
    }

    static String buildUserPrompt(int round, List<PreviewBlock> blocks, String sourceText) {
        return buildUserPrompt(round, "pdf", blocks, sourceText);
    }

    static String buildUserPrompt(int round, String sourceType, List<PreviewBlock> blocks, String sourceText) {
        StringBuilder sb = new StringBuilder();
        sb.append("轮次: ").append(round).append('/').append(MAX_ROUNDS).append('\n');
        sb.append("格式: ").append(sourceType == null ? "unknown" : sourceType).append('\n');
        int previewImages = 0;
        for (PreviewBlock b : blocks) {
            if (b != null && b.getType() == PreviewBlockType.IMAGE) {
                previewImages++;
            }
        }
        sb.append("预览中 IMAGE 块数: ").append(previewImages).append('\n');
        sb.append("原文摘录:\n").append(sourceText == null ? "" : sourceText).append("\n\n");
        sb.append("当前预览块（index|type|level|text）:\n");
        int n = Math.min(blocks.size(), MAX_BLOCKS_IN_PROMPT);
        for (int i = 0; i < n; i++) {
            PreviewBlock b = blocks.get(i);
            String text = PreviewTextNormalize.blockPlainText(b).replace('\n', ' ').trim();
            if (b.getType() == PreviewBlockType.IMAGE) {
                text = "[IMAGE]" + (text.isEmpty() ? "" : " " + text);
            }
            if (text.length() > 160) {
                text = text.substring(0, 160) + "…";
            }
            sb.append(i).append('|')
                    .append(b.getType() == null ? "PARAGRAPH" : b.getType().name()).append('|')
                    .append(b.getLevel()).append('|')
                    .append(text).append('\n');
        }
        if (blocks.size() > n) {
            sb.append("…共 ").append(blocks.size()).append(" 块，仅列出前 ").append(n).append('\n');
        }
        sb.append("\n只输出修补 JSON 数组。重点检查：标题是否重复、标题层级、列表是否被误切、图片是否缺失、空表是否多余。");
        return sb.toString();
    }

    static String buildSourceDigest(DocumentModel source) {
        if (source == null || source.getBlocks() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendBlocks(sb, source.getBlocks());
        String all = sb.toString().trim();
        if (all.length() > MAX_SOURCE_CHARS) {
            return all.substring(0, MAX_SOURCE_CHARS) + "…";
        }
        return all;
    }

    private static void appendBlocks(StringBuilder sb, List<DocumentBlock> blocks) {
        if (blocks == null) {
            return;
        }
        for (DocumentBlock b : blocks) {
            if (b == null) {
                continue;
            }
            if (b.getType() == BlockType.IMAGE) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("[IMAGE]");
                if (b.getText() != null && !b.getText().trim().isEmpty()) {
                    sb.append(' ').append(b.getText().trim());
                }
            } else if (b.getText() != null && !b.getText().trim().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(b.getText().trim());
            }
            appendBlocks(sb, b.getChildren());
            if (sb.length() >= MAX_SOURCE_CHARS) {
                return;
            }
        }
    }

    static List<JsonNode> parseOps(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String json = extractJsonArray(raw);
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isArray()) {
                return Collections.emptyList();
            }
            List<JsonNode> ops = new ArrayList<>();
            for (JsonNode n : root) {
                if (n != null && n.isObject() && n.has("op")) {
                    ops.add(n);
                }
            }
            return ops;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    static String extractJsonArray(String content) {
        String trimmed = content.trim();
        int start = -1;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) != '[') {
                continue;
            }
            int j = i + 1;
            while (j < trimmed.length() && Character.isWhitespace(trimmed.charAt(j))) {
                j++;
            }
            if (j < trimmed.length() && (trimmed.charAt(j) == '{' || trimmed.charAt(j) == ']')) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return "[]";
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
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
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return trimmed.substring(start, i + 1);
                }
            }
        }
        return trimmed.substring(start);
    }

    /**
     * 从后往前应用，避免下标漂移。
     * @return 实际生效的 op 数
     */
    static int applyOps(PreviewDocument document, List<JsonNode> ops) {
        if (document == null || document.getBlocks() == null || ops == null || ops.isEmpty()) {
            return 0;
        }
        List<PreviewBlock> blocks = new ArrayList<>(document.getBlocks());
        List<JsonNode> ordered = new ArrayList<>(ops);
        ordered.sort((a, b) -> Integer.compare(b.path("i").asInt(-1), a.path("i").asInt(-1)));
        int applied = 0;
        for (JsonNode opNode : ordered) {
            String op = opNode.path("op").asText("").trim().toUpperCase(Locale.ROOT);
            int i = opNode.path("i").asInt(-1);
            if (i < 0 || i >= blocks.size()) {
                continue;
            }
            if ("DROP".equals(op)) {
                if (isProtectedStructure(blocks.get(i))) {
                    continue;
                }
                blocks.remove(i);
                applied++;
            } else if ("MERGE".equals(op)) {
                if (i + 1 >= blocks.size()) {
                    continue;
                }
                PreviewBlock a = blocks.get(i);
                PreviewBlock b = blocks.get(i + 1);
                if (isProtectedStructure(a) || isProtectedStructure(b)) {
                    continue;
                }
                String merged = PreviewTextNormalize.blockPlainText(a)
                        + PreviewTextNormalize.blockPlainText(b);
                a.setText(merged);
                a.setRuns(Collections.singletonList(PreviewRun.builder().text(merged)
                        .marks(copyMarks(a)).build()));
                blocks.remove(i + 1);
                applied++;
            } else if ("SET".equals(op)) {
                PreviewBlock target = blocks.get(i);
                if (isProtectedStructure(target)) {
                    continue;
                }
                if (opNode.has("type")) {
                    try {
                        target.setType(PreviewBlockType.valueOf(
                                opNode.path("type").asText("PARAGRAPH").trim().toUpperCase(Locale.ROOT)));
                    } catch (Exception ignored) {
                        // keep
                    }
                }
                if (opNode.has("level")) {
                    target.setLevel(opNode.path("level").asInt(target.getLevel()));
                }
                if (target.getType() == PreviewBlockType.HEADING && target.getLevel() <= 0) {
                    target.setLevel(1);
                }
                if (target.getType() != PreviewBlockType.HEADING) {
                    target.setLevel(0);
                }
                List<PreviewInlineMark> marks = readMarks(opNode);
                String text = PreviewTextNormalize.blockPlainText(target);
                target.setText(text);
                target.setRuns(Collections.singletonList(
                        PreviewRun.builder().text(text).marks(marks).build()));
                applied++;
            }
        }
        document.setBlocks(blocks);
        return applied;
    }

    private static boolean isProtectedStructure(PreviewBlock block) {
        if (block == null || block.getType() == null) {
            return false;
        }
        PreviewBlockType t = block.getType();
        return t == PreviewBlockType.TABLE
                || t == PreviewBlockType.SHEET
                || t == PreviewBlockType.SECTION
                || t == PreviewBlockType.IMAGE
                || t == PreviewBlockType.CODE;
    }

    private static List<PreviewInlineMark> copyMarks(PreviewBlock block) {
        List<PreviewInlineMark> marks = new ArrayList<>();
        if (block.getRuns() != null) {
            for (PreviewRun run : block.getRuns()) {
                if (run.getMarks() != null) {
                    marks.addAll(run.getMarks());
                }
            }
        }
        return marks;
    }

    private static List<PreviewInlineMark> readMarks(JsonNode opNode) {
        List<PreviewInlineMark> marks = new ArrayList<>();
        JsonNode arr = opNode.get("marks");
        if (arr != null && arr.isArray()) {
            for (JsonNode m : arr) {
                String v = m.asText("").toUpperCase(Locale.ROOT);
                if ("BOLD".equals(v)) {
                    marks.add(PreviewInlineMark.BOLD);
                } else if ("ITALIC".equals(v)) {
                    marks.add(PreviewInlineMark.ITALIC);
                }
            }
        }
        return marks;
    }

    private static List<String> renderPageImages(Path pdfPath, int maxPages) {
        if (pdfPath == null || !pdfPath.toFile().isFile()) {
            return Collections.emptyList();
        }
        List<String> images = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int n = Math.min(document.getNumberOfPages(), maxPages);
            for (int i = 0; i < n; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 96, ImageType.RGB);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                images.add("data:image/png;base64," + b64);
            }
        } catch (Exception e) {
            log.warn("Refine page render failed: {}", e.getMessage());
        }
        return images;
    }
}
