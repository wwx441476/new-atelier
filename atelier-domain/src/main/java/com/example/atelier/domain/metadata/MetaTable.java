package com.example.atelier.domain.metadata;

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

    /** 物理库 schema（如 H2 的 PUBLIC、PostgreSQL 的 public） */
    private String schemaCode;

    private String comments;

    private List<MetaTableField> fields;
}
