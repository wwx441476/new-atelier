package com.example.atelier.api.dto;

import com.example.atelier.domain.config.AtelierConfigBundle;
import com.example.atelier.domain.config.ConfigImportOptions;
import lombok.Data;

/**
 * 配置导入请求 — JSON 包 + 导入选项。
 */
@Data
public class ConfigImportRequest {

    private AtelierConfigBundle bundle;

    private ConfigImportOptions options;
}
