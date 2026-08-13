package com.example.atelier.document.diff;

import com.example.atelier.document.model.BlockType;
import com.example.atelier.document.model.DiffOpType;
import com.example.atelier.document.model.DocumentBlock;
import com.example.atelier.document.model.DocumentModel;
import com.example.atelier.document.model.ParagraphOp;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ParagraphDiffComputer {

    private static final double MODIFY_THRESHOLD = 0.55;
    private static final double MOVE_THRESHOLD = 0.92;

    public List<ParagraphOp> diff(DocumentModel a, DocumentModel b, boolean ignoreWhitespace) {
        List<Para> left = flatten(a, ignoreWhitespace);
        List<Para> right = flatten(b, ignoreWhitespace);

        boolean[] usedRight = new boolean[right.size()];
        Integer[] matchRight = new Integer[left.size()];
        DiffOpType[] matchType = new DiffOpType[left.size()];

        // exact / high-similarity matches preferring same index
        for (int i = 0; i < left.size(); i++) {
            int bestJ = -1;
            double bestScore = -1;
            for (int j = 0; j < right.size(); j++) {
                if (usedRight[j]) {
                    continue;
                }
                double score = DiffNormalize.similarity(left.get(i).text, right.get(j).text);
                if (score > bestScore || (score == bestScore && Math.abs(j - i) < Math.abs(bestJ - i))) {
                    bestScore = score;
                    bestJ = j;
                }
            }
            if (bestJ >= 0 && bestScore >= MOVE_THRESHOLD) {
                usedRight[bestJ] = true;
                matchRight[i] = bestJ;
                matchType[i] = bestJ == i ? DiffOpType.EQUAL : DiffOpType.MOVED;
            } else if (bestJ >= 0 && bestScore >= MODIFY_THRESHOLD) {
                usedRight[bestJ] = true;
                matchRight[i] = bestJ;
                matchType[i] = DiffOpType.MODIFIED;
            }
        }

        List<ParagraphOp> ops = new ArrayList<>();
        for (int i = 0; i < left.size(); i++) {
            if (matchRight[i] == null) {
                ops.add(ParagraphOp.builder()
                        .type(DiffOpType.REMOVED)
                        .oldIndex(i)
                        .oldText(left.get(i).raw)
                        .blockType(left.get(i).type.name())
                        .build());
            } else {
                int j = matchRight[i];
                DiffOpType type = matchType[i];
                if (type == DiffOpType.EQUAL) {
                    continue;
                }
                ParagraphOp.ParagraphOpBuilder builder = ParagraphOp.builder()
                        .type(type)
                        .oldIndex(i)
                        .newIndex(j)
                        .oldText(left.get(i).raw)
                        .newText(right.get(j).raw)
                        .blockType(left.get(i).type.name());
                if (type == DiffOpType.MOVED) {
                    builder.movedTo(j);
                }
                ops.add(builder.build());
            }
        }
        for (int j = 0; j < right.size(); j++) {
            if (!usedRight[j]) {
                ops.add(ParagraphOp.builder()
                        .type(DiffOpType.ADDED)
                        .newIndex(j)
                        .newText(right.get(j).raw)
                        .blockType(right.get(j).type.name())
                        .build());
            }
        }
        return ops;
    }

    private static List<Para> flatten(DocumentModel model, boolean ignoreWhitespace) {
        List<Para> list = new ArrayList<>();
        if (model == null || model.getBlocks() == null) {
            return list;
        }
        walk(model.getBlocks(), list, ignoreWhitespace);
        return list;
    }

    private static void walk(List<DocumentBlock> blocks, List<Para> out, boolean ignoreWhitespace) {
        for (DocumentBlock block : blocks) {
            if (block.getType() == BlockType.TABLE || block.getType() == BlockType.SHEET) {
                if (block.getTable() != null) {
                    int r = 0;
                    for (List<String> row : block.getTable().getRows()) {
                        String text = String.join(" | ", row);
                        out.add(new Para(BlockType.PARAGRAPH,
                                DiffNormalize.normalizeParagraph(text, ignoreWhitespace), text));
                        r++;
                    }
                } else if (block.getText() != null) {
                    out.add(new Para(block.getType(),
                            DiffNormalize.normalizeParagraph(block.getText(), ignoreWhitespace),
                            block.getText()));
                }
            } else if (block.getText() != null && !block.getText().trim().isEmpty()) {
                out.add(new Para(block.getType() == null ? BlockType.PARAGRAPH : block.getType(),
                        DiffNormalize.normalizeParagraph(block.getText(), ignoreWhitespace),
                        block.getText()));
            }
            if (block.getChildren() != null && !block.getChildren().isEmpty()) {
                walk(block.getChildren(), out, ignoreWhitespace);
            }
        }
    }

    private static final class Para {
        final BlockType type;
        final String text;
        final String raw;

        Para(BlockType type, String text, String raw) {
            this.type = type;
            this.text = text;
            this.raw = raw;
        }
    }
}
