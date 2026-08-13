package com.example.atelier.document.extract;

import com.example.atelier.document.model.BlockMeta;
import com.example.atelier.document.model.BlockType;
import com.example.atelier.document.model.DocumentBlock;
import com.example.atelier.document.model.DocumentModel;
import com.example.atelier.infra.exception.AtelierException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Component
@Order(60)
public class ImageDocumentExtractor implements DocumentExtractor {

    @Override
    public boolean supports(String mimeType, String fileName) {
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return mime.startsWith("image/")
                || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp");
    }

    @Override
    public DocumentModel extract(InputStream input, ExtractContext context) throws Exception {
        byte[] bytes = TextDocumentExtractor.readAll(input);
        String mime = resolveMime(context);
        List<String> warnings = new ArrayList<>();
        if (context.getOcrService() == null || !context.getOcrService().isAvailable(context.getOptions())) {
            throw new AtelierException("图片/扫描件对比需要配置可用的多模态 LLM（OCR）");
        }
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        String ocrText = context.getOcrService().ocrImage(dataUrl, context.getOptions());
        warnings.add("图片内容由多模态 LLM OCR 抽取，可能不准确");
        BlockIds ids = new BlockIds("img");
        List<DocumentBlock> blocks = new ArrayList<>();
        if (ocrText != null && !ocrText.trim().isEmpty()) {
            String[] parts = ocrText.split("\\R{2,}");
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                blocks.add(DocumentBlock.builder()
                        .id(ids.next())
                        .type(BlockType.PARAGRAPH)
                        .text(trimmed)
                        .meta(BlockMeta.builder().ocrConfidence(0.7).build())
                        .build());
            }
        } else {
            blocks.add(DocumentBlock.builder()
                    .id(ids.next())
                    .type(BlockType.IMAGE_CAPTION)
                    .text("[empty OCR result]")
                    .build());
            warnings.add("OCR 未识别到文字");
        }
        return DocumentModel.builder()
                .fileName(context.getFileName())
                .mimeType(mime)
                .blocks(blocks)
                .warnings(warnings)
                .ocrUsed(true)
                .build();
    }

    private static String resolveMime(ExtractContext context) {
        String declared = context.getDeclaredContentType();
        if (declared != null && declared.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return declared.split(";")[0].trim().toLowerCase(Locale.ROOT);
        }
        String name = context.getFileName() == null ? "" : context.getFileName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
