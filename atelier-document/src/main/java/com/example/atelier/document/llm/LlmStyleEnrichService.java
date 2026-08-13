package com.example.atelier.document.llm;

import com.example.atelier.document.preview.HeuristicStyleEnricher;
import com.example.atelier.document.preview.PreviewBlock;
import com.example.atelier.document.preview.PreviewBlockType;
import com.example.atelier.document.preview.PreviewDocument;
import com.example.atelier.document.preview.PreviewOptions;
import com.example.atelier.document.preview.PreviewTextNormalize;
import com.example.atelier.document.preview.StyleAnnotationApplier;
import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.warning.evaluator.LlmChatClient;
import com.example.atelier.warning.service.SemanticLlmConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * 预览样式增强：按页/分片调用 LLM，对「段落索引」标注 type/level/marks，
 * 正文始终取自原文。支持 PDF / DOCX / Markdown。
 * 流式优先，失败则非流式重试 + salvage 截断 JSON。
 */
@Service
public class LlmStyleEnrichService {

    private static final Logger log = LoggerFactory.getLogger(LlmStyleEnrichService.class);
    private static final int MAX_CHUNKS_TO_STYLE = 30;
    private static final int MAX_BLOCKS_PER_CHUNK = 80;
    private static final int STYLE_TIMEOUT_SECONDS = 180;
    private static final int STYLE_MAX_TOKENS = 1024;
    private static final String SYSTEM = "你是文档结构标注助手。"
            + "输入是带行号的段落列表（格式：index|text）。"
            + "只输出 JSON 数组，不要解释、不要复述正文。"
            + "每个元素：{\"i\":段落index,\"type\":\"HEADING|PARAGRAPH|LIST_ITEM\",\"level\":0,\"marks\":[\"BOLD\"|\"ITALIC\"]}。"
            + "HEADING 的 level 取 1-6；章标题 level=1，节 level=2，条款标题 level=3；Markdown # 对应 level；正文用 PARAGRAPH 且 level=0。"
            + "marks 可为空；章/标题可加 BOLD。禁止改写或输出原文内容。";

    private final SemanticLlmConfigLoader configLoader;
    private final LlmChatClient chatClient = new LlmChatClient();
    private final StyleAnnotationApplier annotationApplier = new StyleAnnotationApplier();
    private final HeuristicStyleEnricher heuristicEnricher = new HeuristicStyleEnricher();

