package com.example.atelier.document.preview;

import java.util.ArrayList;
import java.util.List;

/**
 * 确定性结构归一：去掉相邻重复块（PDF 伪粗体 / 页眉叠标题等），不依赖 LLM。
 */
public class PreviewStructureNormalizer {

    public PreviewDocument normalize(PreviewDocument document) {
        if (document == null || document.getBlocks() == null || document.getBlocks().isEmpty()) {
            return document;
        }
        List<PreviewBlock> in = document.getBlocks();
        List<PreviewBlock> out = new ArrayList<>(in.size());
        for (PreviewBlock block : in) {
            if (block == null) {
                continue;
            }
            if (out.isEmpty()) {
                out.add(block);
                continue;
            }
            PreviewBlock prev = out.get(out.size() - 1);
            if (isAdjacentDuplicate(prev, block)) {
                out.set(out.size() - 1, prefer(prev, block));
                continue;
            }
            out.add(block);
        }
        document.setBlocks(out);
        return document;
    }

    static boolean isAdjacentDuplicate(PreviewBlock a, PreviewBlock b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.getType() == PreviewBlockType.SECTION || b.getType() == PreviewBlockType.SECTION
                || a.getType() == PreviewBlockType.TABLE || b.getType() == PreviewBlockType.TABLE
                || a.getType() == PreviewBlockType.SHEET || b.getType() == PreviewBlockType.SHEET
                || a.getType() == PreviewBlockType.IMAGE || b.getType() == PreviewBlockType.IMAGE) {
            return false;
        }
        String na = PreviewTextNormalize.normalize(PreviewTextNormalize.blockPlainText(a));
        String nb = PreviewTextNormalize.normalize(PreviewTextNormalize.blockPlainText(b));
        if (na.isEmpty() || nb.isEmpty()) {
            return false;
        }
        if (!na.equals(nb)) {
            return false;
        }
        // 只合并短重复（标题/伪粗体），避免误伤正文重复句
        return na.length() <= 80;
    }

    static PreviewBlock prefer(PreviewBlock a, PreviewBlock b) {
        int sa = score(a);
        int sb = score(b);
        return sb > sa ? b : a;
    }

    private static int score(PreviewBlock block) {
        int s = 0;
        if (block.getType() == PreviewBlockType.HEADING) {
            s += 10;
            s += Math.max(0, 6 - block.getLevel());
        } else if (block.getType() == PreviewBlockType.LIST_ITEM) {
            s += 2;
        }
        if (block.getRuns() != null) {
            for (PreviewRun run : block.getRuns()) {
                if (run.getMarks() != null && run.getMarks().contains(PreviewInlineMark.BOLD)) {
                    s += 3;
                    break;
                }
            }
        }
        return s;
    }
}
