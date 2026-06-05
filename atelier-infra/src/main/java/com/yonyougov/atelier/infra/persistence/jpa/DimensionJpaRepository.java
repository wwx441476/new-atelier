package com.yonyougov.atelier.infra.persistence.jpa;

import com.yonyougov.atelier.infra.persistence.entity.DimensionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DimensionJpaRepository extends JpaRepository<DimensionEntity, String> {

    Optional<DimensionEntity> findByDsCode(String dsCode);
}
