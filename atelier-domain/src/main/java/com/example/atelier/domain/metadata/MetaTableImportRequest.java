package com.example.atelier.domain.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 从物理库批量导入元数据表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaTableImportRequest {

    private String datasourceId;

    private String schemaCode;

    /** 应用到所有导入表的目录编码（可选） */
    private String catalogCode;

    /** 物理表名列表 */
    private List<String> tableNames;
}
