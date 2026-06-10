package com.example.atelier.infra.persistence.jpa;

import com.example.atelier.infra.persistence.entity.WarningRuleJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WarningRuleJobJpaRepository extends JpaRepository<WarningRuleJobEntity, String> {

    List<WarningRuleJobEntity> findByJobStatusInOrderByCreateTimeDesc(List<String> statuses);

    List<WarningRuleJobEntity> findByJobStatusAndModifyTimeBefore(String jobStatus, LocalDateTime before);
}
