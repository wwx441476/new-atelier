package com.example.atelier.infra.persistence.jpa;

import com.example.atelier.infra.persistence.entity.DimensionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DimensionJpaRepository extends JpaRepository<DimensionEntity, String> {

    Optional<DimensionEntity> findByDsCode(String dsCode);
}
