package com.example.atelier.document.extract;

import com.example.atelier.document.llm.LlmOcrService;
import com.example.atelier.document.model.CompareOptions;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;

@Data
@Builder
public class ExtractContext {
    private String fileName;
    private String declaredContentType;
    private CompareOptions options;
    private LlmOcrService ocrService;
    private int maxPages;
    private int maxSheets;
    /** 落盘路径；大文件抽取优先走文件而非整文件读入内存 */
    private Path sourcePath;
}
