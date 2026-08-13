package com.example.atelier.document.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 预览块 ↔ 拼接明文（按行）索引，供对比文字 Diff 定位到 blockId。
 */
public final class PreviewLocateIndex {

    public static final class BlockSpan {
        private final String blockId;
        private final int lineStart;
        private final int lineEndExclusive;
        private final int charStart;
        private final int charEndExclusive;
        private final String plainText;

        public BlockSpan(String blockId, int lineStart, int lineEndExclusive,
                         int charStart, int charEndExclusive, String plainText) {
            this.blockId = blockId;
            this.lineStart = lineStart;
            this.lineEndExclusive = lineEndExclusive;
            this.charStart = charStart;
            this.charEndExclusive = charEndExclusive;
            this.plainText = plainText;
        }

        public String getBlockId() {
            return blockId;
        }

        public int getLineStart() {
            return lineStart;
        }

        public int getLineEndExclusive() {
            return lineEndExclusive;
        }

        public int getCharStart() {
            return charStart;
        }

        public int getCharEndExclusive() {
            return charEndExclusive;
        }

        public String getPlainText() {
            return plainText;
        }
    }

    private final String plainText;
    private final List<String> lines;
    private final List<BlockSpan> spans;

    private PreviewLocateIndex(String plainText, List<String> lines, List<BlockSpan> spans) {
        this.plainText = plainText;
        this.lines = lines;
        this.spans = spans;
    }

    public static PreviewLocateIndex from(PreviewDocument document) {
        if (document == null || document.getBlocks() == null || document.getBlocks().isEmpty()) {
            return new PreviewLocateIndex("", Collections.emptyList(), Collections.emptyList());
        }
        StringBuilder plain = new StringBuilder();
        List<String> lines = new ArrayList<>();
        List<BlockSpan> spans = new ArrayList<>();
        for (PreviewBlock block : document.getBlocks()) {
            if (block == null || block.getType() == PreviewBlockType.SECTION) {
                continue;
            }
            String text = blockText(block);
            if (text.isEmpty()) {
                continue;
            }
            String id = block.getId() == null || block.getId().isEmpty()
                    ? "anon-" + spans.size() : block.getId();
            if (plain.length() > 0) {
                plain.append('\n');
            }
            int charStart = plain.length();
            int lineStart = lines.size();
            String[] partLines = text.split("\n", -1);
            for (int i = 0; i < partLines.length; i++) {
                if (i > 0) {
                    plain.append('\n');
                }
                lines.add(partLines[i]);
                plain.append(partLines[i]);
            }
            int charEnd = plain.length();
            int lineEnd = lines.size();
            spans.add(new BlockSpan(id, lineStart, lineEnd, charStart, charEnd, text));
        }
        return new PreviewLocateIndex(plain.toString(), lines, spans);
    }

    private static String blockText(PreviewBlock block) {
        if (block.getType() == PreviewBlockType.TABLE && block.getTable() != null
                && block.getTable().getRows() != null) {
            return flattenTable(block.getTable().getRows());
        }
        if (block.getType() == PreviewBlockType.IMAGE) {
            String t = PreviewTextNormalize.blockPlainText(block);
            return "[IMAGE]" + (t.isEmpty() ? "" : " " + t);
        }
        return PreviewTextNormalize.blockPlainText(block).replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String flattenTable(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            if (row == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(String.join("\t", row));
        }
        return sb.toString();
    }

    public String getPlainText() {
        return plainText;
    }

    public List<String> getLines() {
        return lines;
    }

    public List<BlockSpan> getSpans() {
        return spans;
    }

    /** 行区间 [lineStart, lineStart+lineCount) 覆盖到的块 id（保序去重） */
    public List<String> blockIdsForLineRange(int lineStart, int lineCount) {
        if (lineCount <= 0 || spans.isEmpty()) {
            return Collections.emptyList();
        }
        int from = Math.max(0, lineStart);
        int to = Math.max(from, lineStart + lineCount);
        Set<String> ids = new LinkedHashSet<>();
        for (BlockSpan span : spans) {
            if (span.lineEndExclusive <= from || span.lineStart >= to) {
                continue;
            }
            ids.add(span.blockId);
        }
        return new ArrayList<>(ids);
    }

    /** 用片段文本匹配块（归一化包含 / 相等） */
    public List<String> blockIdsForSnippet(String snippet) {
        if (snippet == null || snippet.trim().isEmpty() || spans.isEmpty()) {
            return Collections.emptyList();
        }
        String norm = PreviewTextNormalize.normalize(snippet);
        if (norm.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> exact = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (BlockSpan span : spans) {
            String sn = PreviewTextNormalize.normalize(span.plainText);
            if (sn.equals(norm)) {
                exact.add(span.blockId);
            } else if (sn.contains(norm) || (norm.contains(sn) && sn.length() >= 8)) {
                contains.add(span.blockId);
            }
        }
        if (!exact.isEmpty()) {
            return exact;
        }
        if (!contains.isEmpty()) {
            return contains.size() > 3 ? contains.subList(0, 3) : contains;
        }
        if (norm.length() > 24) {
            String head = norm.substring(0, 24);
            for (BlockSpan span : spans) {
                if (PreviewTextNormalize.normalize(span.plainText).contains(head)) {
                    return Collections.singletonList(span.blockId);
                }
            }
        }
        return Collections.emptyList();
    }
}
