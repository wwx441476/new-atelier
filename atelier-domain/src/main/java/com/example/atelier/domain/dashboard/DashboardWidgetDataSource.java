package com.example.atelier.domain.dashboard;

import com.example.atelier.domain.metric.FilterGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 大屏组件数据绑定配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardWidgetDataSource {

    /** METRIC / WARNING / SQL */
    private String bindType;

    /** 指标 code 列表 */
    private List<String> metricCodes;

    /** KPI/图表取值字段（指标结果列 code） */
    private String valueField;

    /** 图表分类/维度字段 */
    private String categoryField;

    /** bar / line / pie */
    private String chartType;

    /** 预警规则 ID */
    private String ruleId;

    /** 数据源 ID（SQL 查询） */
    private String datasourceId;

    /** 查询模式：SQL 自定义语句 / TABLE 表预览 */
    private String queryMode;

    /** SELECT 语句（queryMode=SQL） */
    private String sql;

    /** schema（queryMode=TABLE） */
    private String schema;

    /** 表名（queryMode=TABLE） */
    private String tableName;

    private Integer pageSize;

    private List<FilterGroup> filterGroups;

    /** 列字段显示名：字段 code → 展示标题 */
    private Map<String, String> columnLabels;

    /** 字段值映射：字段 code → (原始值 → 展示名) */
    private Map<String, Map<String, String>> valueMappings;

    /** 数值展示格式，如 {value}美元 */
    private String valueFormat;

    private String valuePrefix;

    private String valueSuffix;

    private Integer decimalPlaces;

    private Boolean useGrouping;
}
