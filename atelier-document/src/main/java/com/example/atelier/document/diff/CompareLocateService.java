package com.example.atelier.document.diff;

import com.example.atelier.document.model.CompareOptions;
import com.example.atelier.document.model.CompareResult;
import com.example.atelier.document.model.ParagraphOp;
import com.example.atelier.document.model.StructureOp;
import com.example.atelier.document.model.TextHunk;
import com.example.atelier.document.preview.PreviewDocument;
import com.example.atelier.document.preview.PreviewLocateIndex;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 将 Diff 挂到最终预览 IR 的 blockId 上：文字 Diff 基于预览拼接明文重算，段落/结构用片段匹配。
 */
@Service
public class CompareLocateService {

    private final TextDiffComputer textDiffComputer;

    public CompareLocateService(TextDiffComputer textDiffComputer) {
        this.textDiffComputer = textDiffComputer;
    }

    public void attach(CompareResult result,
                       PreviewDocument previewA,
                       PreviewDocument previewB,
                       CompareOptions options) {
        if (result == null) {
            return;
        }
        result.setPreviewA(previewA);
        result.setPreviewB(previewB);

        PreviewLocateIndex indexA = PreviewLocateIndex.from(previewA);
        PreviewLocateIndex indexB = PreviewLocateIndex.from(previewB);
        boolean ignoreWs = options == null || options.isIgnoreWhitespace();

        List<TextHunk> textHunks = textDiffComputer.diff(
                indexA.getPlainText(), indexB.getPlainText(), ignoreWs);
        for (TextHunk hunk : textHunks) {
            int oldCount = hunk.getOldLines() == null ? 0 : hunk.getOldLines().size();
            int newCount = hunk.getNewLines() == null ? 0 : hunk.getNewLines().size();
            hunk.setBlockIdsA(indexA.blockIdsForLineRange(hunk.getOldStart(), oldCount));
            hunk.setBlockIdsB(indexB.blockIdsForLineRange(hunk.getNewStart(), newCount));
        }
        result.setTextHunks(textHunks);
        result.setPlainTextA(truncate(indexA.getPlainText()));
        result.setPlainTextB(truncate(indexB.getPlainText()));

        if (result.getParagraphOps() != null) {
            for (ParagraphOp op : result.getParagraphOps()) {
                op.setBlockIdsA(resolveSnippet(indexA, op.getOldText()));
                op.setBlockIdsB(resolveSnippet(indexB, op.getNewText()));
            }
        }
        if (result.getStructureOps() != null) {
            for (StructureOp op : result.getStructureOps()) {
                op.setBlockIdsA(resolveSnippet(indexA, op.getOldText()));
                op.setBlockIdsB(resolveSnippet(indexB, op.getNewText()));
            }
        }
    }

    private static List<String> resolveSnippet(PreviewLocateIndex index, String snippet) {
        if (snippet == null || snippet.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = index.blockIdsForSnippet(snippet);
        return ids == null ? new ArrayList<>() : new ArrayList<>(ids);
    }

    private static final int PLAIN_TEXT_LIMIT = 200_000;

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
