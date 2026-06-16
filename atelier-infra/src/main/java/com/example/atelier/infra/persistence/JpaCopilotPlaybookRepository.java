package com.example.atelier.infra.persistence;

import com.example.atelier.domain.copilot.CopilotPlaybook;
import com.example.atelier.infra.exception.AtelierException;
import com.example.atelier.infra.persistence.entity.CopilotPlaybookEntity;
import com.example.atelier.infra.persistence.jpa.CopilotPlaybookJpaRepository;
import com.example.atelier.infra.persistence.mapper.CopilotPlaybookJsonMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class JpaCopilotPlaybookRepository {

    private final CopilotPlaybookJpaRepository jpaRepository;

    public JpaCopilotPlaybookRepository(CopilotPlaybookJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    public List<CopilotPlaybook> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    public Optional<CopilotPlaybook> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    public Optional<CopilotPlaybook> findByCode(String code) {
        return jpaRepository.findByPlaybookCode(code).map(this::toDomain);
    }

    @Transactional
    public CopilotPlaybook save(CopilotPlaybook playbook) {
        if (playbook == null || playbook.getCode() == null || playbook.getCode().trim().isEmpty()) {
            throw new AtelierException("技能编码不能为空");
        }
        CopilotPlaybookEntity entity;
        if (playbook.getId() != null) {
            entity = jpaRepository.findById(playbook.getId())
                    .orElseGet(() -> newEntity(playbook));
        } else {
            entity = jpaRepository.findByPlaybookCode(playbook.getCode())
                    .orElseGet(() -> newEntity(playbook));
        }
        entity.setPlaybookCode(playbook.getCode());
        entity.setPlaybookName(playbook.getName());
        entity.setDefinitionJson(CopilotPlaybookJsonMapper.toJson(playbook));
        entity.setEnabled(Boolean.FALSE.equals(playbook.getEnabled()) ? 0 : 1);
        entity.setUsageCount(playbook.getUsageCount() != null ? playbook.getUsageCount() : 0);
        entity.setModifyTime(LocalDateTime.now());
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(LocalDateTime.now());
        }
        jpaRepository.save(entity);
        playbook.setId(entity.getPkPlaybook());
        return playbook;
    }

    @Transactional
    public void incrementUsage(String id) {
        jpaRepository.findById(id).ifPresent(entity -> {
            int count = entity.getUsageCount() != null ? entity.getUsageCount() : 0;
            entity.setUsageCount(count + 1);
            entity.setModifyTime(LocalDateTime.now());
            jpaRepository.save(entity);
        });
    }

    @Transactional
    public void deleteByCode(String code) {
        jpaRepository.deleteByPlaybookCode(code);
    }

    private CopilotPlaybookEntity newEntity(CopilotPlaybook playbook) {
        return CopilotPlaybookEntity.builder()
                .pkPlaybook(UUID.randomUUID().toString())
                .playbookCode(playbook.getCode())
                .enabled(1)
                .usageCount(0)
                .createTime(LocalDateTime.now())
                .build();
    }

    private CopilotPlaybook toDomain(CopilotPlaybookEntity entity) {
        CopilotPlaybook playbook = CopilotPlaybookJsonMapper.fromJson(entity.getDefinitionJson());
        if (playbook == null) {
            playbook = new CopilotPlaybook();
        }
        playbook.setId(entity.getPkPlaybook());
        if (playbook.getCode() == null) {
            playbook.setCode(entity.getPlaybookCode());
        }
        if (playbook.getName() == null) {
            playbook.setName(entity.getPlaybookName());
        }
        if (playbook.getEnabled() == null) {
            playbook.setEnabled(entity.getEnabled() == null || entity.getEnabled() == 1);
        }
        if (playbook.getUsageCount() == null) {
            playbook.setUsageCount(entity.getUsageCount() != null ? entity.getUsageCount() : 0);
        }
        return playbook;
    }
}
