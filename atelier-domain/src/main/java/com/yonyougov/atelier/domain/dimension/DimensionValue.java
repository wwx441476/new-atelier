package com.yonyougov.atelier.domain.dimension;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 维度数据行 — 演示用，对应旧版维度数据维护。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimensionValue {

    private String id;

    private String dimensionId;

    private String code;

    private String name;

    private String parentCode;

    private Integer sort;
}
