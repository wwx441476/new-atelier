package com.example.atelier.warning.service;

import com.example.atelier.domain.settings.SemanticLlmConfig;
import com.example.atelier.domain.settings.SemanticLlmProfile;
import com.example.atelier.domain.settings.SemanticLlmProfilesSettings;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.persistence.entity.AppSettingEntity;
import com.example.atelier.infra.persistence.jpa.AppSettingJpaRepository;
import com.example.atelier.warning.evaluator.SemanticLlmProviders;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SemanticLlmConfigLoader {

    public static final String SEMANTIC_LLM_KEY = "semantic.llm";
    public static final String SEMANTIC_LLM_PROFILES_KEY = "semantic.llm.profiles";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Object migrateLock = new Object();
    private final AppSettingJpaRepository repository;

    public SemanticLlmConfigLoader(AppSettingJpaRepository repository) {
        this.repository = repository;
    }

    /** 语义检测默认使用的工作区激活档案。 */
    public SemanticLlmConfig load() {
        return loadProfile(null);
    }

    public SemanticLlmConfig loadProfile(String profileId) {
        SemanticLlmProfilesSettings settings = loadProfilesSettings();
        SemanticLlmProfile profile = resolveProfile(settings, profileId);
        if (profile == null) {
            return defaultConfig();
        }
        SemanticLlmConfig config = profile.toConfig();
        SemanticLlmProviders.applyProviderDefaults(config);
        return config;
    }

    public SemanticLlmProfilesSettings loadProfilesSettings() {
        Optional<AppSettingEntity> profilesEntity = repository.findById(SEMANTIC_LLM_PROFILES_KEY);
        if (profilesEntity.isPresent()) {
            return normalizeProfiles(fromProfilesJson(profilesEntity.get().getSettingValue()));
        }
        synchronized (migrateLock) {
            profilesEntity = repository.findById(SEMANTIC_LLM_PROFILES_KEY);
            if (profilesEntity.isPresent()) {
                return normalizeProfiles(fromProfilesJson(profilesEntity.get().getSettingValue()));
            }
            return migrateFromLegacyConfig();
        }
    }

    public void saveProfilesSettings(SemanticLlmProfilesSettings settings) {
        SemanticLlmProfilesSettings normalized = normalizeProfiles(settings);
        AppSettingEntity entity = repository.findById(SEMANTIC_LLM_PROFILES_KEY)
                .orElse(AppSettingEntity.builder().settingKey(SEMANTIC_LLM_PROFILES_KEY).build());
        entity.setSettingValue(toProfilesJson(normalized));
        entity.setModifyTime(LocalDateTime.now());
        repository.save(entity);
        syncLegacyActiveConfig(normalized);
    }

    public Optional<SemanticLlmProfile> findProfile(String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) {
            return Optional.empty();
        }
        return loadProfilesSettings().getProfiles().stream()
                .filter(profile -> profileId.equals(profile.getId()))
                .findFirst();
    }

    private SemanticLlmProfile resolveProfile(SemanticLlmProfilesSettings settings, String profileId) {
        if (settings.getProfiles() == null || settings.getProfiles().isEmpty()) {
            return null;
        }
        if (profileId != null && !profileId.trim().isEmpty()) {
            return settings.getProfiles().stream()
                    .filter(profile -> profileId.equals(profile.getId()))
                    .findFirst()
                    .orElseThrow(() -> new AtelierException("LLM 配置不存在: " + profileId));
        }
        String activeId = settings.getActiveProfileId();
        if (activeId != null) {
            for (SemanticLlmProfile profile : settings.getProfiles()) {
                if (activeId.equals(profile.getId())) {
                    return profile;
                }
            }
        }
        return settings.getProfiles().get(0);
    }

    private SemanticLlmProfilesSettings migrateFromLegacyConfig() {
        Optional<AppSettingEntity> legacy = repository.findById(SEMANTIC_LLM_KEY);
        SemanticLlmProfilesSettings settings;
        if (legacy.isPresent()) {
            SemanticLlmConfig config = fromLegacyJson(legacy.get().getSettingValue());
            SemanticLlmProfile profile = SemanticLlmProfile.fromConfig("default", "默认", config);
            settings = SemanticLlmProfilesSettings.builder()
                    .activeProfileId(profile.getId())
                    .profiles(new ArrayList<>(Collections.singletonList(profile)))
                    .build();
        } else {
            SemanticLlmProfile profile = SemanticLlmProfile.fromConfig(
                    "default", "默认", defaultConfig());
            settings = SemanticLlmProfilesSettings.builder()
                    .activeProfileId(profile.getId())
                    .profiles(new ArrayList<>(Collections.singletonList(profile)))
                    .build();
        }
        try {
            saveProfilesSettings(settings);
        } catch (DataIntegrityViolationException ex) {
            return normalizeProfiles(fromProfilesJson(
                    repository.findById(SEMANTIC_LLM_PROFILES_KEY)
                            .map(AppSettingEntity::getSettingValue)
                            .orElse(null)));
        }
        return settings;
    }

    private SemanticLlmProfilesSettings normalizeProfiles(SemanticLlmProfilesSettings settings) {
        if (settings == null) {
            settings = SemanticLlmProfilesSettings.builder().profiles(new ArrayList<>()).build();
        }
        List<SemanticLlmProfile> profiles = settings.getProfiles() != null
                ? new ArrayList<>(settings.getProfiles())
                : new ArrayList<>();
        if (profiles.isEmpty()) {
            profiles.add(SemanticLlmProfile.fromConfig("default", "默认", defaultConfig()));
        }
        for (SemanticLlmProfile profile : profiles) {
            if (profile.getId() == null || profile.getId().trim().isEmpty()) {
                profile.setId(generateProfileId());
            }
            if (profile.getName() == null || profile.getName().trim().isEmpty()) {
                profile.setName("未命名");
            }
            if (profile.getTimeoutSeconds() == null || profile.getTimeoutSeconds() <= 0) {
                profile.setTimeoutSeconds(30);
            }
            SemanticLlmProviders.applyProviderDefaults(profile.toConfig());
            profile.setProvider(profile.toConfig().getProvider());
            profile.setModel(profile.toConfig().getModel());
            profile.setBaseUrl(profile.toConfig().getBaseUrl());
        }
        final String candidateActiveId = settings.getActiveProfileId();
        boolean activeValid = candidateActiveId != null
                && profiles.stream().anyMatch(profile -> candidateActiveId.equals(profile.getId()));
        String activeProfileId = activeValid ? candidateActiveId : profiles.get(0).getId();
        return SemanticLlmProfilesSettings.builder()
                .activeProfileId(activeProfileId)
                .profiles(profiles)
                .build();
    }

    private void syncLegacyActiveConfig(SemanticLlmProfilesSettings settings) {
        SemanticLlmProfile active = resolveProfile(settings, null);
        if (active == null) {
            return;
        }
        AppSettingEntity entity = repository.findById(SEMANTIC_LLM_KEY)
                .orElse(AppSettingEntity.builder().settingKey(SEMANTIC_LLM_KEY).build());
        entity.setSettingValue(toLegacyJson(active.toConfig()));
        entity.setModifyTime(LocalDateTime.now());
        repository.save(entity);
    }

    private SemanticLlmConfig defaultConfig() {
        SemanticLlmConfig config = SemanticLlmConfig.builder()
                .enabled(false)
                .provider(SemanticLlmProviders.OPENAI)
                .model("gpt-4o-mini")
                .baseUrl("https://api.openai.com/v1")
                .timeoutSeconds(30)
                .build();
        SemanticLlmProviders.applyProviderDefaults(config);
        return config;
    }

    private SemanticLlmConfig fromLegacyJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return defaultConfig();
        }
        try {
            return MAPPER.readValue(json, SemanticLlmConfig.class);
        } catch (JsonProcessingException e) {
            throw new AtelierException("LLM 配置反序列化失败", e);
        }
    }

    private SemanticLlmProfilesSettings fromProfilesJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return SemanticLlmProfilesSettings.builder().profiles(new ArrayList<>()).build();
        }
        try {
            return MAPPER.readValue(json, SemanticLlmProfilesSettings.class);
        } catch (JsonProcessingException e) {
            throw new AtelierException("LLM 多配置反序列化失败", e);
        }
    }

    private String toLegacyJson(SemanticLlmConfig config) {
        try {
            return MAPPER.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new AtelierException("LLM 配置序列化失败", e);
        }
    }

    private String toProfilesJson(SemanticLlmProfilesSettings settings) {
        try {
            return MAPPER.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            throw new AtelierException("LLM 多配置序列化失败", e);
        }
    }

    public static String generateProfileId() {
        return "llm-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
