package com.yonyougov.atelier.infra.persistence.jpa;

import com.yonyougov.atelier.infra.persistence.entity.DimensionValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DimensionValueJpaRepository extends JpaRepository<DimensionValueEntity, String> {

    List<DimensionValueEntity> findByPkDimensionOrderBySortNoAsc(String pkDimension);

    void deleteByPkDimension(String pkDimension);
}
