package com.example.atelier.infra.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 元数据表 — 新表 ATELIER_META_TABLE，字段语义对齐 MetaTableVO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ATELIER_META_TABLE")
public class MetaTableEntity {

    @Id
    @Column(name = "PK_META_TABLE", length = 36, nullable = false)
    private String pkMetaTable;

    @Column(name = "CATALOG_CODE", length = 100)
    private String catalogCode;

    @Column(name = "TABLE_CODE", length = 100, nullable = false)
    private String tableCode;

    @Column(name = "TABLE_NAME", length = 200)
    private String tableName;

    @Column(name = "PK_DATASOURCE", length = 36)
    private String pkDatasource;

    @Column(name = "SCHEMA_CODE", length = 100)
    private String schemaCode;

    @Column(name = "COMMENTS", length = 500)
    private String comments;

    @Column(name = "CREATE_TIME")
    private LocalDateTime createTime;

    @Column(name = "MODIFY_TIME")
    private LocalDateTime modifyTime;
}
