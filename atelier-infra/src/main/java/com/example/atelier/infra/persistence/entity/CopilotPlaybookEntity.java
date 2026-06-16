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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ATELIER_COPILOT_PLAYBOOK")
public class CopilotPlaybookEntity {

    @Id
    @Column(name = "PK_PLAYBOOK", length = 36, nullable = false)
    private String pkPlaybook;

    @Column(name = "PLAYBOOK_CODE", length = 100, nullable = false, unique = true)
    private String playbookCode;

    @Column(name = "PLAYBOOK_NAME", length = 200)
    private String playbookName;

    @Lob
    @Column(name = "DEFINITION_JSON")
    private String definitionJson;

    @Column(name = "ENABLED")
    private Integer enabled;

    @Column(name = "USAGE_COUNT")
    private Integer usageCount;

    @Column(name = "CREATE_TIME")
    private LocalDateTime createTime;

    @Column(name = "MODIFY_TIME")
    private LocalDateTime modifyTime;
}
