package com.example.atelier.domain.metric;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 过滤条件组 — 组内条件以 AND 连接，多个组之间以 OR 连接。
 *
 * <p>示例：(dept_code IN ('001') AND fiscal_year IN ('2024'))
 *     OR (dept_code IN ('002') AND fiscal_year IN ('2025'))
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterGroup {

    private List<FilterCondition> conditions;
}
