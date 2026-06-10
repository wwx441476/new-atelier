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

/** 应用级设置 — 映射 ATELIER_APP_SETTING。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ATELIER_APP_SETTING")
public class AppSettingEntity {

    @Id
    @Column(name = "SETTING_KEY", length = 100, nullable = false)
    private String settingKey;

    @Lob
    @Column(name = "SETTING_VALUE")
    private String settingValue;

    @Column(name = "MODIFY_TIME")
    private LocalDateTime modifyTime;
}
