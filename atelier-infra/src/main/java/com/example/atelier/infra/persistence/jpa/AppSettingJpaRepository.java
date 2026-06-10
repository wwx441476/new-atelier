package com.example.atelier.infra.persistence.jpa;

import com.example.atelier.infra.persistence.entity.AppSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingJpaRepository extends JpaRepository<AppSettingEntity, String> {
}
