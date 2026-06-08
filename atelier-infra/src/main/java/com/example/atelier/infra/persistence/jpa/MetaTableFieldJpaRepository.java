package com.example.atelier.infra.persistence.jpa;

import com.example.atelier.infra.persistence.entity.MetaTableFieldEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MetaTableFieldJpaRepository extends JpaRepository<MetaTableFieldEntity, String> {

    List<MetaTableFieldEntity> findByPkMetaTableOrderBySortNoAsc(String pkMetaTable);

    void deleteByPkMetaTable(String pkMetaTable);
}
