import { useCallback, useEffect, useState } from 'react';
import ReactECharts from 'echarts-for-react';
import { Spin, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { metricApi } from '../../api/metric';
import { warningApi } from '../../api/warning';
import type {
  DashboardWidget,
  DashboardWidgetDataSource,
  MetricQueryRequest,
  QueryResult,
  WarningRulePreviewResult,
} from '../../api/types';
import { loadDashboardQueryData } from './dashboardQueryLoader';
import { buildFilterRequest } from '../../utils/queryFilters';
import { mapFieldValue, type FieldValueMappings } from './valueMappingUtils';
import { formatDisplayValue } from './valueFormatUtils';
import type { DashboardTheme } from './dashboardThemes';

interface DashboardWidgetRendererProps {
  widget: DashboardWidget;
  theme?: DashboardTheme;
  preview?: boolean;
  refreshKey?: number;
}

function pickNumericValue(rows: Record<string, unknown>[], field?: string): string {
  if (rows.length === 0) {
    return '-';
  }
  const row = rows[0];
  if (field && row[field] != null && row[field] !== '') {
    return String(row[field]);
  }
  for (const [key, value] of Object.entries(row)) {
    if (key.startsWith('_')) {
      continue;
    }
    if (typeof value === 'number') {
      return String(value);
    }
    if (value != null && value !== '' && !Number.isNaN(Number(value))) {
      return String(value);
    }
  }
  return '-';
}

function buildChartOption(
  dataSource: DashboardWidgetDataSource | undefined,
  result: QueryResult,
  theme?: DashboardTheme,
): Record<string, unknown> {
  const rows = result.rows ?? [];
  const categoryField = dataSource?.categoryField;
  const valueField = dataSource?.valueField;
  const chartType = dataSource?.chartType ?? 'bar';
  const colors = theme?.chartColors ?? ['#1677ff'];
  const chartText = theme?.chartText ?? 'rgba(255,255,255,0.75)';
  const chartGrid = theme?.chartGrid ?? 'rgba(255,255,255,0.08)';
  const accent = colors[0];

  const categories: string[] = [];
  const values: number[] = [];

  for (const row of rows) {
    let category = categoryField ? String(row[categoryField] ?? '') : '';
    if (!category) {
      const firstKey = Object.keys(row).find((k) => !k.startsWith('_'));
      category = firstKey ? String(row[firstKey] ?? '') : String(categories.length + 1);
    }
    let value = 0;
    if (valueField && row[valueField] != null) {
      value = Number(row[valueField]) || 0;
    } else {
      const numericKey = Object.keys(row).find(
        (k) => !k.startsWith('_') && k !== categoryField && !Number.isNaN(Number(row[k])),
      );
      value = numericKey ? Number(row[numericKey]) || 0 : 0;
    }
    categories.push(mapFieldValue(categoryField, category, dataSource?.valueMappings));
    values.push(value);
  }

  const base = {
    backgroundColor: 'transparent',
    textStyle: { color: chartText },
    tooltip: { trigger: chartType === 'pie' ? 'item' : 'axis' },
    grid: { left: 44, right: 20, top: 28, bottom: 36 },
    color: colors,
  };

  if (chartType === 'pie') {
    return {
      ...base,
      series: [
        {
          type: 'pie',
          radius: ['38%', '68%'],
          data: categories.map((name, i) => ({ name, value: values[i] })),
          label: { color: chartText },
          itemStyle: { borderRadius: 4, borderColor: 'transparent', borderWidth: 2 },
        },
      ],
    };
  }

  return {
    ...base,
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { color: chartText, fontSize: 11 },
      axisLine: { lineStyle: { color: chartGrid } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: chartText, fontSize: 11 },
      splitLine: { lineStyle: { color: chartGrid } },
    },
    series: [
      {
        type: chartType === 'line' ? 'line' : 'bar',
        data: values,
        smooth: chartType === 'line',
        barMaxWidth: 36,
        itemStyle: {
          color: chartType === 'bar' ? {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: accent },
              { offset: 1, color: `${accent}66` },
            ],
          } : accent,
          borderRadius: chartType === 'bar' ? [4, 4, 0, 0] : undefined,
        },
        areaStyle: chartType === 'line' ? { color: `${accent}22` } : undefined,
        lineStyle: chartType === 'line' ? { width: 2, color: accent } : undefined,
      },
    ],
  };
}

function resolveColumnHeaders(
  rows: Record<string, unknown>[],
  apiHeaders?: Record<string, string>,
  customLabels?: Record<string, string>,
): Record<string, string> {
  const merged: Record<string, string> = { ...(apiHeaders ?? {}) };
  const keys =
    rows.length > 0
      ? Object.keys(rows[0]).filter((key) => !key.startsWith('_'))
      : Object.keys(customLabels ?? {});

  for (const key of keys) {
    if (customLabels?.[key]) {
      merged[key] = customLabels[key];
    } else if (!merged[key]) {
      merged[key] = key;
    }
  }
  return merged;
}

function buildTableColumns(
  rows: Record<string, unknown>[],
  apiHeaders?: Record<string, string>,
  customLabels?: Record<string, string>,
  valueMappings?: FieldValueMappings,
): ColumnsType<Record<string, unknown>> {
  if (rows.length === 0) {
    return [];
  }
  const headers = resolveColumnHeaders(rows, apiHeaders, customLabels);
  return Object.keys(rows[0])
    .filter((key) => !key.startsWith('_'))
    .slice(0, 12)
    .map((key) => ({
      title: headers[key] ?? key,
      dataIndex: key,
      key,
      ellipsis: true,
      render: (val: unknown) => mapFieldValue(key, val, valueMappings),
    }));
}

