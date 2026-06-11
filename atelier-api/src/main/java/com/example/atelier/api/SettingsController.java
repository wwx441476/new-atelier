package com.example.atelier.api;

import com.example.atelier.api.dto.ApiResponse;
import com.example.atelier.api.dto.SemanticLlmConfigRequest;
import com.example.atelier.api.dto.SemanticLlmConfigResponse;
import com.example.atelier.api.dto.SemanticLlmProfileRequest;
import com.example.atelier.api.dto.SemanticLlmProfilesResponse;
import com.example.atelier.api.dto.SemanticLlmProfilesSaveRequest;
import com.example.atelier.api.service.AppSettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/settings")
public class SettingsController {

    private final AppSettingService appSettingService;

    public SettingsController(AppSettingService appSettingService) {
        this.appSettingService = appSettingService;
    }

    @GetMapping("/semantic-llm")
    public ApiResponse<SemanticLlmConfigResponse> getSemanticLlm() {
        return ApiResponse.ok(appSettingService.getSemanticLlmConfigForResponse());
    }

    @PutMapping("/semantic-llm")
    public ApiResponse<SemanticLlmConfigResponse> saveSemanticLlm(@RequestBody SemanticLlmConfigRequest request) {
        appSettingService.saveSemanticLlmConfig(request);
        return ApiResponse.ok(appSettingService.getSemanticLlmConfigForResponse());
    }

    @PostMapping("/semantic-llm/test")
    public ApiResponse<Map<String, Object>> testSemanticLlm(@RequestBody SemanticLlmConfigRequest request) {
        boolean ok = appSettingService.testConnection(request);
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        result.put("message", ok ? "连接成功" : "连接失败");
        return ApiResponse.ok(result);
    }

    @GetMapping("/llm-profiles")
    public ApiResponse<SemanticLlmProfilesResponse> getLlmProfiles() {
        return ApiResponse.ok(appSettingService.getLlmProfiles());
    }

    @PutMapping("/llm-profiles")
    public ApiResponse<SemanticLlmProfilesResponse> saveLlmProfiles(
            @RequestBody SemanticLlmProfilesSaveRequest request) {
        return ApiResponse.ok(appSettingService.saveLlmProfiles(request));
    }

    @PostMapping("/llm-profiles/test")
    public ApiResponse<Map<String, Object>> testLlmProfile(@RequestBody SemanticLlmProfileRequest request) {
        boolean ok = appSettingService.testProfile(request);
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        result.put("message", ok ? "连接成功" : "连接失败");
        return ApiResponse.ok(result);
    }
}
