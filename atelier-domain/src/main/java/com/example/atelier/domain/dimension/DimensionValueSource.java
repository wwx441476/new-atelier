package com.example.atelier.domain.dimension;

/**
 * 维度值来源。
 */
public enum DimensionValueSource {

    /** 手动维护 ATELIER_DIMENSION_VALUE */
    MANUAL,

    /** 从关联元数据表对应的物理表读取 */
    TABLE
}
