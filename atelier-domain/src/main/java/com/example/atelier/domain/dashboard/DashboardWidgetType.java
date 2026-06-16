package com.example.atelier.domain.dashboard;

/**
 * 大屏组件类型。
 */
public enum DashboardWidgetType {
    /** 标题/装饰文本 */
    TITLE,
    /** 指标 KPI 数值卡 */
    METRIC_VALUE,
    /** 指标图表（柱/线/饼） */
    METRIC_CHART,
    /** 指标明细表格 */
    METRIC_TABLE,
    /** 预警统计卡 */
    WARNING_STAT,
    /** 预警命中表格 */
    WARNING_TABLE,
    /** 数据库查询 KPI 数值 */
    SQL_VALUE,
    /** 数据库查询图表 */
    SQL_CHART,
    /** 数据库查询表格 */
    SQL_TABLE
}
