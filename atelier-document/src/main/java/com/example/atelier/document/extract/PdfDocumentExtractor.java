package com.example.atelier.document.extract;

import com.example.atelier.document.model.BlockMeta;
import com.example.atelier.document.model.BlockType;
import com.example.atelier.document.model.DocumentBlock;
import com.example.atelier.document.model.DocumentModel;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Component
@Order(50)
public class PdfDocumentExtractor implements DocumentExtractor {

    private static final int MIN_CHARS_PER_PAGE = 40;

    @Override
    public boolean supports(String mimeType, String fileName) {
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return "application/pdf".equals(mime) || name.endsWith(".pdf");
    }

    @Override
    public DocumentModel extract(InputStream input, ExtractContext context) throws Exception {
        BlockIds ids = new BlockIds("pdf");
        List<DocumentBlock> blocks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean ocrUsed = false;
        int maxPages = context.getMaxPages() > 0 ? context.getMaxPages() : 200;

        try (PDDocument document = openPdf(input, context.getSourcePath())) {
            int pageCount = document.getNumberOfPages();
            int limit = Math.min(pageCount, maxPages);
            if (pageCount > maxPages) {
                warnings.add("PDF 页数超过上限 " + maxPages + "，已截断");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder allText = new StringBuilder();
            List<String> pageTexts = new ArrayList<>();
            for (int page = 1; page <= limit; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);
                pageTexts.add(pageText == null ? "" : pageText.trim());
                allText.append(pageTexts.get(page - 1));
            }

            boolean sparse = allText.toString().replaceAll("\\s+", "").length() < MIN_CHARS_PER_PAGE * Math.max(1, limit / 2);
            if (sparse && context.getOcrService() != null && context.getOcrService().isAvailable(context.getOptions())) {
                warnings.add("检测到疑似扫描 PDF，已启用多模态 OCR（可能不准确）");
                ocrUsed = true;
                PDFRenderer renderer = new PDFRenderer(document);
                int ocrPages = Math.min(limit, 8);
                int ocrOk = 0;
                for (int i = 0; i < ocrPages; i++) {
                    try {
                        BufferedImage image = renderer.renderImageWithDPI(i, 120, ImageType.RGB);
                        String dataUrl = toPngDataUrl(image);
                        String ocrText = context.getOcrService().ocrImage(dataUrl, context.getOptions());
                        if (ocrText != null && !ocrText.trim().isEmpty()) {
                            splitParagraphs(blocks, ids, ocrText.trim(), i + 1, 0.7);
                            ocrOk++;
                        }
                    } catch (Exception ocrEx) {
                        warnings.add("第 " + (i + 1) + " 页 OCR 失败: " + ocrEx.getMessage());
                    }
                }
                if (ocrOk == 0) {
                    // OCR 全失败时回退文本层，避免整任务失败
                    warnings.add("OCR 未成功提取文字，已回退到 PDF 文本层（可能为空）");
                    ocrUsed = false;
                    for (int i = 0; i < pageTexts.size(); i++) {
                        String pageText = pageTexts.get(i);
                        if (!pageText.isEmpty()) {
                            splitParagraphs(blocks, ids, pageText, i + 1, null);
                        }
                    }
                } else if (limit > ocrPages) {
                    warnings.add("OCR 仅处理前 " + ocrPages + " 页");
                }
            } else {
                if (sparse) {
                    warnings.add("PDF 文本层稀少且 OCR 不可用（请配置 LLM），结果可能不完整");
                }
                for (int i = 0; i < pageTexts.size(); i++) {
                    String pageText = pageTexts.get(i);
                    if (pageText.isEmpty()) {
                        continue;
                    }
                    splitParagraphs(blocks, ids, pageText, i + 1, null);
                }
            }
        }

        return DocumentModel.builder()
                .fileName(context.getFileName())
                .mimeType("application/pdf")
                .blocks(blocks)
                .warnings(warnings)
                .ocrUsed(ocrUsed)
                .build();
    }

    private static void splitParagraphs(List<DocumentBlock> blocks, BlockIds ids, String text,
                                        int page, Double confidence) {
        String[] parts = text.split("\\R{2,}");
        for (String part : parts) {
            String trimmed = part.replace('\r', '\n').trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            blocks.add(DocumentBlock.builder()
                    .id(ids.next())
                    .type(BlockType.PARAGRAPH)
                    .text(trimmed)
                    .meta(BlockMeta.builder().page(page).ocrConfidence(confidence).build())
                    .build());
        }
    }

    private static PDDocument openPdf(InputStream input, Path sourcePath) throws Exception {
        if (sourcePath != null) {
            return PDDocument.load(sourcePath.toFile());
        }
        return PDDocument.load(input);
    }

    private static String toPngDataUrl(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        return "data:image/png;base64," + b64;
    }
}
