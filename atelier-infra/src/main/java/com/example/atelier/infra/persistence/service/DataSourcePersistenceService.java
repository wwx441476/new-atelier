package com.example.atelier.infra.persistence.service;

import com.example.atelier.infra.datasource.DataSourceConfig;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.persistence.entity.DataSourceEntity;
import com.example.atelier.infra.persistence.jpa.DataSourceJpaRepository;
import com.example.atelier.infra.persistence.mapper.DataSourceEntityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 数据源 CRUD 服务 — 替代 IDataSourceService 的持久化能力。
 */
@Service
public class DataSourcePersistenceService {

    private final DataSourceJpaRepository repository;

    public DataSourcePersistenceService(DataSourceJpaRepository repository) {
        this.repository = repository;
    }

    public List<DataSourceConfig> findAllEnabledConfigs() {
        return repository.findByEnable(1).stream()
                .map(DataSourceEntityMapper::toConfig)
                .collect(Collectors.toList());
    }

    public List<DataSourceConfig> findAllConfigs() {
        return repository.findAll().stream()
                .map(DataSourceEntityMapper::toConfig)
                .collect(Collectors.toList());
    }

    public Optional<DataSourceConfig> findConfigById(String id) {
        return repository.findById(id).map(DataSourceEntityMapper::toConfig);
    }

    @Transactional
    public DataSourceConfig save(DataSourceConfig config) {
        validate(config);
        DataSourceEntity entity = repository.findById(config.getId())
                .map(existing -> {
                    DataSourceEntityMapper.mergeEntity(existing, config);
                    return existing;
                })
                .orElseGet(() -> DataSourceEntityMapper.toEntity(config));
        return DataSourceEntityMapper.toConfig(repository.save(entity));
    }

    @Transactional
    public void deleteById(String id) {
        if (!repository.existsById(id)) {
            throw new AtelierException("数据源不存在: " + id);
        }
        repository.deleteById(id);
    }

    public long count() {
        return repository.count();
    }

    private void validate(DataSourceConfig config) {
        if (config == null || config.getId() == null || config.getId().trim().isEmpty()) {
            throw new AtelierException("数据源 id 不能为空");
        }
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            throw new AtelierException("数据源名称不能为空");
        }
        if (config.getJdbcUrl() == null || config.getJdbcUrl().trim().isEmpty()) {
            throw new AtelierException("数据源 jdbcUrl 不能为空");
        }
        if (config.getUsername() == null || config.getUsername().trim().isEmpty()) {
            throw new AtelierException("数据源用户名不能为空");
        }
    }
}
