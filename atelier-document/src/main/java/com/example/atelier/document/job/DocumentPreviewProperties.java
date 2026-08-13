package com.example.atelier.document.job;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "atelier.document-preview")
public class DocumentPreviewProperties {
    /** 单文件最大字节数，默认 200MB */
    private long maxFileBytes = 200L * 1024 * 1024;
    /** 任务 TTL（毫秒），默认 1 小时 */
    private long jobTtlMillis = 60L * 60 * 1000;
    private int maxConcurrentJobs = 2;

    public String formatMaxFileSize() {
        return DocumentCompareProperties.formatBytes(maxFileBytes);
    }
}
