package com.example.atelier.document.extract;

import com.example.atelier.document.model.BlockMeta;
import com.example.atelier.document.model.BlockType;
import com.example.atelier.document.model.DocumentBlock;
import com.example.atelier.document.model.DocumentModel;
import com.example.atelier.document.model.TableData;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Order(20)
public class WordDocumentExtractor implements DocumentExtractor {

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
        try (XWPFDocument doc = new XWPFDocument(input)) {
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph) {
                    XWPFParagraph p = (XWPFParagraph) element;
                    String text = p.getText();
                    if (text == null || text.trim().isEmpty()) {
                        continue;
                    }
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
                } else if (element instanceof XWPFTable) {
                    blocks.add(toTableBlock(ids.next(), (XWPFTable) element));
                }
            }
        }
        return DocumentModel.builder()
                .fileName(context.getFileName())
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .blocks(blocks)
                .build();
    }

    private static DocumentBlock toTableBlock(String id, XWPFTable table) {
        List<List<String>> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cell.getText() == null ? "" : cell.getText().trim());
            }
            rows.add(cells);
        }
        return DocumentBlock.builder()
                .id(id)
                .type(BlockType.TABLE)
                .table(TableData.builder().rows(rows).build())
                .text(summarizeTable(rows))
                .build();
    }

    private static String summarizeTable(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder("[table ");
        sb.append(rows.size()).append(" rows]");
        return sb.toString();
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
