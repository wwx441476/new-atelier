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

/** 维度主表 — 语义对齐 DMP_STD_K_DIM。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ATELIER_DIMENSION")
public class DimensionEntity {

    @Id
    @Column(name = "PK_DIMENSION", length = 36, nullable = false)
    private String pkDimension;

    @Column(name = "CATALOG_CODE", length = 100)
    private String catalogCode;

    @Column(name = "DS_CODE", length = 100, nullable = false)
    private String dsCode;

    @Column(name = "DS_NAME", length = 200)
    private String dsName;

    @Column(name = "DS_TYPE", length = 30)
    private String dsType;

    @Column(name = "PK_DATASOURCE", length = 36)
    private String pkDatasource;

    @Column(name = "PK_META_TABLE", length = 36)
    private String pkMetaTable;

    @Column(name = "VALUE_SOURCE", length = 30)
    private String valueSource;

    @Column(name = "COMMENTS", length = 500)
    private String comments;

    @Column(name = "CREATE_TIME")
    private LocalDateTime createTime;

    @Column(name = "MODIFY_TIME")
    private LocalDateTime modifyTime;
}
