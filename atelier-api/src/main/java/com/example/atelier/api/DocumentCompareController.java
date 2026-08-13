package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.document.job.CompareJobService;
import com.example.atelier.document.job.DocumentCompareProperties;
import com.example.atelier.document.model.CompareJob;
import com.example.atelier.document.model.CompareOptions;
import com.example.atelier.infra.exception.AtelierException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@RequestMapping("/api/v2/document-compare")
public class DocumentCompareController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CompareJobService jobService;
    private final DocumentCompareProperties properties;

    public DocumentCompareController(CompareJobService jobService, DocumentCompareProperties properties) {
        this.jobService = jobService;
        this.properties = properties;
    }

    @PostMapping("/jobs")
    public ApiResponse<CompareJob> createJob(
            @RequestPart("fileA") MultipartFile fileA,
            @RequestPart("fileB") MultipartFile fileB,
            @RequestParam(value = "options", required = false) String optionsJson) {
        validateFile(fileA, "fileA");
        validateFile(fileB, "fileB");
        CompareOptions options = parseOptions(optionsJson);
        try {
            CompareJob job = jobService.submit(
                    fileA.getOriginalFilename(),
                    fileA.getContentType(),
                    fileA.getInputStream(),
                    fileB.getOriginalFilename(),
                    fileB.getContentType(),
                    fileB.getInputStream(),
                    options);
            return ApiResponse.ok(job);
        } catch (AtelierException e) {
            throw e;
        } catch (Exception e) {
            throw new AtelierException("创建对比任务失败: " + e.getMessage(), e);
        }
    }

    @GetMapping("/jobs/{id}")
    public ApiResponse<CompareJob> getJob(@PathVariable String id) {
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

    private void validateFile(MultipartFile file, String label) {
        if (file == null || file.isEmpty()) {
            throw new AtelierException(label + " 不能为空");
        }
        if (file.getSize() > properties.getMaxFileBytes()) {
            throw new AtelierException(label + " 超过大小限制 "
                    + DocumentCompareProperties.formatBytes(file.getSize())
                    + "（上限 " + properties.formatMaxFileSize() + "）");
        }
    }

    private CompareOptions parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.trim().isEmpty()) {
            return CompareOptions.builder().build();
        }
        try {
            return MAPPER.readValue(optionsJson, CompareOptions.class);
        } catch (Exception e) {
            throw new AtelierException("options JSON 无效: " + e.getMessage());
        }
    }
}