export default function DashboardWidgetRenderer({
  widget,
  theme,
  preview = false,
  refreshKey = 0,
}: DashboardWidgetRendererProps) {
  const [loading, setLoading] = useState(false);
  const [queryResult, setQueryResult] = useState<QueryResult | null>(null);
  const [warningResult, setWarningResult] = useState<WarningRulePreviewResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    if (widget.type === 'TITLE') {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      if (widget.type.startsWith('WARNING')) {
        const ruleId = widget.dataSource?.ruleId;
        if (!ruleId) {
          setWarningResult(null);
          return;
        }
        const result = await warningApi.previewRule(ruleId, {
          pageIndex: 1,
          pageSize: widget.dataSource?.pageSize ?? 10,
          keywordOnly: true,
        });
        setWarningResult(result);
        setQueryResult(null);
      } else if (widget.type.startsWith('SQL_')) {
        const result = await loadDashboardQueryData(widget.dataSource);
        setQueryResult(result);
        setWarningResult(null);
      } else {
        const metricCodes = widget.dataSource?.metricCodes ?? [];
        if (metricCodes.length === 0) {
          setQueryResult(null);
          return;
        }
        const request: MetricQueryRequest = {
          metricCodes,
          pageIndex: 1,
          pageSize: widget.dataSource?.pageSize ?? 20,
          ...buildFilterRequest(
            (widget.dataSource?.filterGroups ?? []).map((g) => ({
              conditions: (g.conditions ?? []).map((c) => ({
                field: c.field,
                operator: c.operator,
                values: c.values ?? [],
              })),
            })),
          ),
        };
        const result = await metricApi.query(request);
        setQueryResult(result);
        setWarningResult(null);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, [widget]);

  useEffect(() => {
    if (preview) {
      void loadData();
    }
  }, [loadData, preview, refreshKey]);

  if (widget.type === 'TITLE') {
    const style = widget.style ?? {};
    return (
      <div
        className="dashboard-widget-title-text"
        style={{
          fontSize: style.fontSize ?? 32,
          color: style.color ?? theme?.titleColor ?? '#69b1ff',
          justifyContent:
            style.textAlign === 'left'
              ? 'flex-start'
              : style.textAlign === 'right'
                ? 'flex-end'
                : 'center',
        }}
      >
        {widget.content || widget.title || '标题'}
      </div>
    );
  }

  if (!preview) {
    return <div className="dashboard-empty-hint">预览模式下显示数据</div>;
  }

  if (loading) {
    return (
      <div className="dashboard-empty-hint">
        <Spin size="small" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="dashboard-empty-hint">
        <Typography.Text type="danger">{error}</Typography.Text>
      </div>
    );
  }

  if (widget.type === 'METRIC_VALUE' || widget.type === 'SQL_VALUE') {
    const raw = pickNumericValue(queryResult?.rows ?? [], widget.dataSource?.valueField);
    const value = formatDisplayValue(raw, widget.dataSource ?? undefined);
    return (
      <div className="dashboard-kpi-value">
        <div className="dashboard-kpi-number">{value}</div>
        <div className="dashboard-kpi-label">{widget.title}</div>
      </div>
    );
  }

  if (widget.type === 'METRIC_CHART' || widget.type === 'SQL_CHART') {
    if (!queryResult || queryResult.rows.length === 0) {
      return <div className="dashboard-empty-hint">请配置数据源并保存预览</div>;
    }
    return (
      <ReactECharts
        option={buildChartOption(widget.dataSource, queryResult, theme)}
        style={{ height: '100%', width: '100%' }}
        opts={{ renderer: 'canvas' }}
      />
    );
  }

  if (widget.type === 'METRIC_TABLE' || widget.type === 'SQL_TABLE') {
    const columns = buildTableColumns(
      queryResult?.rows ?? [],
      queryResult?.headers,
      widget.dataSource?.columnLabels,
      widget.dataSource?.valueMappings,
    );
    return (
      <div className="dashboard-table" style={{ height: '100%', overflow: 'auto' }}>
        <Table
          size="small"
          pagination={false}
          columns={columns}
          dataSource={(queryResult?.rows ?? []).map((row, i) => ({ ...row, key: i }))}
          scroll={{ y: 160 }}
        />
      </div>
    );
  }

  if (widget.type === 'WARNING_STAT') {
    const matched = warningResult?.matchedCount ?? 0;
    const total = warningResult?.total ?? 0;
    return (
      <div className="dashboard-warning-stat">
        <div className="dashboard-warning-stat-number">
          {matched}/{total}
        </div>
        <div className="dashboard-kpi-label">命中 / 总数</div>
      </div>
    );
  }

  if (widget.type === 'WARNING_TABLE') {
    const rows = (warningResult?.rows ?? []).filter(
      (row) => row._triggered === true || row._triggered === 'true',
    );
    const columns = buildTableColumns(
      rows,
      warningResult?.headers,
      widget.dataSource?.columnLabels,
      widget.dataSource?.valueMappings,
    );
    return (
      <div className="dashboard-table" style={{ height: '100%', overflow: 'auto' }}>
        <Table
          size="small"
          pagination={false}
          columns={columns}
          dataSource={rows.map((row, i) => ({ ...row, key: i }))}
          scroll={{ y: 160 }}
        />
      </div>
    );
  }

  return null;
}
