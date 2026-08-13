package com.example.atelier.document.job;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "atelier.document-compare")
public class DocumentCompareProperties {
    /** 单文件最大字节数，默认 200MB */
    private long maxFileBytes = 200L * 1024 * 1024;
    private int maxPages = 200;
    private int maxSheets = 50;
    /** 任务 TTL（毫秒），默认 1 小时 */
    private long jobTtlMillis = 60L * 60 * 1000;
    private int maxConcurrentJobs = 2;
    private String tempDir = "";

    public String formatMaxFileSize() {
        return formatBytes(maxFileBytes);
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return mb == Math.rint(mb)
                    ? String.format("%.0f MB", mb)
                    : String.format("%.1f MB", mb);
        }
        double gb = mb / 1024.0;
        return gb == Math.rint(gb)
                ? String.format("%.0f GB", gb)
                : String.format("%.2f GB", gb);
    }
}
