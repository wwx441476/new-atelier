package com.example.atelier.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 表关联定义 — 从指标中抽离，多个指标复用同一模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableJoin {

    private String joinType;

    private String tableCode;

    private String leftTableCode;

    private List<JoinField> joinFields;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinField {
        private String leftField;
        private String rightField;
    }
}
