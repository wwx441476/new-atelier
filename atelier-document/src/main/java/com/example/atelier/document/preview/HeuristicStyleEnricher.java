package com.example.atelier.document.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * LLM 失败时的轻量规则样式：识别中文章条/标题等，保证至少有标题层级。
 */
public class HeuristicStyleEnricher {

    // 不用 \\b：Java 默认词边界不含中文，会导致「第一章」匹配失败
    private static final Pattern CHAPTER = Pattern.compile("^第[零一二三四五六七八九十百千\\d]+章.*");
    private static final Pattern ARTICLE = Pattern.compile("^第[零一二三四五六七八九十百千\\d]+条.*");
    private static final Pattern SECTION = Pattern.compile("^第[零一二三四五六七八九十百千\\d]+节.*");

    public List<PreviewBlock> enrichPage(List<PreviewBlock> originals) {
        if (originals == null || originals.isEmpty()) {
            return originals;
        }
        List<PreviewBlock> out = new ArrayList<>(originals.size());
        boolean first = true;
        for (PreviewBlock src : originals) {
            PreviewBlock copy = copyOf(src);
            String text = PreviewTextNormalize.blockPlainText(copy).trim();
            if (text.isEmpty()) {
                out.add(copy);
                continue;
            }
            String oneLine = text.replaceAll("\\s+", "");
            if (first && text.length() <= 40 && !CHAPTER.matcher(text).find() && !ARTICLE.matcher(text).find()) {
                applyHeading(copy, 1, true);
                first = false;
                out.add(copy);
                continue;
            }
            first = false;
            if (CHAPTER.matcher(text).find() || CHAPTER.matcher(oneLine).find()) {
                applyHeading(copy, 2, true);
            } else if (SECTION.matcher(text).find() || SECTION.matcher(oneLine).find()) {
                applyHeading(copy, 2, true);
            } else if (ARTICLE.matcher(text).find() || ARTICLE.matcher(oneLine).find()) {
                applyHeading(copy, 3, true);
            }
            out.add(copy);
        }
        return out;
    }

    private static void applyHeading(PreviewBlock block, int level, boolean bold) {
        block.setType(PreviewBlockType.HEADING);
        block.setLevel(level);
        String text = PreviewTextNormalize.blockPlainText(block);
        List<PreviewInlineMark> marks = bold
                ? Collections.singletonList(PreviewInlineMark.BOLD)
                : new ArrayList<>();
        block.setRuns(Collections.singletonList(PreviewRun.builder().text(text).marks(new ArrayList<>(marks)).build()));
        block.setText(text);
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
