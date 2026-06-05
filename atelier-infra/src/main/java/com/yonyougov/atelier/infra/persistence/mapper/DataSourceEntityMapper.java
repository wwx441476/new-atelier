package com.yonyougov.atelier.infra.persistence.mapper;

import com.yonyougov.atelier.infra.datasource.DataSourceConfig;
import com.yonyougov.atelier.infra.datasource.DbType;
import com.yonyougov.atelier.infra.datasource.PasswordCrypto;
import com.yonyougov.atelier.infra.persistence.entity.DataSourceEntity;

import java.time.LocalDateTime;

/**
 * DataSourceEntity ↔ DataSourceConfig 转换。
 */
public final class DataSourceEntityMapper {

    private DataSourceEntityMapper() {
    }

    public static DataSourceConfig toConfig(DataSourceEntity entity) {
        if (entity == null) {
            return null;
        }
        return DataSourceConfig.builder()
                .id(entity.getPkDatasource())
                .name(entity.getDsName())
                .jdbcUrl(entity.getConnectUrl())
                .username(PasswordCrypto.decrypt(entity.getDsUsername()))
                .password(PasswordCrypto.decrypt(entity.getVerification()))
                .dbType(DbType.fromString(entity.getDbType()))
                .enabled(isEnabled(entity))
                .build();
    }

    public static DataSourceEntity toEntity(DataSourceConfig config) {
        LocalDateTime now = LocalDateTime.now();
        return DataSourceEntity.builder()
                .pkDatasource(config.getId())
                .dsName(config.getName())
                .connectUrl(config.getJdbcUrl())
                .dsUsername(PasswordCrypto.encrypt(config.getUsername()))
                .verification(PasswordCrypto.encrypt(config.getPassword()))
                .dbType(config.getDbType() != null ? config.getDbType().name() : DbType.UNKNOWN.name())
                .enable(config.isEnabled() ? 1 : 0)
                .createTime(now)
                .modifyTime(now)
                .build();
    }

    public static void mergeEntity(DataSourceEntity entity, DataSourceConfig config) {
        entity.setDsName(config.getName());
        entity.setConnectUrl(config.getJdbcUrl());
        entity.setDsUsername(PasswordCrypto.encrypt(config.getUsername()));
        entity.setVerification(PasswordCrypto.encrypt(config.getPassword()));
        entity.setDbType(config.getDbType() != null ? config.getDbType().name() : DbType.UNKNOWN.name());
        entity.setEnable(config.isEnabled() ? 1 : 0);
        entity.setModifyTime(LocalDateTime.now());
    }

    private static boolean isEnabled(DataSourceEntity entity) {
        return entity.getEnable() == null || entity.getEnable() == 1;
    }
}
