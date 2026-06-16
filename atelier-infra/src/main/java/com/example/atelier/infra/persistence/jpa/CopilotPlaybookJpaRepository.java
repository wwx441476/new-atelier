package com.example.atelier.infra.persistence.jpa;

import com.example.atelier.infra.persistence.entity.CopilotPlaybookEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CopilotPlaybookJpaRepository extends JpaRepository<CopilotPlaybookEntity, String> {

    Optional<CopilotPlaybookEntity> findByPlaybookCode(String playbookCode);

    void deleteByPlaybookCode(String playbookCode);
}
