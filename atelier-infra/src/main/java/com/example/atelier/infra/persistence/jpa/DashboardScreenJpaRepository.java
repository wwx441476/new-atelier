package com.example.atelier.infra.persistence.jpa;

import com.example.atelier.infra.persistence.entity.DashboardScreenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DashboardScreenJpaRepository extends JpaRepository<DashboardScreenEntity, String> {

    Optional<DashboardScreenEntity> findByScreenCode(String screenCode);

    void deleteByScreenCode(String screenCode);
}
