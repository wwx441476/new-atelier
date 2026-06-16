package com.example.atelier.infra.persistence;

import com.example.atelier.domain.dashboard.DashboardScreen;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.persistence.entity.DashboardScreenEntity;
import com.example.atelier.infra.persistence.jpa.DashboardScreenJpaRepository;
import com.example.atelier.infra.persistence.mapper.DashboardScreenJsonMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class JpaDashboardScreenRepository {

    private final DashboardScreenJpaRepository jpaRepository;

    public JpaDashboardScreenRepository(DashboardScreenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    public List<DashboardScreen> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    public Optional<DashboardScreen> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    public Optional<DashboardScreen> findByCode(String code) {
        return jpaRepository.findByScreenCode(code).map(this::toDomain);
    }

    @Transactional
    public DashboardScreen save(DashboardScreen screen) {
        if (screen == null || screen.getCode() == null || screen.getCode().trim().isEmpty()) {
            throw new AtelierException("大屏编码不能为空");
        }
        DashboardScreenEntity entity;
        if (screen.getId() != null) {
            entity = jpaRepository.findById(screen.getId())
                    .orElseGet(() -> newEntity(screen));
        } else {
            Optional<DashboardScreenEntity> byCode = jpaRepository.findByScreenCode(screen.getCode());
            entity = byCode.orElseGet(() -> newEntity(screen));
        }
        entity.setScreenCode(screen.getCode());
        entity.setScreenName(screen.getName());
        entity.setDefinitionJson(DashboardScreenJsonMapper.toJson(screen));
        entity.setEnabled(Boolean.FALSE.equals(screen.getEnabled()) ? 0 : 1);
        entity.setModifyTime(LocalDateTime.now());
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(LocalDateTime.now());
        }
        jpaRepository.save(entity);
        screen.setId(entity.getPkDashboard());
        return screen;
    }

    @Transactional
    public void deleteByCode(String code) {
        jpaRepository.deleteByScreenCode(code);
    }

    private DashboardScreenEntity newEntity(DashboardScreen screen) {
        return DashboardScreenEntity.builder()
                .pkDashboard(UUID.randomUUID().toString())
                .screenCode(screen.getCode())
                .enabled(1)
                .createTime(LocalDateTime.now())
                .build();
    }

    private DashboardScreen toDomain(DashboardScreenEntity entity) {
        DashboardScreen screen = DashboardScreenJsonMapper.fromJson(entity.getDefinitionJson());
        if (screen == null) {
            screen = new DashboardScreen();
        }
        screen.setId(entity.getPkDashboard());
        if (screen.getCode() == null) {
            screen.setCode(entity.getScreenCode());
        }
        if (screen.getName() == null) {
            screen.setName(entity.getScreenName());
        }
        if (screen.getEnabled() == null) {
            screen.setEnabled(entity.getEnabled() == null || entity.getEnabled() == 1);
        }
        return screen;
    }
}
