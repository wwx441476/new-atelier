package com.yonyougov.atelier.infra.persistence.jpa;

import com.yonyougov.atelier.infra.persistence.entity.DimensionFieldEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DimensionFieldJpaRepository extends JpaRepository<DimensionFieldEntity, String> {

    List<DimensionFieldEntity> findByPkDimensionOrderBySortNoAsc(String pkDimension);

    void deleteByPkDimension(String pkDimension);
}
