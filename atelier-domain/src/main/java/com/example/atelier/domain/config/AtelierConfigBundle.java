package com.example.atelier.domain.config;

import com.example.atelier.domain.dimension.Dimension;
import com.example.atelier.domain.dimension.DimensionField;
import com.example.atelier.domain.dimension.DimensionValue;
import com.example.atelier.domain.metadata.MetaTable;
import com.example.atelier.domain.metadata.MetaTableField;
import com.example.atelier.domain.metric.MetricDefinition;
import com.example.atelier.domain.warning.WarningRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Atelier 全量配置导出包 — 可 JSON 序列化后导入其他环境。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtelierConfigBundle {

    public static final String FORMAT_VERSION = "1.0";

    @Builder.Default
    private String version = FORMAT_VERSION;

    private Instant exportedAt;

    @Builder.Default
    private List<ConfigDataSource> datasources = new ArrayList<>();

    @Builder.Default
    private List<MetaTableExport> metadataTables = new ArrayList<>();

    @Builder.Default
    private List<DimensionExport> dimensions = new ArrayList<>();

    @Builder.Default
    private List<MetricDefinition> metrics = new ArrayList<>();

    @Builder.Default
    private List<WarningRule> warningRules = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetaTableExport {
        private MetaTable table;
        private List<MetaTableField> fields;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionExport {
        private Dimension dimension;
        private List<DimensionField> fields;
        private List<DimensionValue> values;
    }
}
