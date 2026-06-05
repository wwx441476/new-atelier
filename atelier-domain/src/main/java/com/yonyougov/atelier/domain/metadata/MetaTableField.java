package com.yonyougov.atelier.domain.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 元数据字段 — 对应旧版 MetaTableFieldVO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaTableField {

    private String id;

    private String tableId;

    private String fieldCode;

    private String fieldName;

    private String fieldType;

    private Integer fieldLength;

    private Integer fieldPrecision;

    private Boolean nullable;

    private Integer sort;
}
