package com.yonyougov.atelier.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据模型 — 表关系在模型层统一管理，指标只引用 modelCode。
 * 对应旧版每个指标各自存 relation JSON 的做法。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricModel {

    private String modelCode;

    private String modelName;

    private String datasourceId;

    /** 主表编码（可含 schema） */
    private String mainTableCode;

    private List<TableJoin> joins;
}
