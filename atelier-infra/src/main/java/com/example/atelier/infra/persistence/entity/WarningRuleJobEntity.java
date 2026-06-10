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

/** 预警规则异步执行任务。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ATELIER_WARNING_RULE_JOB")
public class WarningRuleJobEntity {

    @Id
    @Column(name = "PK_JOB", length = 36, nullable = false)
    private String pkJob;

    @Column(name = "PK_WARNING_RULE", length = 36, nullable = false)
    private String pkWarningRule;

    @Column(name = "RULE_CODE", length = 100)
    private String ruleCode;

    @Column(name = "RULE_NAME", length = 200)
    private String ruleName;

    @Column(name = "JOB_STATUS", length = 20, nullable = false)
    private String jobStatus;

    @Column(name = "JOB_SOURCE", length = 20)
    private String jobSource;

    @Column(name = "PROGRESS")
    @Builder.Default
    private Integer progress = 0;

    @Lob
    @Column(name = "PARAMS_JSON")
    private String paramsJson;

    @Lob
    @Column(name = "RESULT_JSON")
    private String resultJson;

    @Column(name = "ERROR_MSG", length = 500)
    private String errorMsg;

    @Column(name = "TOTAL_ROWS")
    private Long totalRows;

    @Column(name = "MATCHED_COUNT")
    private Long matchedCount;

    @Column(name = "CREATE_TIME")
    private LocalDateTime createTime;

    @Column(name = "MODIFY_TIME")
    private LocalDateTime modifyTime;

    @Column(name = "FINISH_TIME")
    private LocalDateTime finishTime;
}
