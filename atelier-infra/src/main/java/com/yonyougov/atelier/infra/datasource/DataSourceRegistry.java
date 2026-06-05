package com.yonyougov.atelier.infra.datasource;

import com.yonyougov.atelier.infra.exception.AtelierException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源注册中心 — 管理配置与连接池，替代 ConnectionPoolManager + IDataSourceDAO 查询侧能力。
 */
public class DataSourceRegistry implements AutoCloseable {

    private final Map<String, DataSourceConfig> configs = new ConcurrentHashMap<>();
    private final Map<String, ConnectionPool> pools = new ConcurrentHashMap<>();

    public void register(DataSourceConfig config) {
        if (config == null || config.getId() == null) {
            throw new AtelierException("数据源配置无效：id 不能为空");
        }
        if (!config.isEnabled()) {
            unregister(config.getId());
            return;
        }
        configs.put(config.getId(), config);
        refreshPool(config);
    }

    /** 更新或新增数据源时刷新连接池 */
    public void refresh(DataSourceConfig config) {
        register(config);
    }

    public void unregister(String datasourceId) {
        ConnectionPool pool = pools.remove(datasourceId);
        if (pool != null) {
            pool.close();
        }
        configs.remove(datasourceId);
    }

    private void refreshPool(DataSourceConfig config) {
        pools.compute(config.getId(), (id, existing) -> {
            if (existing != null) {
                existing.close();
            }
            return new ConnectionPool(config);
        });
    }

    public void registerAll(Collection<DataSourceConfig> configList) {
        if (configList != null) {
            configList.forEach(this::register);
        }
    }

    public DataSourceConfig getConfig(String datasourceId) {
        return configs.get(datasourceId);
    }

    public DbType getDbType(String datasourceId) {
        DataSourceConfig config = getConfig(datasourceId);
        return config != null && config.getDbType() != null ? config.getDbType() : DbType.UNKNOWN;
    }

    public Connection getConnection(String datasourceId) throws SQLException {
        ConnectionPool pool = pools.get(datasourceId);
        if (pool == null) {
            throw new AtelierException("数据源不存在: " + datasourceId);
        }
        return pool.getConnection();
    }

    public Collection<DataSourceConfig> listConfigs() {
        return configs.values();
    }

    @Override
    public void close() {
        pools.values().forEach(ConnectionPool::close);
        pools.clear();
        configs.clear();
    }
}
