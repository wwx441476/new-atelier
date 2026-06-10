package com.example.atelier.infra.persistence.mapper;

import com.example.atelier.infra.datasource.DataSourceConfig;
import com.example.atelier.infra.datasource.DbType;
import com.example.atelier.infra.datasource.PasswordCrypto;
import com.example.atelier.infra.persistence.entity.DataSourceEntity;
import com.example.atelier.infra.persistence.mapper.DataSourcePropsMapper;

import java.time.LocalDateTime;
import java.util.Collections;

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
                .connectionProperties(DataSourcePropsMapper.fromJson(entity.getConnectProps()))
                .build();
    }

    public static DataSourceEntity toEntity(DataSourceConfig config) {
        LocalDateTime now = LocalDateTime.now();
        return DataSourceEntity.builder()
                .pkDatasource(config.getId())
                .dsName(config.getName())
                .connectUrl(config.getJdbcUrl())
                .dsUsername(PasswordCrypto.encrypt(nullToEmpty(config.getUsername())))
                .verification(PasswordCrypto.encrypt(nullToEmpty(config.getPassword())))
                .dbType(config.getDbType() != null ? config.getDbType().name() : DbType.UNKNOWN.name())
                .enable(config.isEnabled() ? 1 : 0)
                .connectProps(DataSourcePropsMapper.toJson(config.getConnectionProperties()))
                .createTime(now)
                .modifyTime(now)
                .build();
    }

    public static void mergeEntity(DataSourceEntity entity, DataSourceConfig config) {
        entity.setDsName(config.getName());
        entity.setConnectUrl(config.getJdbcUrl());
        if (config.getUsername() != null) {
            entity.setDsUsername(PasswordCrypto.encrypt(config.getUsername()));
        }
        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            entity.setVerification(PasswordCrypto.encrypt(config.getPassword()));
        }
        entity.setDbType(config.getDbType() != null ? config.getDbType().name() : DbType.UNKNOWN.name());
        entity.setEnable(config.isEnabled() ? 1 : 0);
        entity.setConnectProps(DataSourcePropsMapper.toJson(
                config.getConnectionProperties() != null
                        ? config.getConnectionProperties()
                        : Collections.emptyMap()));
        entity.setModifyTime(LocalDateTime.now());
    }

    private static boolean isEnabled(DataSourceEntity entity) {
        return entity.getEnable() == null || entity.getEnable() == 1;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
