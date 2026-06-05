package com.yonyougov.atelier.infra.persistence.jpa;

import com.yonyougov.atelier.infra.persistence.entity.MetaTableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MetaTableJpaRepository extends JpaRepository<MetaTableEntity, String> {

    List<MetaTableEntity> findByPkDatasource(String pkDatasource);

    Optional<MetaTableEntity> findByTableCode(String tableCode);
}
