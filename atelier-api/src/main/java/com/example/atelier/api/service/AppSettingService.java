package com.example.atelier.api.service;

import com.example.atelier.api.dto.SemanticLlmConfigRequest;
import com.example.atelier.api.dto.SemanticLlmConfigResponse;
import com.example.atelier.api.dto.SemanticLlmProfileRequest;
import com.example.atelier.api.dto.SemanticLlmProfileResponse;
import com.example.atelier.api.dto.SemanticLlmProfilesResponse;
import com.example.atelier.api.dto.SemanticLlmProfilesSaveRequest;
import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.domain.settings.SemanticLlmProfile;
import com.example.atelier.domain.settings.SemanticLlmProfilesSettings;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.warning.evaluator.LlmChatClient;
import com.example.atelier.warning.evaluator.SemanticLlmProviders;
import com.example.atelier.warning.service.SemanticLlmConfigLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AppSettingService {

    private final SemanticLlmConfigLoader configLoader;
    private final LlmChatClient chatClient = new LlmChatClient();

    public AppSettingService(SemanticLlmConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    public SemanticLlmConfig getSemanticLlmConfig() {
        return configLoader.load();
    }

    public SemanticLlmConfigResponse getSemanticLlmConfigForResponse() {
        return toLegacyResponse(configLoader.load());
    }

    public SemanticLlmProfilesResponse getLlmProfiles() {
        return toProfilesResponse(configLoader.loadProfilesSettings());
    }

    @Transactional
    public SemanticLlmProfilesResponse saveLlmProfiles(SemanticLlmProfilesSaveRequest request) {
        if (request == null || request.getProfiles() == null || request.getProfiles().isEmpty()) {
            throw new AtelierException("至少保留一套 LLM 配置");
        }
        SemanticLlmProfilesSettings existing = configLoader.loadProfilesSettings();
        Map<String, SemanticLlmProfile> existingById = existing.getProfiles().stream()
                .collect(Collectors.toMap(SemanticLlmProfile::getId, profile -> profile, (a, b) -> a));

        List<SemanticLlmProfile> profiles = new ArrayList<>();
        for (SemanticLlmProfileRequest item : request.getProfiles()) {
            profiles.add(mergeProfile(item, existingById));
        }
        SemanticLlmProfilesSettings settings = SemanticLlmProfilesSettings.builder()
                .activeProfileId(request.getActiveProfileId())
                .profiles(profiles)
                .build();
        configLoader.saveProfilesSettings(settings);
        return toProfilesResponse(configLoader.loadProfilesSettings());
    }

    @Transactional
    public void saveSemanticLlmConfig(SemanticLlmConfigRequest request) {
        SemanticLlmProfilesSettings settings = configLoader.loadProfilesSettings();
        String activeId = settings.getActiveProfileId();
        SemanticLlmProfile active = settings.getProfiles().stream()
                .filter(profile -> activeId != null && activeId.equals(profile.getId()))
                .findFirst()
                .orElse(settings.getProfiles().get(0));

        SemanticLlmProfile merged = mergeLegacyRequest(active, request);
        List<SemanticLlmProfile> profiles = settings.getProfiles().stream()
                .map(profile -> profile.getId().equals(merged.getId()) ? merged : profile)
                .collect(Collectors.toList());
        configLoader.saveProfilesSettings(SemanticLlmProfilesSettings.builder()
                .activeProfileId(settings.getActiveProfileId())
                .profiles(profiles)
                .build());
    }

    public boolean testConnection(SemanticLlmConfigRequest request) {
        SemanticLlmConfig config = buildConfigForTest(request, null);
        chatClient.chat(config, "Reply with OK only.", "ping");
        return true;
    }

    public boolean testProfile(SemanticLlmProfileRequest request) {
        SemanticLlmProfilesSettings settings = configLoader.loadProfilesSettings();
        Map<String, SemanticLlmProfile> existingById = settings.getProfiles().stream()
                .collect(Collectors.toMap(SemanticLlmProfile::getId, profile -> profile, (a, b) -> a));
        SemanticLlmProfile profile = mergeProfile(request, existingById);
        SemanticLlmConfig config = profile.toConfig();
        SemanticLlmProviders.applyProviderDefaults(config);
        if (config.getModel() == null || config.getModel().trim().isEmpty()) {
            config.setModel("gpt-4o-mini");
        }
        chatClient.chat(config, "Reply with OK only.", "ping");
        return true;
    }

    private SemanticLlmProfile mergeLegacyRequest(SemanticLlmProfile existing, SemanticLlmConfigRequest request) {
        String apiKey = request.getApiKey() != null && !request.getApiKey().trim().isEmpty()
                ? request.getApiKey().trim()
                : existing.getApiKey();
        return SemanticLlmProfile.builder()
                .id(existing.getId())
                .name(existing.getName())
                .enabled(request.getEnabled() != null ? request.getEnabled() : existing.isEnabled())
                .provider(firstNonBlank(request.getProvider(), existing.getProvider()))
                .protocol(firstNonBlank(request.getProtocol(), existing.getProtocol()))
                .apiKey(apiKey)
                .model(firstNonBlank(request.getModel(), existing.getModel()))
                .baseUrl(firstNonBlank(request.getBaseUrl(), existing.getBaseUrl()))
                .timeoutSeconds(request.getTimeoutSeconds() != null
                        ? request.getTimeoutSeconds()
                        : existing.getTimeoutSeconds())
                .build();
    }

    private SemanticLlmProfile mergeProfile(SemanticLlmProfileRequest request,
            Map<String, SemanticLlmProfile> existingById) {
        String id = request.getId() != null && !request.getId().trim().isEmpty()
                ? request.getId().trim()
                : SemanticLlmConfigLoader.generateProfileId();
        SemanticLlmProfile existing = existingById.get(id);
        String apiKey = request.getApiKey() != null && !request.getApiKey().trim().isEmpty()
                ? request.getApiKey().trim()
                : existing != null ? existing.getApiKey() : null;
        return SemanticLlmProfile.builder()
                .id(id)
                .name(firstNonBlank(request.getName(), existing != null ? existing.getName() : null, "未命名"))
                .enabled(request.getEnabled() != null
                        ? request.getEnabled()
                        : existing != null && existing.isEnabled())
                .provider(firstNonBlank(request.getProvider(), existing != null ? existing.getProvider() : null))
                .protocol(firstNonBlank(request.getProtocol(),
                        existing != null ? existing.getProtocol() : null))
                .apiKey(apiKey)
                .model(firstNonBlank(request.getModel(), existing != null ? existing.getModel() : null))
                .baseUrl(firstNonBlank(request.getBaseUrl(), existing != null ? existing.getBaseUrl() : null))
                .timeoutSeconds(request.getTimeoutSeconds() != null
                        ? request.getTimeoutSeconds()
                        : existing != null ? existing.getTimeoutSeconds() : 120)
                .build();
    }

    private SemanticLlmConfig buildConfigForTest(SemanticLlmConfigRequest request, String profileId) {
        SemanticLlmConfig existing = profileId != null
                ? configLoader.loadProfile(profileId)
                : configLoader.load();
        String apiKey = request.getApiKey() != null && !request.getApiKey().trim().isEmpty()
                ? request.getApiKey().trim()
                : existing.getApiKey();
        SemanticLlmConfig config = SemanticLlmConfig.builder()
                .enabled(true)
                .provider(firstNonBlank(request.getProvider(), existing.getProvider()))
                .protocol(firstNonBlank(request.getProtocol(), existing.getProtocol()))
                .apiKey(apiKey)
                .model(firstNonBlank(request.getModel(), existing.getModel()))
                .baseUrl(firstNonBlank(request.getBaseUrl(), existing.getBaseUrl()))
                .timeoutSeconds(request.getTimeoutSeconds() != null
                        ? request.getTimeoutSeconds()
                        : Optional.ofNullable(existing.getTimeoutSeconds()).orElse(120))
                .build();
        SemanticLlmProviders.applyProviderDefaults(config);
        if (config.getModel() == null || config.getModel().trim().isEmpty()) {
            config.setModel("gpt-4o-mini");
        }
        return config;
    }

    private SemanticLlmProfilesResponse toProfilesResponse(SemanticLlmProfilesSettings settings) {
        List<SemanticLlmProfileResponse> profiles = settings.getProfiles().stream()
                .map(this::toProfileResponse)
                .collect(Collectors.toList());
        return SemanticLlmProfilesResponse.builder()
                .activeProfileId(settings.getActiveProfileId())
                .profiles(profiles)
                .build();
    }

    private SemanticLlmProfileResponse toProfileResponse(SemanticLlmProfile profile) {
        return SemanticLlmProfileResponse.builder()
                .id(profile.getId())
                .name(profile.getName())
                .enabled(profile.isEnabled())
                .provider(profile.getProvider())
                .protocol(profile.getProtocol())
                .model(profile.getModel())
                .baseUrl(profile.getBaseUrl())
                .timeoutSeconds(profile.getTimeoutSeconds())
                .apiKeyConfigured(profile.getApiKey() != null && !profile.getApiKey().trim().isEmpty())
                .build();
    }

    private SemanticLlmConfigResponse toLegacyResponse(SemanticLlmConfig config) {
        return SemanticLlmConfigResponse.builder()
                .enabled(config.isEnabled())
                .provider(config.getProvider())
                .protocol(config.getProtocol())
                .model(config.getModel())
                .baseUrl(config.getBaseUrl())
                .timeoutSeconds(config.getTimeoutSeconds())
                .apiKeyConfigured(config.getApiKey() != null && !config.getApiKey().trim().isEmpty())
                .build();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
