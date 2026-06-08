package com.example.atelier.domain.dimension;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 维度字段映射 — 对应旧版 DMP_STD_K_DIM_FIELD。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimensionField {

    private String id;

    private String dimensionId;

    private String fieldCode;

    private String fieldName;

    private String fieldType;

    private Boolean codeField;

    private Boolean nameField;

    private Boolean parentField;

    private Integer sort;
}
