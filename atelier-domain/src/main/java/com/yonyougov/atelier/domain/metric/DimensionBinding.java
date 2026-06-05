package com.yonyougov.atelier.domain.metric;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 维度绑定 — 声明指标可按哪些维度切片，不含具体过滤值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimensionBinding {

    /** 维度业务编码，如 dept、year */
    private String dimensionCode;

    /** 物理字段名 */
    private String fieldCode;

    /** 展示名称 */
    private String fieldName;

    /** 排序 */
    private Integer sort;
}
