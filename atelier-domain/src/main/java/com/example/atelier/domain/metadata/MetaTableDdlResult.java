package com.example.atelier.domain.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 元数据表建表 DDL 预览结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaTableDdlResult {

    private String ddl;

    private boolean tableExists;

    private String datasourceId;

    private String tableCode;
}
