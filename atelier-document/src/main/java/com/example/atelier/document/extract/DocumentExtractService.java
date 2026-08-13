package com.example.atelier.document.extract;

import com.example.atelier.document.job.DocumentCompareProperties;
import com.example.atelier.document.llm.LlmOcrService;
import com.example.atelier.document.model.CompareOptions;
import com.example.atelier.document.model.DocumentModel;
import com.example.atelier.infra.exception.AtelierException;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class DocumentExtractService {

    private final List<DocumentExtractor> extractors;
    private final TypeDetector typeDetector;
    private final LlmOcrService ocrService;
    private final DocumentCompareProperties properties;

    public DocumentExtractService(List<DocumentExtractor> extractors,
                                  TypeDetector typeDetector,
                                  LlmOcrService ocrService,
                                  DocumentCompareProperties properties) {
        this.extractors = extractors;
        this.typeDetector = typeDetector;
        this.ocrService = ocrService;
        this.properties = properties;
    }

    public DocumentModel extract(Path file, String fileName, String contentType, CompareOptions options) {
        try (InputStream raw = Files.newInputStream(file);
             BufferedInputStream input = new BufferedInputStream(raw)) {
            input.mark((int) Math.min(Files.size(file) + 1, 2_000_000L));
            String mime = typeDetector.detect(input, fileName, contentType);
            try {
                input.reset();
            } catch (Exception ignored) {
                // fall through with fresh stream below
            }

            ExtractContext context = ExtractContext.builder()
                    .fileName(fileName)
                    .declaredContentType(mime)
                    .options(options == null ? CompareOptions.builder().build() : options)
                    .ocrService(ocrService)
                    .maxPages(properties.getMaxPages())
                    .maxSheets(properties.getMaxSheets())
                    .sourcePath(file)
                    .build();

            for (DocumentExtractor extractor : extractors) {
                if (extractor.supports(mime, fileName)) {
                    try (InputStream stream = Files.newInputStream(file)) {
                        return extractor.extract(stream, context);
                    }
                }
            }
            throw new AtelierException("不支持的文件类型: " + mime + " (" + fileName + ")");
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("文档解析失败: " + e.getMessage(), e);
        }
    }
}
