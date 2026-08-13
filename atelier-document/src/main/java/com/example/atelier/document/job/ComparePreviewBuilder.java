package com.example.atelier.document.job;

import com.example.atelier.document.llm.LlmPreviewRefineService;
import com.example.atelier.document.llm.LlmStyleEnrichService;
import com.example.atelier.document.model.CompareOptions;
import com.example.atelier.document.model.DocumentModel;
import com.example.atelier.document.preview.AnchorAssigner;
import com.example.atelier.document.preview.PreviewDocument;
import com.example.atelier.document.preview.PreviewMapper;
import com.example.atelier.document.preview.PreviewOptions;
import com.example.atelier.document.preview.PreviewStructureNormalizer;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/**
 * 对比任务内复用预览管线，产出最终 PreviewDocument（含稳定 blockId）。
 */
@Service
public class ComparePreviewBuilder {

    private final PreviewMapper previewMapper;
    private final LlmStyleEnrichService styleEnrichService;
    private final LlmPreviewRefineService refineService;
    private final AnchorAssigner anchorAssigner = new AnchorAssigner();
    private final PreviewStructureNormalizer structureNormalizer = new PreviewStructureNormalizer();

    public ComparePreviewBuilder(PreviewMapper previewMapper,
                                 LlmStyleEnrichService styleEnrichService,
                                 LlmPreviewRefineService refineService) {
        this.previewMapper = previewMapper;
        this.styleEnrichService = styleEnrichService;
        this.refineService = refineService;
    }

    public PreviewDocument build(DocumentModel model,
                                 Path sourceFile,
                                 CompareOptions compareOptions,
                                 BooleanSupplier cancelled) {
        PreviewOptions previewOptions = toPreviewOptions(compareOptions);
        PreviewDocument preview = previewMapper.toPreview(model);
        preview = structureNormalizer.normalize(preview);
        preview = styleEnrichService.enrich(preview, previewOptions, cancelled);
        preview = structureNormalizer.normalize(preview);
        preview = refineService.refine(preview, model, sourceFile, previewOptions, cancelled);
        anchorAssigner.assign(preview);
        return preview;
    }

    private static PreviewOptions toPreviewOptions(CompareOptions options) {
        if (options == null) {
            return PreviewOptions.builder().build();
        }
        return PreviewOptions.builder()
                .enableLlmStyle(options.isEnableLlmStyle())
                .enableLlmRefine(options.isEnableLlmRefine())
                .llmProfileId(options.getLlmProfileId())
                .build();
    }
}
