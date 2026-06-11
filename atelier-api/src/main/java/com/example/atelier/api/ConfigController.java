package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.api.dto.ConfigExportRequest;
import com.example.atelier.api.dto.ConfigImportRequest;
import com.example.atelier.api.service.ConfigBundleService;
import com.example.atelier.domain.config.AtelierConfigBundle;
import com.example.atelier.domain.config.ConfigImportOptions;
import com.example.atelier.domain.config.ConfigImportResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全量配置导出 / 导入 API。
 */
@RestController
@RequestMapping("/api/v2/config")
public class ConfigController {

    private final ConfigBundleService configBundleService;

    public ConfigController(ConfigBundleService configBundleService) {
        this.configBundleService = configBundleService;
    }

    @GetMapping("/export")
    public ApiResponse<AtelierConfigBundle> export(
            @RequestParam(value = "includeSecrets", defaultValue = "true") boolean includeSecrets) {
        return ApiResponse.ok(configBundleService.exportBundle(
                includeSecrets, ConfigImportOptions.builder().build()));
    }

    @PostMapping("/export")
    public ApiResponse<AtelierConfigBundle> exportWithOptions(@RequestBody ConfigExportRequest request) {
        boolean includeSecrets = request == null || request.isIncludeSecrets();
        ConfigImportOptions options = request != null && request.getOptions() != null
                ? request.getOptions()
                : ConfigImportOptions.builder().build();
        return ApiResponse.ok(configBundleService.exportBundle(includeSecrets, options));
    }

    @PostMapping("/import")
    public ApiResponse<ConfigImportResult> importConfig(@RequestBody ConfigImportRequest request) {
        AtelierConfigBundle bundle = request != null ? request.getBundle() : null;
        ConfigImportOptions options = request != null && request.getOptions() != null
                ? request.getOptions()
                : ConfigImportOptions.builder().build();
        return ApiResponse.ok(configBundleService.importBundle(bundle, options));
    }
}
