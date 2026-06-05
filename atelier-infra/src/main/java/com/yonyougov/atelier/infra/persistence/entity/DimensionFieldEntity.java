package com.yonyougov.atelier.infra.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/** 维度字段 — 语义对齐 DMP_STD_K_DIM_FIELD。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ATELIER_DIMENSION_FIELD")
public class DimensionFieldEntity {

    @Id
    @Column(name = "PK_DIM_FIELD", length = 36, nullable = false)
    private String pkDimField;

    @Column(name = "PK_DIMENSION", length = 36, nullable = false)
    private String pkDimension;

    @Column(name = "FIELD_CODE", length = 100)
    private String fieldCode;

    @Column(name = "FIELD_NAME", length = 200)
    private String fieldName;

    @Column(name = "FIELD_TYPE", length = 50)
    private String fieldType;

    @Column(name = "CODE_FIELD")
    private Integer codeField;

    @Column(name = "NAME_FIELD")
    private Integer nameField;

    @Column(name = "PARENT_FIELD")
    private Integer parentField;

    @Column(name = "SORT_NO")
    private Integer sortNo;
}
