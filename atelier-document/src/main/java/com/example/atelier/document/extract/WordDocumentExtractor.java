package com.example.atelier.document.extract;

import com.example.atelier.document.model.BlockMeta;
import com.example.atelier.document.model.BlockType;
import com.example.atelier.document.model.DocumentBlock;
import com.example.atelier.document.model.DocumentModel;
import com.example.atelier.document.model.TableData;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@Order(20)
public class WordDocumentExtractor implements DocumentExtractor {

    private static final int MAX_IMAGES = 40;
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;

    @Override
    public boolean supports(String mimeType, String fileName) {
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return mime.contains("wordprocessingml") || name.endsWith(".docx");
    }

    @Override
    public DocumentModel extract(InputStream input, ExtractContext context) throws Exception {
        BlockIds ids = new BlockIds("docx");
        List<DocumentBlock> blocks = new ArrayList<>();
        Set<String> seenImageKeys = new LinkedHashSet<>();
        int imageCount = 0;
        try (XWPFDocument doc = new XWPFDocument(input)) {
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph) {
                    imageCount += appendParagraph(blocks, ids, (XWPFParagraph) element,
                            seenImageKeys, imageCount);
                } else if (element instanceof XWPFTable) {
                    imageCount += appendTable(blocks, ids, (XWPFTable) element,
                            seenImageKeys, imageCount);
                }
            }
        }
        return DocumentModel.builder()
                .fileName(context.getFileName())
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .blocks(blocks)
                .build();
    }

    private static int appendParagraph(List<DocumentBlock> blocks, BlockIds ids, XWPFParagraph p,
                                       Set<String> seenImageKeys, int imageCount) {
        String text = p.getText();
        boolean hasText = text != null && !text.trim().isEmpty();
        List<DocumentBlock> images = extractImagesFromParagraph(ids, p, seenImageKeys, imageCount);
        if (hasText) {
            String style = p.getStyle();
            BlockType type = BlockType.PARAGRAPH;
            int level = 0;
            if (style != null && style.toLowerCase(Locale.ROOT).contains("heading")) {
                type = BlockType.HEADING;
                level = parseHeadingLevel(style);
            } else if (text.trim().matches("^([-*+]|\\d+\\.)\\s+.*")) {
                type = BlockType.LIST_ITEM;
            }
            blocks.add(DocumentBlock.builder()
                    .id(ids.next())
                    .type(type)
                    .level(level)
                    .text(text.trim())
                    .meta(BlockMeta.builder().styleHints(style).build())
                    .build());
        }
        blocks.addAll(images);
        return images.size();
    }

    private static int appendTable(List<DocumentBlock> blocks, BlockIds ids, XWPFTable table,
                                   Set<String> seenImageKeys, int imageCount) {
        List<List<String>> rows = new ArrayList<>();
        List<DocumentBlock> images = new ArrayList<>();
        int addedImages = 0;
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cellTextPreservingBreaks(cell));
                for (XWPFParagraph p : cell.getParagraphs()) {
                    List<DocumentBlock> cellImages = extractImagesFromParagraph(
                            ids, p, seenImageKeys, imageCount + addedImages);
                    images.addAll(cellImages);
                    addedImages += cellImages.size();
                }
            }
            rows.add(cells);
        }
        boolean hasText = rows.stream().flatMap(List::stream).anyMatch(s -> s != null && !s.isEmpty());
        if (hasText) {
            if (isLayoutOrPreformattedTable(rows)) {
                // Word 常用单列表 / 文本框式表格承载 ASCII 树图；按等宽文本输出，避免「A」假表头
                String plain = flattenTableToPlainText(rows);
                blocks.add(DocumentBlock.builder()
                        .id(ids.next())
                        .type(BlockType.CODE)
                        .text(plain)
                        .meta(BlockMeta.builder().styleHints("word-layout-table").build())
                        .build());
            } else {
                blocks.add(DocumentBlock.builder()
                        .id(ids.next())
                        .type(BlockType.TABLE)
                        .table(TableData.builder().rows(normalizeTableRows(rows)).build())
                        .text(summarizeTable(rows))
                        .build());
            }
        }
        // 纯图片表格（流程图常嵌在空表里）：不输出空表，只输出图片
        blocks.addAll(images);
        return addedImages;
    }

    /** 单元格内多段落保留换行（POI getText() 会拼成一行，树图会乱） */
    static String cellTextPreservingBreaks(XWPFTableCell cell) {
        if (cell == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph p : cell.getParagraphs()) {
            if (p == null) {
                continue;
            }
            String t = p.getText();
            if (t == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(t);
        }
        return sb.toString().replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    /**
     * 单列/近似文本框的表格，或含 ASCII 树连接符的版式表 → 不当真实数据表。
     */
    static boolean isLayoutOrPreformattedTable(List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        int maxCols = 0;
        int nonEmptyCells = 0;
        StringBuilder all = new StringBuilder();
        for (List<String> row : rows) {
            if (row == null) {
                continue;
            }
            maxCols = Math.max(maxCols, row.size());
            for (String cell : row) {
                if (cell != null && !cell.trim().isEmpty()) {
                    nonEmptyCells++;
                    if (all.length() > 0) {
                        all.append('\n');
                    }
                    all.append(cell);
                }
            }
        }
        if (maxCols <= 1) {
            return true;
        }
        // 多数单元格为空、实质只有一两格有字 → 版式表
        if (nonEmptyCells <= 2 && rows.size() >= 1) {
            return looksLikeAsciiTree(all.toString()) || maxCols <= 2 && nonEmptyCells == 1;
        }
        return looksLikeAsciiTree(all.toString());
    }

    static boolean looksLikeAsciiTree(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains("|——")
                || text.contains("|──")
                || text.contains("|--")
                || text.contains("L——")
                || text.contains("└——")
                || text.contains("└─")
                || text.contains("├─")
                || text.contains("├──")
                || (text.contains("│") && (text.contains("─") || text.contains("--")));
    }

    static String flattenTableToPlainText(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            if (row == null) {
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String cell : row) {
                if (cell == null || cell.trim().isEmpty()) {
                    continue;
                }
                if (line.length() > 0) {
                    line.append('\n');
                }
                line.append(cell.trim());
            }
            if (line.length() == 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }

    /** 真实数据表：单元格内换行改为空格，避免前端表格单元格过高 */
    private static List<List<String>> normalizeTableRows(List<List<String>> rows) {
        List<List<String>> out = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            List<String> cells = new ArrayList<>();
            for (String cell : row) {
                String c = cell == null ? "" : cell.replace('\n', ' ').replaceAll("\\s+", " ").trim();
                cells.add(c);
            }
            out.add(cells);
        }
        return out;
    }

    private static List<DocumentBlock> extractImagesFromParagraph(BlockIds ids, XWPFParagraph p,
                                                                  Set<String> seenImageKeys,
                                                                  int imageCount) {
        List<DocumentBlock> out = new ArrayList<>();
        if (p == null || imageCount >= MAX_IMAGES) {
            return out;
        }
        for (XWPFRun run : p.getRuns()) {
            if (run == null) {
                continue;
            }
            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                if (imageCount + out.size() >= MAX_IMAGES) {
                    return out;
                }
                DocumentBlock img = toImageBlock(ids, picture, seenImageKeys);
                if (img != null) {
                    out.add(img);
                }
            }
        }
        return out;
    }

    static DocumentBlock toImageBlock(BlockIds ids, XWPFPicture picture, Set<String> seenImageKeys) {
        if (picture == null || picture.getPictureData() == null) {
            return null;
        }
        XWPFPictureData data = picture.getPictureData();
        byte[] bytes = data.getData();
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            return null;
        }
        String partName = "";
        try {
            partName = data.getPackagePart().getPartName().getName();
        } catch (Exception ignored) {
            partName = String.valueOf(System.identityHashCode(data));
        }
        String key = partName + ":" + bytes.length;
        if (seenImageKeys != null && !seenImageKeys.add(key)) {
            return null;
        }
        String mime = pictureMime(data);
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        String desc = picture.getDescription();
        String text = (desc != null && !desc.trim().isEmpty()) ? desc.trim() : "[图片]";
        return DocumentBlock.builder()
                .id(ids.next())
                .type(BlockType.IMAGE)
                .text(text)
                .imageDataUrl(dataUrl)
                .build();
    }

    private static String pictureMime(XWPFPictureData data) {
        try {
            String ct = data.getPackagePart().getContentType();
            if (ct != null && ct.startsWith("image/")) {
                return ct;
            }
        } catch (Exception ignored) {
            // fall through
        }
        String ext = data.suggestFileExtension();
        if (ext == null) {
            return "image/png";
        }
        switch (ext.toLowerCase(Locale.ROOT)) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "tif":
            case "tiff":
                return "image/tiff";
            case "webp":
                return "image/webp";
            case "emf":
            case "wmf":
                return "image/" + ext.toLowerCase(Locale.ROOT);
            default:
                return "image/png";
        }
    }

    private static String summarizeTable(List<List<String>> rows) {
        return "[table " + rows.size() + " rows]";
    }

    private static int parseHeadingLevel(String style) {
        for (int i = 1; i <= 6; i++) {
            if (style.toLowerCase(Locale.ROOT).contains("heading" + i)
                    || style.toLowerCase(Locale.ROOT).contains("heading " + i)) {
                return i;
            }
        }
        return 1;
    }
}
