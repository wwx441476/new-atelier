package com.example.atelier.infra.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/** 维度数据 — 演示存储。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ATELIER_DIMENSION_VALUE")
public class DimensionValueEntity {

    @Id
    @Column(name = "PK_DIM_VALUE", length = 36, nullable = false)
    private String pkDimValue;

    @Column(name = "PK_DIMENSION", length = 36, nullable = false)
    private String pkDimension;

    @Column(name = "CODE", length = 100)
    private String code;

    @Column(name = "NAME", length = 200)
    private String name;

    @Column(name = "PARENT_CODE", length = 100)
    private String parentCode;

    @Column(name = "SORT_NO")
    private Integer sortNo;
}
