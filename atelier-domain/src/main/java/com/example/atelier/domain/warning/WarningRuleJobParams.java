package com.example.atelier.domain.warning;

import com.example.atelier.domain.metric.FilterCondition;
import com.example.atelier.domain.metric.FilterGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 预警预览任务参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarningRuleJobParams {

    @Builder.Default
    private int pageIndex = 1;

    @Builder.Default
    private int pageSize = 20;

    @Builder.Default
    private boolean keywordOnly = true;

    private List<FilterCondition> filters;

    private List<FilterGroup> filterGroups;
}
