package com.example.atelier.domain.dimension;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 维度定义 — 对应旧版 DmpDataSetVO / DMP_STD_K_DIM。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dimension {

    private String id;

    private String catalogCode;

    private String code;

    private String name;

    private DimensionType type;

    private String datasourceId;

    /** 关联元数据表 PK */
    private String metaTableId;

    /** 维度值来源：MANUAL 手动维护 / TABLE 数据库表 */
    private DimensionValueSource valueSource;

    private String comments;

    private List<DimensionField> fields;
}
