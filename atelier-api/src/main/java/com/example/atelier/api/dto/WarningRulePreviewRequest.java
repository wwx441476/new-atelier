package com.example.atelier.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 预警规则数据预览请求 — 支持可选维度过滤与分页。
 */
@Data
public class WarningRulePreviewRequest {

    private int pageIndex = 1;

    private int pageSize = 20;

    private List<MetricQueryApiRequest.FilterDto> filters;

    private List<MetricQueryApiRequest.FilterGroupDto> filterGroups;

    /** true 时预览仅用词库，不调用 LLM（更快） */
    private boolean keywordOnly = true;
}
