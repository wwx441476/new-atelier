package com.example.atelier.domain.metric;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 指标定义 — 声明式元数据，不存预生成 SQL。
 *
 * <p>与旧版 DataIndexVO 对比：
 * <ul>
 *   <li>用 code 标识，不用 UUID 拼表达式</li>
 *   <li>过滤条件在查询时传入，不写入定义</li>
 *   <li>表关系通过 modelCode 引用，不重复存储</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricDefinition {

    private String code;

    private String name;

    private String catalogCode;

    private MetricType type;

    private String datasourceId;

    /** TABLE 类型：引用共享模型 */
    private String modelCode;

    /** TABLE 类型：指标字段 */
    private String tableCode;
    private String fieldCode;
    private String fieldName;

    /** 自定义表达式，如 table.col * 100 */
    private String expression;

    /** SQL 类型：数据集 SQL（不 Base64，纯文本） */
    private String datasetSql;

    /** COMPOSITE 类型：如 revenue - cost，引用其他指标 code */
    private String formula;

    private AggregationType aggregation;

    private String alias;

    private String format;

    private String unit;

    private String description;

    /** 可切片维度，不含默认过滤值 */
    private List<DimensionBinding> dimensions;

    /** 额外展示列（非维度） */
    private List<String> displayFields;
}
