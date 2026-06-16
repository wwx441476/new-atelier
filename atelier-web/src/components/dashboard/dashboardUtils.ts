import type { DashboardWidget, DashboardWidgetType } from '../../api/types';

export const WIDGET_TYPE_LABELS: Record<DashboardWidgetType, string> = {
  TITLE: '标题文本',
  METRIC_VALUE: '指标数值',
  METRIC_CHART: '指标图表',
  METRIC_TABLE: '指标表格',
  WARNING_STAT: '预警统计',
  WARNING_TABLE: '预警表格',
  SQL_VALUE: '数据库数值',
  SQL_CHART: '数据库图表',
  SQL_TABLE: '数据库表格',
};

export const DEFAULT_WIDGET_SIZE: Record<DashboardWidgetType, { w: number; h: number }> = {
  TITLE: { w: 12, h: 2 },
  METRIC_VALUE: { w: 6, h: 4 },
  METRIC_CHART: { w: 12, h: 8 },
  METRIC_TABLE: { w: 12, h: 8 },
  WARNING_STAT: { w: 6, h: 4 },
  WARNING_TABLE: { w: 12, h: 8 },
  SQL_VALUE: { w: 6, h: 4 },
  SQL_CHART: { w: 12, h: 8 },
  SQL_TABLE: { w: 12, h: 8 },
};

const DEFAULT_SQL_DATASOURCE = {
  bindType: 'SQL' as const,
  queryMode: 'TABLE' as const,
  datasourceId: 'ds-demo',
  tableName: 'orders',
  chartType: 'bar' as const,
  pageSize: 10,
};

export function createWidget(type: DashboardWidgetType, y: number): DashboardWidget {
  const size = DEFAULT_WIDGET_SIZE[type];
  let dataSource: DashboardWidget['dataSource'];

  if (type === 'TITLE') {
    dataSource = undefined;
  } else if (type.startsWith('WARNING')) {
    dataSource = { bindType: 'WARNING', pageSize: 10 };
  } else if (type.startsWith('SQL_')) {
    dataSource = {
      ...DEFAULT_SQL_DATASOURCE,
      sql:
        type === 'SQL_VALUE'
          ? 'SELECT SUM(amount) AS total_amount FROM orders'
          : type === 'SQL_CHART'
            ? 'SELECT dept_code, SUM(amount) AS total_amount FROM orders GROUP BY dept_code'
            : undefined,
      valueField: type === 'SQL_VALUE' ? 'total_amount' : 'total_amount',
      categoryField: type === 'SQL_CHART' ? 'dept_code' : undefined,
    };
  } else {
    dataSource = {
      bindType: 'METRIC',
      chartType: 'bar',
      pageSize: 10,
      metricCodes: [],
    };
  }

  return {
    id: `w-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    type,
    title: WIDGET_TYPE_LABELS[type],
    x: 0,
    y,
    w: size.w,
    h: size.h,
    content: type === 'TITLE' ? '大屏标题' : undefined,
    style:
      type === 'TITLE'
        ? { fontSize: 28, color: '#ffffff', textAlign: 'center' }
        : undefined,
    dataSource,
  };
}

export function nextWidgetY(widgets: DashboardWidget[]): number {
  if (widgets.length === 0) {
    return 0;
  }
  return Math.max(...widgets.map((w) => w.y + w.h));
}

export function isQueryResultWidget(type: DashboardWidgetType): boolean {
  return type.startsWith('METRIC_') || type.startsWith('SQL_');
}

export function widgetShellClass(type: DashboardWidgetType): string {
  if (type === 'TITLE') {
    return 'widget-title';
  }
  if (type === 'METRIC_VALUE' || type === 'SQL_VALUE') {
    return 'widget-kpi';
  }
  if (type === 'WARNING_STAT') {
    return 'widget-warning-stat';
  }
  return '';
}
