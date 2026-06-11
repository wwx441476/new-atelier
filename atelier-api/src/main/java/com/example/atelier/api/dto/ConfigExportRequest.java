package com.example.atelier.api.dto;

import com.example.atelier.domain.config.ConfigImportOptions;
import lombok.Data;

@Data
public class ConfigExportRequest {

    private boolean includeSecrets = true;

    private ConfigImportOptions options;
}
