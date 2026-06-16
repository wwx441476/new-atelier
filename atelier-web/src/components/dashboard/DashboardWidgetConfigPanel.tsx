import { useEffect, useState } from 'react';
import { Button, Form, Input, InputNumber, Radio, Select, Typography } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import { datasourceApi } from '../../api/datasource';
import type {
  DashboardWidget,
  DataSourceResponse,
  DbTableInfo,
  MetricDefinition,
  WarningRule,
  Dimension,
} from '../../api/types';
import ColumnLabelsEditor from './ColumnLabelsEditor';
import ValueMappingsEditor from './ValueMappingsEditor';

interface DashboardWidgetConfigPanelProps {
  widget: DashboardWidget | null;
  metrics: MetricDefinition[];
  rules: WarningRule[];
  datasources: DataSourceResponse[];
  dimensions: Dimension[];
  onChange: (widget: DashboardWidget) => void;
  onDelete: (widgetId: string) => void;
}

export default function DashboardWidgetConfigPanel({
  widget,
  metrics,
  rules,
  datasources,
  dimensions,
  onChange,
  onDelete,
}: DashboardWidgetConfigPanelProps) {
  const [tables, setTables] = useState<DbTableInfo[]>([]);
  const [tablesLoading, setTablesLoading] = useState(false);
  const [tableColumns, setTableColumns] = useState<string[]>([]);

  useEffect(() => {
    if (!widget?.type.startsWith('SQL_') || !widget.dataSource?.datasourceId) {
      setTables([]);
      return;
    }
    let cancelled = false;
    setTablesLoading(true);
    void datasourceApi
      .browseTables(widget.dataSource.datasourceId, widget.dataSource.schema)
      .then((list) => {
        if (!cancelled) {
          setTables(list);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setTables([]);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setTablesLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [widget?.type, widget?.dataSource?.datasourceId, widget?.dataSource?.schema]);

  useEffect(() => {
    if (
      widget?.type !== 'SQL_TABLE' ||
      widget.dataSource?.queryMode !== 'TABLE' ||
      !widget.dataSource?.datasourceId ||
      !widget.dataSource?.tableName
    ) {
      setTableColumns([]);
      return;
    }
    let cancelled = false;
    void datasourceApi
      .browseColumns(
        widget.dataSource.datasourceId,
        widget.dataSource.tableName,
        widget.dataSource.schema,
      )
      .then((cols) => {
        if (!cancelled) {
          setTableColumns(cols.map((col) => col.name));
        }
      })
      .catch(() => {
        if (!cancelled) {
          setTableColumns([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [
    widget?.type,
    widget?.dataSource?.datasourceId,
    widget?.dataSource?.tableName,
    widget?.dataSource?.schema,
    widget?.dataSource?.queryMode,
  ]);

  if (!widget) {
    return (
      <div className="dashboard-config-panel">
        <Typography.Text type="secondary">选中画布上的组件以编辑属性</Typography.Text>
      </div>
    );
  }

  const patch = (partial: Partial<DashboardWidget>) => {
    onChange({ ...widget, ...partial });
  };

  const patchDataSource = (partial: NonNullable<DashboardWidget['dataSource']>) => {
    onChange({
      ...widget,
      dataSource: { ...widget.dataSource, ...partial },
    });
  };

  const patchStyle = (partial: NonNullable<DashboardWidget['style']>) => {
    onChange({
      ...widget,
      style: { ...widget.style, ...partial },
    });
  };

  const queryMode = widget.dataSource?.queryMode ?? 'SQL';

  return (
    <div className="dashboard-config-panel">
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
        <Typography.Text strong>组件属性</Typography.Text>
        <Button
          danger
          size="small"
          icon={<DeleteOutlined />}
          onClick={() => onDelete(widget.id)}
        >
          删除
        </Button>
      </div>

      <Form layout="vertical" size="small">
        <Form.Item label="标题">
          <Input value={widget.title} onChange={(e) => patch({ title: e.target.value })} />
        </Form.Item>

        {widget.type === 'TITLE' && (
          <>
            <Form.Item label="文本内容">
              <Input.TextArea
                rows={2}
                value={widget.content}
                onChange={(e) => patch({ content: e.target.value })}
              />
            </Form.Item>
            <Form.Item label="字号">
              <InputNumber
                min={12}
                max={72}
                style={{ width: '100%' }}
                value={widget.style?.fontSize}
                onChange={(v) => patchStyle({ fontSize: v ?? 28 })}
              />
            </Form.Item>
            <Form.Item label="颜色">
              <Input
                value={widget.style?.color}
                onChange={(e) => patchStyle({ color: e.target.value })}
              />
            </Form.Item>
            <Form.Item label="对齐">
              <Select
                value={widget.style?.textAlign ?? 'center'}
                onChange={(v) => patchStyle({ textAlign: v })}
                options={[
                  { label: '左对齐', value: 'left' },
                  { label: '居中', value: 'center' },
                  { label: '右对齐', value: 'right' },
                ]}
              />
            </Form.Item>
          </>
        )}

        {widget.type.startsWith('METRIC') && (
          <>
            <Form.Item label="绑定指标">
              <Select
                mode="multiple"
                value={widget.dataSource?.metricCodes ?? []}
                onChange={(codes) => patchDataSource({ bindType: 'METRIC', metricCodes: codes })}
                options={metrics.map((m) => ({ label: `${m.name} (${m.code})`, value: m.code }))}
                placeholder="选择指标"
              />
            </Form.Item>
            {(widget.type === 'METRIC_VALUE' || widget.type === 'METRIC_CHART') && (
              <Form.Item label="数值字段">
                <Input
                  placeholder="结果列 code，如 revenue"
                  value={widget.dataSource?.valueField}
                  onChange={(e) => patchDataSource({ valueField: e.target.value })}
                />
              </Form.Item>
            )}
            {widget.type === 'METRIC_VALUE' && (
              <>
                <Form.Item label="显示格式">
                  <Select
                    allowClear
                    placeholder="选择预设或下方自定义"
                    value={
                      widget.dataSource?.valueFormat ||
                      (widget.dataSource?.valueSuffix ? `suffix:${widget.dataSource.valueSuffix}` : undefined)
                    }
                    onChange={(v) => {
                      if (!v) {
                        patchDataSource({
                          valueFormat: undefined,
                          valuePrefix: undefined,
                          valueSuffix: undefined,
                        });
                        return;
                      }
                      if (v.startsWith('suffix:')) {
                        patchDataSource({
                          valueFormat: undefined,
                          valuePrefix: undefined,
                          valueSuffix: v.slice(7),
                        });
                        return;
                      }
                      patchDataSource({
                        valueFormat: v,
                        valuePrefix: undefined,
                        valueSuffix: undefined,
                      });
                    }}
                    options={[
                      { label: '{value}美元', value: '{value}美元' },
                      { label: '{value}元', value: '{value}元' },
                      { label: '¥{value}', value: '¥{value}' },
                      { label: '{value}%', value: '{value}%' },
                    ]}
                  />
                </Form.Item>
                <Form.Item label="自定义格式">
                  <Input
                    placeholder="{value}美元，{value} 为数值占位符"
                    value={widget.dataSource?.valueFormat}
                    onChange={(e) => patchDataSource({ valueFormat: e.target.value || undefined })}
                  />
                </Form.Item>
                <Form.Item label="小数位数">
                  <InputNumber
                    min={0}
                    max={6}
                    style={{ width: '100%' }}
                    value={widget.dataSource?.decimalPlaces}
                    onChange={(v) => patchDataSource({ decimalPlaces: v ?? undefined })}
                  />
                </Form.Item>
              </>
            )}
            {widget.type === 'METRIC_CHART' && (
              <>
                <Form.Item label="分类字段">
                  <Input
                    placeholder="维度列 code"
                    value={widget.dataSource?.categoryField}
                    onChange={(e) => patchDataSource({ categoryField: e.target.value })}
                  />
                </Form.Item>
                <Form.Item label="图表类型">
                  <Select
                    value={widget.dataSource?.chartType ?? 'bar'}
                    onChange={(v) => patchDataSource({ chartType: v })}
                    options={[
                      { label: '柱状图', value: 'bar' },
                      { label: '折线图', value: 'line' },
                      { label: '饼图', value: 'pie' },
                    ]}
                  />
                </Form.Item>
              </>
            )}
            {widget.type === 'METRIC_CHART' && widget.dataSource?.categoryField && (
              <Form.Item label="编码值映射">
                <ValueMappingsEditor
                  valueMappings={widget.dataSource?.valueMappings}
                  activeField={widget.dataSource.categoryField}
                  suggestedFields={[widget.dataSource.categoryField]}
                  dimensions={dimensions}
                  onChange={(valueMappings) => patchDataSource({ valueMappings })}
                />
              </Form.Item>
            )}
            {(widget.type === 'METRIC_TABLE' || widget.type === 'METRIC_CHART') && (
              <Form.Item label="数据行数">
                <InputNumber
                  min={1}
                  max={100}
                  style={{ width: '100%' }}
                  value={widget.dataSource?.pageSize ?? 10}
                  onChange={(v) => patchDataSource({ pageSize: v ?? 10 })}
                />
              </Form.Item>
            )}
            {widget.type === 'METRIC_TABLE' && (
              <Form.Item label="列显示名">
                <ColumnLabelsEditor
                  value={widget.dataSource?.columnLabels}
                  onChange={(columnLabels) => patchDataSource({ columnLabels })}
                />
              </Form.Item>
            )}
            {widget.type === 'METRIC_TABLE' && (
              <Form.Item label="编码值映射">
                <ValueMappingsEditor
                  valueMappings={widget.dataSource?.valueMappings}
                  activeField="dept_code"
                  suggestedFields={['dept_code', 'fiscal_year']}
                  dimensions={dimensions}
                  onChange={(valueMappings) => patchDataSource({ valueMappings })}
                />
              </Form.Item>
            )}
          </>
        )}

        {widget.type.startsWith('SQL_') && (
          <>
            <Form.Item label="数据源">
              <Select
                value={widget.dataSource?.datasourceId}
                onChange={(datasourceId) =>
                  patchDataSource({ bindType: 'SQL', datasourceId, tableName: undefined })
                }
                options={datasources.map((ds) => ({
                  label: `${ds.name} (${ds.id})`,
                  value: ds.id,
                }))}
                placeholder="选择数据源"
              />
            </Form.Item>
            <Form.Item label="查询方式">
              <Radio.Group
                value={queryMode}
                onChange={(e) =>
                  patchDataSource({
                    bindType: 'SQL',
                    queryMode: e.target.value,
                    sql: e.target.value === 'TABLE' ? undefined : widget.dataSource?.sql,
                    tableName:
                      e.target.value === 'SQL' ? undefined : widget.dataSource?.tableName,
                  })
                }
              >
                <Radio.Button value="TABLE">选择数据表</Radio.Button>
                <Radio.Button value="SQL">自定义 SQL</Radio.Button>
              </Radio.Group>
            </Form.Item>
            {queryMode === 'TABLE' ? (
              <>
                <Form.Item label="Schema">
                  <Input
                    placeholder="可选，如 PUBLIC"
                    value={widget.dataSource?.schema}
                    onChange={(e) =>
                      patchDataSource({ schema: e.target.value || undefined, tableName: undefined })
                    }
                  />
                </Form.Item>
                <Form.Item label="数据表">
                  <Select
                    showSearch
                    loading={tablesLoading}
                    value={widget.dataSource?.tableName}
                    onChange={(tableName) => patchDataSource({ tableName })}
                    options={tables.map((t) => ({
                      label: t.schema ? `${t.schema}.${t.name}` : t.name,
                      value: t.name,
                    }))}
                    placeholder="选择表"
                  />
                </Form.Item>
              </>
            ) : (
              <Form.Item label="SELECT 语句">
                <Input.TextArea
                  rows={4}
                  placeholder="SELECT dept_code, SUM(amount) AS total FROM orders GROUP BY dept_code"
                  value={widget.dataSource?.sql}
                  onChange={(e) => patchDataSource({ sql: e.target.value })}
                />
              </Form.Item>
            )}
            {(widget.type === 'SQL_VALUE' || widget.type === 'SQL_CHART') && (
              <Form.Item label="数值字段">
                <Input
                  placeholder="结果列名，如 total_amount"
                  value={widget.dataSource?.valueField}
                  onChange={(e) => patchDataSource({ valueField: e.target.value })}
                />
              </Form.Item>
            )}
            {widget.type === 'SQL_VALUE' && (
              <>
                <Form.Item label="显示格式">
                  <Select
                    allowClear
                    placeholder="选择预设"
                    value={widget.dataSource?.valueFormat}
                    onChange={(v) => patchDataSource({ valueFormat: v || undefined })}
                    options={[
                      { label: '{value}美元', value: '{value}美元' },
                      { label: '{value}元', value: '{value}元' },
                      { label: '¥{value}', value: '¥{value}' },
                      { label: '{value}%', value: '{value}%' },
                    ]}
                  />
                </Form.Item>
                <Form.Item label="自定义格式">
                  <Input
                    placeholder="{value}美元"
                    value={widget.dataSource?.valueFormat}
                    onChange={(e) => patchDataSource({ valueFormat: e.target.value || undefined })}
                  />
                </Form.Item>
              </>
            )}
            {widget.type === 'SQL_CHART' && (
              <>
                <Form.Item label="分类字段">
                  <Input
                    placeholder="X 轴列名，如 dept_code"
                    value={widget.dataSource?.categoryField}
                    onChange={(e) => patchDataSource({ categoryField: e.target.value })}
                  />
                </Form.Item>
                <Form.Item label="图表类型">
                  <Select
                    value={widget.dataSource?.chartType ?? 'bar'}
                    onChange={(v) => patchDataSource({ chartType: v })}
                    options={[
                      { label: '柱状图', value: 'bar' },
                      { label: '折线图', value: 'line' },
                      { label: '饼图', value: 'pie' },
                    ]}
                  />
                </Form.Item>
              </>
            )}
            {widget.type === 'SQL_CHART' && widget.dataSource?.categoryField && (
              <Form.Item label="编码值映射">
                <ValueMappingsEditor
                  valueMappings={widget.dataSource?.valueMappings}
                  activeField={widget.dataSource.categoryField}
                  suggestedFields={[widget.dataSource.categoryField]}
                  dimensions={dimensions}
                  onChange={(valueMappings) => patchDataSource({ valueMappings })}
                />
              </Form.Item>
            )}
            <Form.Item label="数据行数">
              <InputNumber
                min={1}
                max={100}
                style={{ width: '100%' }}
                value={widget.dataSource?.pageSize ?? 10}
                onChange={(v) => patchDataSource({ pageSize: v ?? 10 })}
              />
            </Form.Item>
            {widget.type === 'SQL_TABLE' && (
              <Form.Item label="列显示名">
                <ColumnLabelsEditor
                  value={widget.dataSource?.columnLabels}
                  suggestedFields={tableColumns}
                  onChange={(columnLabels) => patchDataSource({ columnLabels })}
                />
              </Form.Item>
            )}
            {widget.type === 'SQL_TABLE' && (
              <Form.Item label="编码值映射">
                <ValueMappingsEditor
                  valueMappings={widget.dataSource?.valueMappings}
                  activeField="dept_code"
                  suggestedFields={tableColumns}
                  dimensions={dimensions}
                  onChange={(valueMappings) => patchDataSource({ valueMappings })}
                />
              </Form.Item>
            )}
          </>
        )}

        {widget.type.startsWith('WARNING') && (
          <>
            <Form.Item label="绑定预警规则">
              <Select
                value={widget.dataSource?.ruleId}
                onChange={(ruleId) => patchDataSource({ bindType: 'WARNING', ruleId })}
                options={rules.map((r) => ({
                  label: `${r.name}${r.code ? ` (${r.code})` : ''}`,
                  value: r.id,
                }))}
                placeholder="选择规则"
                allowClear
              />
            </Form.Item>
            <Form.Item label="预览行数">
              <InputNumber
                min={1}
                max={100}
                style={{ width: '100%' }}
                value={widget.dataSource?.pageSize ?? 10}
                onChange={(v) => patchDataSource({ pageSize: v ?? 10 })}
              />
            </Form.Item>
            {widget.type === 'WARNING_TABLE' && (
              <Form.Item label="列显示名">
                <ColumnLabelsEditor
                  value={widget.dataSource?.columnLabels}
                  onChange={(columnLabels) => patchDataSource({ columnLabels })}
                />
              </Form.Item>
            )}
            {widget.type === 'WARNING_TABLE' && (
              <Form.Item label="编码值映射">
                <ValueMappingsEditor
                  valueMappings={widget.dataSource?.valueMappings}
                  activeField="dept_code"
                  suggestedFields={['dept_code', 'fiscal_year']}
                  dimensions={dimensions}
                  onChange={(valueMappings) => patchDataSource({ valueMappings })}
                />
              </Form.Item>
            )}
          </>
        )}
      </Form>
    </div>
  );
}
