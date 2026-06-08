package com.example.atelier.infra.persistence;

import java.util.List;
import java.util.Optional;

/**
 * 通用仓储接口桩 — 后续 JPA/MyBatis 实现可继承此约定。
 *
 * @param <T>  实体类型
 * @param <ID> 主键类型
 */
public interface BaseRepository<T, ID> {

    Optional<T> findById(ID id);

    List<T> findAll();

    void save(T entity);

    void deleteById(ID id);
}
