package com.example.atelier.document.diff;

import com.example.atelier.document.model.CompareOptions;
import com.example.atelier.document.model.CompareQuality;
import com.example.atelier.document.model.CompareResult;
import com.example.atelier.document.model.CompareStats;
import com.example.atelier.document.model.DiffOpType;
import com.example.atelier.document.model.DocumentModel;
import com.example.atelier.document.model.ParagraphOp;
import com.example.atelier.document.model.StructureOp;
import com.example.atelier.document.model.TextHunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DiffEngine {

    private static final int PLAIN_TEXT_LIMIT = 200_000;

    private final TextDiffComputer textDiffComputer;
    private final ParagraphDiffComputer paragraphDiffComputer;
    private final StructureDiffComputer structureDiffComputer;

    public DiffEngine(TextDiffComputer textDiffComputer,
                      ParagraphDiffComputer paragraphDiffComputer,
                      StructureDiffComputer structureDiffComputer) {
        this.textDiffComputer = textDiffComputer;
        this.paragraphDiffComputer = paragraphDiffComputer;
        this.structureDiffComputer = structureDiffComputer;
    }

    public CompareResult compare(DocumentModel a, DocumentModel b, CompareOptions options) {
        CompareOptions opts = options == null ? CompareOptions.builder().build() : options;
        String plainA = a == null ? "" : a.toPlainText();
        String plainB = b == null ? "" : b.toPlainText();

        List<TextHunk> textHunks = textDiffComputer.diff(plainA, plainB, opts.isIgnoreWhitespace());
        List<ParagraphOp> paragraphOps = paragraphDiffComputer.diff(a, b, opts.isIgnoreWhitespace());
        List<StructureOp> structureOps = structureDiffComputer.diff(a, b, opts);

        CompareStats stats = tally(paragraphOps, structureOps);
        List<String> warnings = new ArrayList<>();
        boolean ocrUsed = (a != null && a.isOcrUsed()) || (b != null && b.isOcrUsed());
        if (a != null && a.getWarnings() != null) {
            warnings.addAll(a.getWarnings());
        }
        if (b != null && b.getWarnings() != null) {
            warnings.addAll(b.getWarnings());
        }
        if (plainA.length() > PLAIN_TEXT_LIMIT || plainB.length() > PLAIN_TEXT_LIMIT) {
            warnings.add("全文过长，前端展示文本已截断（Diff 仍基于完整抽取结果）");
        }

        return CompareResult.builder()
                .fileNameA(a == null ? null : a.getFileName())
                .fileNameB(b == null ? null : b.getFileName())
                .textHunks(textHunks)
                .paragraphOps(paragraphOps)
                .structureOps(structureOps)
                .stats(stats)
                .quality(CompareQuality.builder().ocrUsed(ocrUsed).warnings(warnings).build())
                .plainTextA(truncate(plainA))
                .plainTextB(truncate(plainB))
                .build();
    }

    private static CompareStats tally(List<ParagraphOp> paragraphOps, List<StructureOp> structureOps) {
        int added = 0, removed = 0, modified = 0, moved = 0;
        for (ParagraphOp op : paragraphOps) {
            if (op.getType() == DiffOpType.ADDED) {
                added++;
            } else if (op.getType() == DiffOpType.REMOVED) {
                removed++;
            } else if (op.getType() == DiffOpType.MODIFIED) {
                modified++;
            } else if (op.getType() == DiffOpType.MOVED) {
                moved++;
            }
        }
        // structure ops contribute when paragraph layer is sparse
        if (paragraphOps.isEmpty()) {
            for (StructureOp op : structureOps) {
                if (op.getType() == DiffOpType.ADDED) {
                    added++;
                } else if (op.getType() == DiffOpType.REMOVED) {
                    removed++;
                } else if (op.getType() == DiffOpType.MODIFIED) {
                    modified++;
                } else if (op.getType() == DiffOpType.MOVED) {
                    moved++;
                }
            }
        }
        return CompareStats.builder()
                .added(added)
                .removed(removed)
                .modified(modified)
                .moved(moved)
                .build();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= PLAIN_TEXT_LIMIT) {
            return text;
        }
        return text.substring(0, PLAIN_TEXT_LIMIT) + "\n...[truncated]...";
    }
}
