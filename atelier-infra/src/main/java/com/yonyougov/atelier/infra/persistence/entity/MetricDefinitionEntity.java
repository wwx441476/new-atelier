package com.yonyougov.atelier.infra.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 指标定义持久化 — 声明式 JSON 存储，替代旧版 DMP_ATELIER_N_INDEX 多表结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ATELIER_METRIC_DEFINITION")
public class MetricDefinitionEntity {

    @Id
    @Column(name = "PK_METRIC", length = 36, nullable = false)
    private String pkMetric;

    @Column(name = "METRIC_CODE", length = 100, nullable = false, unique = true)
    private String metricCode;

    @Column(name = "METRIC_NAME", length = 200)
    private String metricName;

    @Column(name = "CATALOG_CODE", length = 100)
    private String catalogCode;

    @Column(name = "METRIC_TYPE", length = 30)
    private String metricType;

    @Column(name = "PK_DATASOURCE", length = 36)
    private String pkDatasource;

    @Lob
    @Column(name = "DEFINITION_JSON")
    private String definitionJson;

    @Column(name = "ENABLED")
    private Integer enabled;

    @Column(name = "CREATE_TIME")
    private LocalDateTime createTime;

    @Column(name = "MODIFY_TIME")
    private LocalDateTime modifyTime;
}
