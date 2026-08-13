package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.document.job.DocumentCompareProperties;
import com.example.atelier.document.job.DocumentPreviewProperties;
import com.example.atelier.document.job.PreviewJobService;
import com.example.atelier.document.preview.PreviewJob;
import com.example.atelier.document.preview.PreviewOptions;
import com.example.atelier.infra.exception.AtelierException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v2/document-preview")
public class DocumentPreviewController {

    private final PreviewJobService jobService;
    private final DocumentPreviewProperties properties;

    public DocumentPreviewController(PreviewJobService jobService, DocumentPreviewProperties properties) {
        this.jobService = jobService;
        this.properties = properties;
    }

    @PostMapping("/jobs")
    public ApiResponse<PreviewJob> createJob(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "enableLlmStyle", required = false, defaultValue = "true") boolean enableLlmStyle,
            @RequestParam(value = "enableLlmRefine", required = false, defaultValue = "true") boolean enableLlmRefine,
            @RequestParam(value = "llmProfileId", required = false) String llmProfileId) {
        validateFile(file);
        try {
            PreviewOptions options = PreviewOptions.builder()
                    .enableLlmStyle(enableLlmStyle)
                    .enableLlmRefine(enableLlmRefine)
                    .llmProfileId(llmProfileId)
                    .build();
            PreviewJob job = jobService.submit(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getInputStream(),
                    options);
            return ApiResponse.ok(job);
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("创建预览任务失败: " + e.getMessage(), e);
        }
    }

    @PostMapping("/jobs/{id}/cancel")
    public ApiResponse<PreviewJob> cancelJob(@PathVariable String id) {
        return jobService.cancel(id)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("任务不存在或已过期: " + id));
    }

    @GetMapping("/jobs/{id}")
    public ApiResponse<PreviewJob> getJob(@PathVariable String id) {
        return jobService.get(id)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("任务不存在或已过期: " + id));
    }

    @DeleteMapping("/jobs/{id}")
    public ApiResponse<Void> deleteJob(@PathVariable String id) {
        if (!jobService.delete(id)) {
            return ApiResponse.fail("任务不存在或已过期: " + id);
        }
        return ApiResponse.ok(null);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AtelierException("file 不能为空");
        }
        if (file.getSize() > properties.getMaxFileBytes()) {
            throw new AtelierException("file 超过大小限制 "
                    + DocumentCompareProperties.formatBytes(file.getSize())
                    + "（上限 " + properties.formatMaxFileSize() + "）");
        }
    }
}
