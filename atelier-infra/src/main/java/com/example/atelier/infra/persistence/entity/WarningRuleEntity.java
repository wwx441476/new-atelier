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

/** 预警规则 — 语义对齐 DMP_ATELIER_WARNING_RULE。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ATELIER_WARNING_RULE")
public class WarningRuleEntity {

    @Id
    @Column(name = "PK_WARNING_RULE", length = 36, nullable = false)
    private String pkWarningRule;

    @Column(name = "CATALOG_CODE", length = 100)
    private String catalogCode;

    @Column(name = "RULE_CODE", length = 100, nullable = false)
    private String ruleCode;

    @Column(name = "RULE_NAME", length = 200)
    private String ruleName;

    @Column(name = "METRIC_CODES", length = 500)
    private String metricCodes;

    @Column(name = "EXPRESSION", length = 1000)
    private String expression;

    @Column(name = "ENABLED")
    private Integer enabled;

    @Column(name = "WARNING_LEVEL")
    private Integer warningLevel;

    @Lob
    @Column(name = "NOTIFY_CONFIG")
    private String notifyConfig;

    @Column(name = "RULE_TYPE", length = 20)
    @Builder.Default
    private String ruleType = "METRIC";

    @Lob
    @Column(name = "RULE_CONFIG")
    private String ruleConfig;

    @Column(name = "COMMENTS", length = 500)
    private String comments;

    @Column(name = "CREATE_TIME")
    private LocalDateTime createTime;

    @Column(name = "MODIFY_TIME")
    private LocalDateTime modifyTime;
}
