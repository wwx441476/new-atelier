package com.example.atelier.infra.persistence.jpa;

import com.example.atelier.infra.persistence.entity.DataSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 数据源 JPA 仓储 — 替代 bd-platform {@code IDataSourceDAO} 的持久化侧。
 */
public interface DataSourceJpaRepository extends JpaRepository<DataSourceEntity, String> {

    /** 查询所有已启用的数据源 */
    List<DataSourceEntity> findByEnable(Integer enable);
}
