package com.example.atelier.infra.persistence.jpa;

import com.example.atelier.infra.persistence.entity.WarningRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WarningRuleJpaRepository extends JpaRepository<WarningRuleEntity, String> {

    Optional<WarningRuleEntity> findByRuleCode(String ruleCode);
}
