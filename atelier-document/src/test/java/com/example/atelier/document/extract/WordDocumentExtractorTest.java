package com.example.atelier.document.extract;

import com.example.atelier.document.model.BlockType;
import com.example.atelier.document.model.DocumentBlock;
import com.example.atelier.document.model.DocumentModel;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WordDocumentExtractorTest {

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private final WordDocumentExtractor extractor = new WordDocumentExtractor();

    @Test
    public void extractsEmbeddedPictureFromParagraph() throws Exception {
        byte[] docx = buildDocxWithParagraphImage();
        DocumentModel model = extractor.extract(new ByteArrayInputStream(docx),
                ExtractContext.builder().fileName("pic.docx").build());

        assertTrue(model.getBlocks().stream().anyMatch(b -> b.getType() == BlockType.IMAGE));
        DocumentBlock image = model.getBlocks().stream()
                .filter(b -> b.getType() == BlockType.IMAGE)
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertTrue(image.getImageDataUrl() != null && image.getImageDataUrl().startsWith("data:image/png;base64,"));
    }

    @Test
    public void emptyTableWithOnlyImageDoesNotEmitEmptyTable() throws Exception {
        byte[] docx = buildDocxWithImageOnlyTable();
        DocumentModel model = extractor.extract(new ByteArrayInputStream(docx),
                ExtractContext.builder().fileName("flow.docx").build());

        List<DocumentBlock> blocks = model.getBlocks();
        assertFalse(blocks.stream().anyMatch(b -> b.getType() == BlockType.TABLE));
        assertEquals(1, blocks.stream().filter(b -> b.getType() == BlockType.IMAGE).count());
    }

    @Test
    public void singleColumnTreeTableBecomesCodeBlock() throws Exception {
        byte[] docx = buildDocxWithTreeTable();
        DocumentModel model = extractor.extract(new ByteArrayInputStream(docx),
                ExtractContext.builder().fileName("tree.docx").build());

        assertFalse(model.getBlocks().stream().anyMatch(b -> b.getType() == BlockType.TABLE));
        DocumentBlock code = model.getBlocks().stream()
                .filter(b -> b.getType() == BlockType.CODE)
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertTrue(code.getText().contains("WarningRule"));
        assertTrue(code.getText().contains("WarningTask"));
        assertTrue(code.getText().contains("\n"));
        assertFalse(code.getText().contains("Plain Text WarningRule"));
    }

    @Test
    public void detectsAsciiTree() {
        assertTrue(WordDocumentExtractor.looksLikeAsciiTree(
                "WarningRule\n|—— WR_LEVEL_ITEM\nL—— MonitorScene"));
        assertTrue(WordDocumentExtractor.isLayoutOrPreformattedTable(
                java.util.Collections.singletonList(
                        java.util.Collections.singletonList("WarningRule\n|—— child"))));
        assertFalse(WordDocumentExtractor.isLayoutOrPreformattedTable(
                java.util.Arrays.asList(
                        java.util.Arrays.asList("痛点", "具体表现"),
                        java.util.Arrays.asList("规则配置门槛高", "周期长"))));
    }

    private static byte[] buildDocxWithTreeTable() throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFTable table = doc.createTable(1, 1);
            XWPFTableRow row = table.getRow(0);
            XWPFParagraph p0 = row.getCell(0).getParagraphs().get(0);
            p0.createRun().setText("WarningRule (预警规则)");
            XWPFParagraph p1 = row.getCell(0).addParagraph();
            p1.createRun().setText("|—— WR_LEVEL_ITEM (BASE)");
            XWPFParagraph p2 = row.getCell(0).addParagraph();
            p2.createRun().setText("|—— WarningTask (任务实例)");
            XWPFParagraph p3 = row.getCell(0).addParagraph();
            p3.createRun().setText("|  |—— WarningBatch");
            XWPFParagraph p4 = row.getCell(0).addParagraph();
            p4.createRun().setText("L—— MonitorScene -> Voucher");
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] buildDocxWithParagraphImage() throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun run = p.createRun();
            run.setText("before");
            run.addPicture(new ByteArrayInputStream(TINY_PNG), Document.PICTURE_TYPE_PNG,
                    "tiny.png", Units.toEMU(20), Units.toEMU(20));
            doc.write(out);
            return out.toByteArray();
        } catch (InvalidFormatException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] buildDocxWithImageOnlyTable() throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFTable table = doc.createTable(1, 1);
            XWPFTableRow row = table.getRow(0);
            XWPFParagraph p = row.getCell(0).getParagraphs().get(0);
            XWPFRun run = p.createRun();
            run.addPicture(new ByteArrayInputStream(TINY_PNG), Document.PICTURE_TYPE_PNG,
                    "flow.png", Units.toEMU(40), Units.toEMU(40));
            doc.write(out);
            return out.toByteArray();
        } catch (InvalidFormatException e) {
            throw new IllegalStateException(e);
        }
    }
}
