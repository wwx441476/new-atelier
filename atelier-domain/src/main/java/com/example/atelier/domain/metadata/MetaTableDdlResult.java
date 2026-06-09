package com.example.atelier.domain.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 元数据表建表 / 增量同步 DDL 预览结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaTableDdlResult {

    /** 全量建表 DDL */
    private String ddl;

    /** 增量加字段 DDL（多语句以分号分隔，仅 tableExists 且存在缺失字段时有值） */
    private String alterDdl;

    /** 待同步到物理表的字段编码 */
    private List<String> missingFieldCodes;

    /** 是否需要执行增量同步 */
    private boolean syncNeeded;

    private boolean tableExists;

    private String datasourceId;

    private String tableCode;
}
