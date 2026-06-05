package com.yonyougov.atelier.infra.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 数据源持久化实体 — 映射 bd-platform {@code DataSourceVO} / 表 {@code DMP_DATASOURCE}。
 *
 * <p>列名与旧版保持一致，便于从 dmp-atelier 库直接迁移数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "DMP_DATASOURCE")
public class DataSourceEntity {

    /** 主键，对应旧版 dsPk / DataSourceConfig.id */
    @Id
    @Column(name = "PK_DATASOURCE", length = 36, nullable = false)
    private String pkDatasource;

    /** 数据源目录 PK */
    @Column(name = "PK_DS_CATALOG", length = 36)
    private String pkDsCatalog;

    /** 显示名称，对应 dsName */
    @Column(name = "DS_NAME", length = 200)
    private String dsName;

    /** 逻辑库名 */
    @Column(name = "DATABASE_NAME", length = 100)
    private String databaseName;

    /** 数据库类型字符串，对应 DBType 枚举名 */
    @Column(name = "DB_TYPE", length = 30)
    private String dbType;

    /** 是否启用：1 启用，0 禁用 */
    @Column(name = "ENABLE")
    private Integer enable;

    @Column(name = "CONNECT_URL", length = 500)
    private String connectUrl;

    /** 用户名（旧版可能加密存储） */
    @Column(name = "DS_USERNAME", length = 200)
    private String dsUsername;

    /** 密码（旧版 verification 字段，可能加密） */
    @Column(name = "VERIFICATION", length = 500)
    private String verification;

    @Column(name = "POOL_INITIAL_SIZE")
    private Integer poolInitialSize;

    @Column(name = "POOL_MAX_IDLE")
    private Integer poolMaxIdle;

    @Column(name = "POOL_MAX_ACTIVE")
    private Integer poolMaxActive;

    @Column(name = "POOL_MAX_WAIT")
    private Integer poolMaxWait;

    @Column(name = "POOL_MIN_IDLE")
    private Integer poolMinIdle;

    @Column(name = "CREATE_TIME")
    private LocalDateTime createTime;

    @Column(name = "MODIFY_TIME")
    private LocalDateTime modifyTime;
}
