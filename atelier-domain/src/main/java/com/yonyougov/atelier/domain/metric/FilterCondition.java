package com.yonyougov.atelier.domain.metric;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 查询时过滤条件 — 旧版在保存时写入 WHERE，新版在查询时传入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterCondition {

    /** 维度 code 或字段 code */
    private String field;

    private FilterOperator operator;

    /** 单值或多值（IN 场景） */
    private List<String> values;
}
