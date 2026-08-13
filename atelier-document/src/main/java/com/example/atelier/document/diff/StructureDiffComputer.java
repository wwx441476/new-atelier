package com.example.atelier.document.diff;

import com.example.atelier.document.model.BlockType;
import com.example.atelier.document.model.CompareOptions;
import com.example.atelier.document.model.DiffOpType;
import com.example.atelier.document.model.DocumentBlock;
import com.example.atelier.document.model.DocumentModel;
import com.example.atelier.document.model.StructureOp;
import com.example.atelier.document.model.TableData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StructureDiffComputer {

    public List<StructureOp> diff(DocumentModel a, DocumentModel b, CompareOptions options) {
        boolean ignoreWhitespace = options == null || options.isIgnoreWhitespace();
        boolean excelKey = options != null && options.isExcelKeyColumn();
        List<StructureOp> ops = new ArrayList<>();
        Map<String, DocumentBlock> left = index(a, ignoreWhitespace);
        Map<String, DocumentBlock> right = index(b, ignoreWhitespace);

        for (Map.Entry<String, DocumentBlock> e : left.entrySet()) {
            String path = e.getKey();
            DocumentBlock lb = e.getValue();
            DocumentBlock rb = right.get(path);
            if (rb == null) {
                ops.add(StructureOp.builder()
                        .type(DiffOpType.REMOVED)
                        .path(path)
                        .blockType(typeName(lb))
                        .oldText(preview(lb))
                        .build());
                continue;
            }
            if (lb.getType() == BlockType.TABLE || lb.getType() == BlockType.SHEET
                    || rb.getType() == BlockType.TABLE || rb.getType() == BlockType.SHEET) {
                diffTables(ops, path, lb.getTable(), rb.getTable(), excelKey, ignoreWhitespace);
            } else {
                String lt = DiffNormalize.normalizeParagraph(lb.getText(), ignoreWhitespace);
                String rt = DiffNormalize.normalizeParagraph(rb.getText(), ignoreWhitespace);
                if (!lt.equals(rt)) {
                    ops.add(StructureOp.builder()
                            .type(DiffOpType.MODIFIED)
                            .path(path)
                            .blockType(typeName(lb))
                            .oldText(lb.getText())
                            .newText(rb.getText())
                            .build());
                }
            }
        }
        for (Map.Entry<String, DocumentBlock> e : right.entrySet()) {
            if (!left.containsKey(e.getKey())) {
                ops.add(StructureOp.builder()
                        .type(DiffOpType.ADDED)
                        .path(e.getKey())
                        .blockType(typeName(e.getValue()))
                        .newText(preview(e.getValue()))
                        .build());
            }
        }
        return ops;
    }

    private void diffTables(List<StructureOp> ops, String path, TableData left, TableData right,
                            boolean excelKey, boolean ignoreWhitespace) {
        if (left == null && right == null) {
            return;
        }
        if (left == null) {
            ops.add(StructureOp.builder().type(DiffOpType.ADDED).path(path + "/table")
                    .blockType("TABLE").newText("rows=" + right.getRows().size()).build());
            return;
        }
        if (right == null) {
            ops.add(StructureOp.builder().type(DiffOpType.REMOVED).path(path + "/table")
                    .blockType("TABLE").oldText("rows=" + left.getRows().size()).build());
            return;
        }
        if (excelKey) {
            Map<String, List<String>> lmap = rowsByKey(left.getRows(), ignoreWhitespace);
            Map<String, List<String>> rmap = rowsByKey(right.getRows(), ignoreWhitespace);
            for (Map.Entry<String, List<String>> e : lmap.entrySet()) {
                List<String> rr = rmap.get(e.getKey());
                if (rr == null) {
                    ops.add(StructureOp.builder().type(DiffOpType.REMOVED)
                            .path(path + "/row[key=" + e.getKey() + "]")
                            .blockType("TABLE_ROW")
                            .oldText(String.join(" | ", e.getValue()))
                            .build());
                } else if (!normalizeRow(e.getValue(), ignoreWhitespace)
                        .equals(normalizeRow(rr, ignoreWhitespace))) {
                    ops.add(StructureOp.builder().type(DiffOpType.MODIFIED)
                            .path(path + "/row[key=" + e.getKey() + "]")
                            .blockType("TABLE_ROW")
                            .oldText(String.join(" | ", e.getValue()))
                            .newText(String.join(" | ", rr))
                            .build());
                }
            }
            for (Map.Entry<String, List<String>> e : rmap.entrySet()) {
                if (!lmap.containsKey(e.getKey())) {
                    ops.add(StructureOp.builder().type(DiffOpType.ADDED)
                            .path(path + "/row[key=" + e.getKey() + "]")
                            .blockType("TABLE_ROW")
                            .newText(String.join(" | ", e.getValue()))
                            .build());
                }
            }
            return;
        }

        int max = Math.max(left.getRows().size(), right.getRows().size());
        for (int i = 0; i < max; i++) {
            List<String> lr = i < left.getRows().size() ? left.getRows().get(i) : null;
            List<String> rr = i < right.getRows().size() ? right.getRows().get(i) : null;
            if (lr == null) {
                ops.add(StructureOp.builder().type(DiffOpType.ADDED)
                        .path(path + "/row[" + i + "]")
                        .blockType("TABLE_ROW")
                        .newText(String.join(" | ", rr))
                        .build());
            } else if (rr == null) {
                ops.add(StructureOp.builder().type(DiffOpType.REMOVED)
                        .path(path + "/row[" + i + "]")
                        .blockType("TABLE_ROW")
                        .oldText(String.join(" | ", lr))
                        .build());
            } else {
                int cols = Math.max(lr.size(), rr.size());
                for (int c = 0; c < cols; c++) {
                    String lc = c < lr.size() ? DiffNormalize.normalizeLine(lr.get(c), ignoreWhitespace) : "";
                    String rc = c < rr.size() ? DiffNormalize.normalizeLine(rr.get(c), ignoreWhitespace) : "";
                    if (!lc.equals(rc)) {
                        ops.add(StructureOp.builder().type(DiffOpType.MODIFIED)
                                .path(path + "/row[" + i + "]/cell[" + c + "]")
                                .blockType("TABLE_CELL")
                                .oldText(c < lr.size() ? lr.get(c) : "")
                                .newText(c < rr.size() ? rr.get(c) : "")
                                .detail("row=" + i + ",col=" + c)
                                .build());
                    }
                }
            }
        }
    }

    private static Map<String, List<String>> rowsByKey(List<List<String>> rows, boolean ignoreWhitespace) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (List<String> row : rows) {
            String key = row.isEmpty() ? "" : DiffNormalize.normalizeLine(row.get(0), ignoreWhitespace);
            if (key.isEmpty()) {
                key = "__empty_" + map.size();
            }
            map.put(key, row);
        }
        return map;
    }

    private static String normalizeRow(List<String> row, boolean ignoreWhitespace) {
        StringBuilder sb = new StringBuilder();
        for (String c : row) {
            sb.append(DiffNormalize.normalizeLine(c, ignoreWhitespace)).append('\u0001');
        }
        return sb.toString();
    }

    private static Map<String, DocumentBlock> index(DocumentModel model, boolean ignoreWhitespace) {
        Map<String, DocumentBlock> map = new LinkedHashMap<>();
        if (model == null || model.getBlocks() == null) {
            return map;
        }
        int i = 0;
        for (DocumentBlock block : model.getBlocks()) {
            String path = pathFor(block, i++);
            map.put(path, block);
            if (block.getChildren() != null) {
                int c = 0;
                for (DocumentBlock child : block.getChildren()) {
                    map.put(path + "/" + pathFor(child, c++), child);
                }
            }
        }
        return map;
    }

    private static String pathFor(DocumentBlock block, int index) {
        if (block.getMeta() != null && block.getMeta().getSheet() != null) {
            return "sheet:" + block.getMeta().getSheet();
        }
        if (block.getMeta() != null && block.getMeta().getSlideIndex() != null) {
            return "slide:" + block.getMeta().getSlideIndex();
        }
        if (block.getType() == BlockType.HEADING) {
            return "heading[" + index + "]:L" + block.getLevel();
        }
        return (block.getType() == null ? "block" : block.getType().name().toLowerCase()) + "[" + index + "]";
    }

    private static String typeName(DocumentBlock block) {
        return block.getType() == null ? "BLOCK" : block.getType().name();
    }

    private static String preview(DocumentBlock block) {
        if (block.getTable() != null) {
            return "[table rows=" + block.getTable().getRows().size() + "]";
        }
        return block.getText();
    }
}
