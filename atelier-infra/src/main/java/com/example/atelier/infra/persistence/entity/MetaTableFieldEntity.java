package com.example.atelier.infra.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/** 元数据字段 — 新表 ATELIER_META_TABLE_FIELD。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ATELIER_META_TABLE_FIELD")
public class MetaTableFieldEntity {

    @Id
    @Column(name = "PK_META_FIELD", length = 36, nullable = false)
    private String pkMetaField;

    @Column(name = "PK_META_TABLE", length = 36, nullable = false)
    private String pkMetaTable;

    @Column(name = "FIELD_CODE", length = 100, nullable = false)
    private String fieldCode;

    @Column(name = "FIELD_NAME", length = 200)
    private String fieldName;

    @Column(name = "FIELD_TYPE", length = 50)
    private String fieldType;

    @Column(name = "FIELD_LENGTH")
    private Integer fieldLength;

    @Column(name = "FIELD_PRECISION")
    private Integer fieldPrecision;

    @Column(name = "NULLABLE")
    private Integer nullable;

    @Column(name = "SORT_NO")
    private Integer sortNo;
}
