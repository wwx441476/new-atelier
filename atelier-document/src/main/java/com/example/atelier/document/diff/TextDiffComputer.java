package com.example.atelier.document.diff;

import com.example.atelier.document.model.DiffOpType;
import com.example.atelier.document.model.TextHunk;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class TextDiffComputer {

    public List<TextHunk> diff(String textA, String textB, boolean ignoreWhitespace) {
        List<String> linesA = toLines(textA, ignoreWhitespace);
        List<String> linesB = toLines(textB, ignoreWhitespace);
        Patch<String> patch = DiffUtils.diff(linesA, linesB);
        List<TextHunk> hunks = new ArrayList<>();
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            DiffOpType type;
            switch (delta.getType()) {
                case INSERT:
                    type = DiffOpType.ADDED;
                    break;
                case DELETE:
                    type = DiffOpType.REMOVED;
                    break;
                case CHANGE:
                    type = DiffOpType.MODIFIED;
                    break;
                default:
                    type = DiffOpType.EQUAL;
            }
            hunks.add(TextHunk.builder()
                    .type(type)
                    .oldStart(delta.getSource().getPosition())
                    .newStart(delta.getTarget().getPosition())
                    .oldLines(new ArrayList<>(delta.getSource().getLines() == null
                            ? Collections.emptyList() : delta.getSource().getLines()))
                    .newLines(new ArrayList<>(delta.getTarget().getLines() == null
                            ? Collections.emptyList() : delta.getTarget().getLines()))
                    .build());
        }
        return hunks;
    }

    private static List<String> toLines(String text, boolean ignoreWhitespace) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        String[] raw = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> lines = new ArrayList<>(raw.length);
        for (String line : raw) {
            lines.add(DiffNormalize.normalizeLine(line, ignoreWhitespace));
        }
        // drop trailing empty line noise from split
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }
}
