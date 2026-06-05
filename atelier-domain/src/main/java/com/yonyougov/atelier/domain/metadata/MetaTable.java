package com.yonyougov.atelier.domain.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 元数据表 — 对应旧版 MetaTableVO / DMP_META_TABLE。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaTable {

    private String id;

    private String catalogCode;

    private String tableCode;

    private String tableName;

    private String datasourceId;

    private String comments;

    private List<MetaTableField> fields;
}
