package com.example.atelier.infra.persistence.entity;

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

/** 可视化大屏 — 声明式 JSON 存储布局与组件。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ATELIER_DASHBOARD_SCREEN")
public class DashboardScreenEntity {

    @Id
    @Column(name = "PK_DASHBOARD", length = 36, nullable = false)
    private String pkDashboard;

    @Column(name = "SCREEN_CODE", length = 100, nullable = false, unique = true)
    private String screenCode;

    @Column(name = "SCREEN_NAME", length = 200)
    private String screenName;

    @Column(name = "CATALOG_CODE", length = 100)
    private String catalogCode;

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
