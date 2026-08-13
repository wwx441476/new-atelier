package com.example.atelier.document.extract;

import com.example.atelier.document.model.BlockMeta;
import com.example.atelier.document.model.BlockType;
import com.example.atelier.document.model.DocumentBlock;
import com.example.atelier.document.model.DocumentModel;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Order(40)
public class PptDocumentExtractor implements DocumentExtractor {

    @Override
    public boolean supports(String mimeType, String fileName) {
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return mime.contains("presentationml") || name.endsWith(".pptx");
    }

    @Override
    public DocumentModel extract(InputStream input, ExtractContext context) throws Exception {
        BlockIds ids = new BlockIds("pptx");
        List<DocumentBlock> blocks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int maxPages = context.getMaxPages() > 0 ? context.getMaxPages() : 50;
        try (XMLSlideShow ppt = new XMLSlideShow(input)) {
            List<XSLFSlide> slides = ppt.getSlides();
            int limit = Math.min(slides.size(), maxPages);
            if (slides.size() > maxPages) {
                warnings.add("PPT 页数超过上限 " + maxPages + "，已截断");
            }
            for (int i = 0; i < limit; i++) {
                XSLFSlide slide = slides.get(i);
                StringBuilder sb = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        String text = ((XSLFTextShape) shape).getText();
                        if (text != null && !text.trim().isEmpty()) {
                            if (sb.length() > 0) {
                                sb.append('\n');
                            }
                            sb.append(text.trim());
                        }
                    }
                }
                if (sb.length() == 0) {
                    continue;
                }
                blocks.add(DocumentBlock.builder()
                        .id(ids.next())
                        .type(BlockType.SLIDE)
                        .text(sb.toString())
                        .meta(BlockMeta.builder().slideIndex(i + 1).page(i + 1).build())
                        .build());
            }
        }
        return DocumentModel.builder()
                .fileName(context.getFileName())
                .mimeType("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                .blocks(blocks)
                .warnings(warnings)
                .build();
    }
}