    public LlmStyleEnrichService(SemanticLlmConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    public PreviewDocument enrich(PreviewDocument document, PreviewOptions options) {
        return enrich(document, options, null);
    }

    public PreviewDocument enrich(PreviewDocument document, PreviewOptions options,
                                  BooleanSupplier cancelled) {
        if (document == null) {
            return null;
        }
        document.setStyleMode("canonical");
        String sourceType = document.getSourceType();
        if (!LlmPreviewFormats.supportsLlmEnhance(sourceType)) {
            return document;
        }
        if (options != null && !options.isEnableLlmStyle()) {
            document.getWarnings().add("已关闭 LLM 样式增强");
            return document;
        }

        SemanticLlmConfig config = null;
        boolean llmReady = false;
        try {
            config = configLoader.loadProfile(options == null ? null : options.getLlmProfileId());
            String reject = LlmConfigSupport.rejectReason(config);
            if (reject != null) {
                document.getWarnings().add(reject + "，" + sourceType + " 样式改用规则启发式");
            } else {
                if (!config.isEnabled()) {
                    config.setEnabled(true);
                }
                config.setTimeoutSeconds(STYLE_TIMEOUT_SECONDS);
                llmReady = true;
                String protocol = config.getProtocol() == null ? "auto" : config.getProtocol();
                document.getWarnings().add("LLM 样式调用：流式 SSE，协议=" + protocol
                        + "，格式=" + sourceType
                        + "（严格按档案 protocol；custom 空 protocol 默认 anthropic）");
            }
        } catch (Exception e) {
            document.getWarnings().add("读取 LLM 配置失败，" + sourceType + " 样式改用规则启发式");
        }

        List<List<PreviewBlock>> chunks = toStyleChunks(document);
        if (chunks.isEmpty()) {
            // 可能全是表格等非样式块，保持原样
            return document;
        }

        int styledChunks = 0;
        boolean anyLlm = false;
        boolean anyHeuristic = false;
        List<PreviewBlock> styledStyleable = new ArrayList<>();

        for (int c = 0; c < chunks.size(); c++) {
            throwIfCancelled(cancelled);
            List<PreviewBlock> originals = chunks.get(c);
            if (styledChunks >= MAX_CHUNKS_TO_STYLE) {
                styledStyleable.addAll(heuristicEnricher.enrichPage(originals));
                anyHeuristic = true;
                continue;
            }
            List<PreviewBlock> styled = null;
            if (llmReady && config != null) {
                try {
                    styled = stylePageWithLlm(config, c + 1, originals);
                    if (styled != null) {
                        anyLlm = true;
                    }
                } catch (CancellationException e) {
                    throw e;
                } catch (Exception e) {
                    log.warn("LLM style chunk {} failed: {}", c + 1, e.getMessage());
                    document.getWarnings().add("第 " + (c + 1) + " 片 LLM 样式失败: "
                            + e.getMessage() + "，已用规则启发式");
                }
            }
            if (styled == null) {
                styled = heuristicEnricher.enrichPage(originals);
                anyHeuristic = true;
            }
            styledStyleable.addAll(styled);
            styledChunks++;
        }

        if (chunks.size() > MAX_CHUNKS_TO_STYLE) {
            document.getWarnings().add("样式增强仅处理前 " + MAX_CHUNKS_TO_STYLE + " 片");
        }
        // 按原文顺序回填，保留 TABLE / SECTION / SHEET 等未参与样式标注的块
        document.setBlocks(mergePreservingNonStyleable(document.getBlocks(), styledStyleable));
        document.setLlmStyleUsed(anyLlm);
        if (anyLlm) {
            document.getWarnings().add("已应用 LLM 段落标注样式（标题/加粗/斜体；正文保真）");
        } else if (anyHeuristic) {
            document.getWarnings().add("已应用规则启发式样式（章/节/条 → 标题）");
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

    private List<PreviewBlock> stylePageWithLlm(SemanticLlmConfig config, int page,
                                               List<PreviewBlock> originals) throws Exception {
        String numbered = buildNumberedPrompt(originals);
        if (PreviewTextNormalize.normalize(numbered).isEmpty()) {
            return null;
        }
        String userPrompt = "分片: " + page + "\n共 " + originals.size() + " 段，index 从 0 到 "
                + (originals.size() - 1) + "。\n" + numbered
                + "\n只输出 JSON 数组，例如："
                + "[{\"i\":0,\"type\":\"HEADING\",\"level\":1,\"marks\":[\"BOLD\"]},{\"i\":1,\"type\":\"PARAGRAPH\",\"level\":0,\"marks\":[]}]。"
                + "必须用字段 i（或 index）指向段落号；不要复述正文。";

        // 先流式；若网关丢 SSE 开头导致 JSON 残缺，再非流式重试一次（标注体积小，通常更快更稳）
        String content = chatClient.chat(config, SYSTEM, userPrompt, null, STYLE_MAX_TOKENS, true);
        List<PreviewBlock> applied = annotationApplier.applyFromRaw(originals, content);
        if (applied == null) {
            log.warn("LLM style page {} stream apply incomplete, paragraphs={}, rawHead={}, retry non-stream",
                    page, originals.size(), truncate(content, 160));
            content = chatClient.chat(config, SYSTEM, userPrompt, null, STYLE_MAX_TOKENS, false);
            applied = annotationApplier.applyFromRaw(originals, content);
        }
        if (applied == null) {
            log.warn("LLM style page {} apply failed, paragraphs={}, raw={}",
                    page, originals.size(), truncate(content, 360));
            throw new IllegalStateException("LLM 标注无法应用到原文段落（返回格式无可用 i/index 或 type）");
        }
        return applied;
    }

    /** @deprecated 使用 {@link StyleAnnotationApplier#extractAnnotationJson(String)} */
    static String extractJson(String content) {
        return StyleAnnotationApplier.extractAnnotationJson(content);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\n', ' ');
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    static String buildNumberedPrompt(List<PreviewBlock> blocks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            String text = PreviewTextNormalize.blockPlainText(blocks.get(i))
                    .replace('\n', ' ')
                    .trim();
            if (text.length() > 200) {
                text = text.substring(0, 200) + "…";
            }
            sb.append(i).append('|').append(text).append('\n');
        }
        return sb.toString();
    }

    /**
     * PDF 按真实页分组；docx/md 无页概念，按块数分片，避免长文只标前 80 段。
     */
    static List<List<PreviewBlock>> toStyleChunks(PreviewDocument document) {
        List<PreviewBlock> styleable = collectStyleable(document == null ? null : document.getBlocks());
        if (styleable.isEmpty()) {
            return Collections.emptyList();
        }
        if (LlmPreviewFormats.isPdf(document.getSourceType())) {
            Map<Integer, List<PreviewBlock>> byPage = new LinkedHashMap<>();
            for (PreviewBlock block : styleable) {
                int page = 1;
                if (block.getMeta() != null && block.getMeta().getPage() != null) {
                    page = block.getMeta().getPage();
                }
                byPage.computeIfAbsent(page, p -> new ArrayList<>()).add(block);
            }
            List<List<PreviewBlock>> chunks = new ArrayList<>();
            for (List<PreviewBlock> pageBlocks : byPage.values()) {
                for (int i = 0; i < pageBlocks.size(); i += MAX_BLOCKS_PER_CHUNK) {
                    chunks.add(new ArrayList<>(pageBlocks.subList(
                            i, Math.min(i + MAX_BLOCKS_PER_CHUNK, pageBlocks.size()))));
                }
            }
            return chunks;
        }
        List<List<PreviewBlock>> chunks = new ArrayList<>();
        for (int i = 0; i < styleable.size(); i += MAX_BLOCKS_PER_CHUNK) {
            chunks.add(new ArrayList<>(styleable.subList(
                    i, Math.min(i + MAX_BLOCKS_PER_CHUNK, styleable.size()))));
        }
        return chunks;
    }

    private static List<PreviewBlock> collectStyleable(List<PreviewBlock> blocks) {
        List<PreviewBlock> out = new ArrayList<>();
        if (blocks == null) {
            return out;
        }
        for (PreviewBlock block : blocks) {
            if (isStyleable(block)) {
                out.add(block);
            }
        }
        return out;
    }

    static boolean isStyleable(PreviewBlock block) {
        if (block == null || block.getType() == null) {
            return false;
        }
        PreviewBlockType t = block.getType();
        return t != PreviewBlockType.SECTION
                && t != PreviewBlockType.TABLE
                && t != PreviewBlockType.SHEET
                && t != PreviewBlockType.IMAGE
                && t != PreviewBlockType.CODE;
    }

    /**
     * 将样式结果按原文顺序合并回去，避免丢掉表格等非样式块。
     */
    static List<PreviewBlock> mergePreservingNonStyleable(List<PreviewBlock> original,
                                                          List<PreviewBlock> styledStyleable) {
        if (original == null || original.isEmpty()) {
            return styledStyleable == null ? new ArrayList<>() : new ArrayList<>(styledStyleable);
        }
        List<PreviewBlock> styled = styledStyleable == null
                ? Collections.emptyList()
                : styledStyleable;
        List<PreviewBlock> out = new ArrayList<>(original.size());
        int si = 0;
        for (PreviewBlock block : original) {
            if (isStyleable(block)) {
                if (si < styled.size()) {
                    out.add(styled.get(si++));
                } else {
                    out.add(block);
                }
            } else {
                out.add(block);
            }
        }
        // 防御：样式结果多于可样式块时追加（不应发生）
        while (si < styled.size()) {
            out.add(styled.get(si++));
        }
        return out;
    }

    /** @deprecated */
    static String extractJsonArray(String content) {
        return extractJson(content);
    }
}
