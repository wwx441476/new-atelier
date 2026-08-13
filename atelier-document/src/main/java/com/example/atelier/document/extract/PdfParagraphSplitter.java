package com.example.atelier.document.extract;

import com.example.atelier.document.model.BlockMeta;
import com.example.atelier.document.model.BlockType;
import com.example.atelier.document.model.DocumentBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 将 PDF/OCR 纯文本拆成阅读块。
 * <p>
 * 中文制度类 PDF 通常只有版心软换行、很少空行：软换行应并入当前块（避免「处/置」断字）。
 * 章/节/条标题立即成块；编号/项目符开启可续行块；空行强制分段。
 */
final class PdfParagraphSplitter {

    private static final Pattern HEADING_LINE = Pattern.compile(
            "^第[零一二三四五六七八九十百千\\d]+[章节条].*");

    private static final Pattern NUMBERED_ITEM = Pattern.compile(
            "^\\d+\\s*[.、．]\\s*.+"
                    + "|^[（(][零一二三四五六七八九十百千\\d]+[）)]\\s*.+");

    private static final Pattern BULLET_GLYPH = Pattern.compile("^[·•●○◦▪▫◆◇]\\s*.+");

    /**
     * PDF 常把空心圆抽成 。／·；不含破折号，避免软换行行首「—」被误切。
     */
    private static final Pattern LEADING_BULLET_JUNK = Pattern.compile("^[·•●○◦▪▫◆◇。．]\\s*");

    private PdfParagraphSplitter() {
    }

    static void splitInto(List<DocumentBlock> blocks, BlockIds ids, String text,
                          int page, Double confidence) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        String normalized = text.replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder buf = new StringBuilder();
        String prevEmittedNorm = null;
        for (String raw : lines) {
            String trimmed = raw == null ? "" : raw.trim();
            if (trimmed.isEmpty()) {
                flush(blocks, ids, buf, page, confidence);
                prevEmittedNorm = null;
                continue;
            }
            if (LEADING_BULLET_JUNK.matcher(trimmed).matches() && trimmed.length() <= 2) {
                continue;
            }

            boolean bulletStart = isBulletBody(trimmed);
            String line = LEADING_BULLET_JUNK.matcher(trimmed).replaceFirst("").trim();
            if (line.isEmpty()) {
                continue;
            }

            String lineNorm = collapseWs(line);
            if (lineNorm.equals(prevEmittedNorm) && lineNorm.length() <= 80) {
                continue;
            }

            // 章 / 节 / 条：立即成块，不与后文软拼接
            if (HEADING_LINE.matcher(line).matches()) {
                flush(blocks, ids, buf, page, confidence);
                blocks.add(block(ids, line, page, confidence));
                prevEmittedNorm = lineNorm;
                continue;
            }

            // 编号列表 / 项目符：开启新块，后续软换行并入
            if (NUMBERED_ITEM.matcher(line).matches() || bulletStart) {
                flush(blocks, ids, buf, page, confidence);
                String display = bulletStart && !NUMBERED_ITEM.matcher(line).matches()
                        ? "· " + line
                        : line;
                buf.append(display);
                prevEmittedNorm = collapseWs(display);
                continue;
            }

            // 正文软换行并入当前块
            if (buf.length() > 0) {
                if (lineNorm.equals(collapseWs(buf.toString())) && lineNorm.length() <= 80) {
                    continue;
                }
                appendSoft(buf, line);
            } else {
                buf.append(line);
            }
            prevEmittedNorm = lineNorm;
        }
        flush(blocks, ids, buf, page, confidence);
    }

    /** 软换行拼接：CJK 直接相连，拉丁词 / 破折号后补空格 */
    static void appendSoft(StringBuilder buf, String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        if (buf.length() == 0) {
            buf.append(line);
            return;
        }
        char prev = buf.charAt(buf.length() - 1);
        char next = line.charAt(0);
        if (needsJoinSpace(prev, next)) {
            buf.append(' ');
        }
        buf.append(line);
    }

    static boolean needsJoinSpace(char prev, char next) {
        if (isAsciiWordChar(prev) && isAsciiWordChar(next)) {
            return true;
        }
        if ((prev == '—' || prev == '–' || prev == '－' || prev == '-' || prev == '/')
                && !isClosingOrPunct(next)) {
            return true;
        }
        return false;
    }

    private static boolean isClosingOrPunct(char c) {
        return "，。；、）》」』】）].,;:!?".indexOf(c) >= 0;
    }

    private static boolean isAsciiWordChar(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9');
    }

    private static String collapseWs(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }

    static boolean isStructuralLine(String line) {
        if (line == null) {
            return false;
        }
        String t = line.trim();
        return HEADING_LINE.matcher(t).matches()
                || NUMBERED_ITEM.matcher(t).matches()
                || BULLET_GLYPH.matcher(t).matches();
    }

    private static boolean isBulletBody(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        return LEADING_BULLET_JUNK.matcher(raw).lookingAt();
    }

    private static void flush(List<DocumentBlock> blocks, BlockIds ids, StringBuilder buf,
                              int page, Double confidence) {
        if (buf.length() == 0) {
            return;
        }
        String text = buf.toString().trim();
        buf.setLength(0);
        if (!text.isEmpty()) {
            blocks.add(block(ids, text, page, confidence));
        }
    }

    private static DocumentBlock block(BlockIds ids, String text, int page, Double confidence) {
        return DocumentBlock.builder()
                .id(ids.next())
                .type(BlockType.PARAGRAPH)
                .text(text)
                .meta(BlockMeta.builder().page(page).ocrConfidence(confidence).build())
                .build();
    }

    static List<String> splitTexts(String text) {
        List<DocumentBlock> blocks = new ArrayList<>();
        splitInto(blocks, new BlockIds("t"), text, 1, null);
        List<String> out = new ArrayList<>();
        for (DocumentBlock b : blocks) {
            out.add(b.getText());
        }
        return out;
    }
}
