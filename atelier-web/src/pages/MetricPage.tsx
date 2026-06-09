import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined, CodeOutlined, SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import GuidePageShell from '../components/GuidePageShell';
import PageHeader from '../components/PageHeader';
import { useTutorialDemo } from '../guide/useTutorialDemo';
import SqlPreviewBlock from '../components/SqlPreviewBlock';
import { metricApi } from '../api/metric';
import { datasourceApi } from '../api/datasource';
import { dimensionApi } from '../api/dimension';
import { metadataApi } from '../api/metadata';
import type {
  AggregationType,
  DataSourceResponse,
  Dimension,
  MetaTableField,
  MetricDefinition,
  MetricQueryRequest,
  MetricType,
  QueryResult,
  SqlPreviewResult,
} from '../api/types';

const METRIC_TYPES: { label: string; value: MetricType }[] = [
  { label: '表指标', value: 'TABLE' },
  { label: 'SQL 指标', value: 'SQL' },
  { label: '复合指标', value: 'COMPOSITE' },
];

const AGGREGATIONS: AggregationType[] = ['NONE', 'SUM', 'COUNT', 'AVG', 'MAX', 'MIN'];

type QueryFilterGroupForm = {
  conditions?: Array<{ field?: string; operator?: string; values?: string }>;
};

function buildQueryRequest(
  metricCodes: string[],
  filterGroups: QueryFilterGroupForm[],
): MetricQueryRequest {
  const groups = (filterGroups || [])
    .map((group) => ({
      conditions: (group.conditions || [])
        .filter((c) => c.field && c.values)
        .map((c) => ({
          field: c.field!,
          operator: c.operator || 'IN',
          values: c.values!.split(',').map((v) => v.trim()).filter(Boolean),
        })),
    }))
    .filter((group) => group.conditions.length > 0);
  const flatFilters = groups.length === 1 ? groups[0].conditions : undefined;
  return {
    metricCodes,
    filterGroups: groups.length > 1 ? groups : undefined,
    filters: flatFilters,
  };
}

function formatSqlColumns(columns: SqlPreviewResult['columns']): string {
  if (Array.isArray(columns)) {
    return columns.join(', ');
  }
  if (columns && typeof columns === 'object') {
    return Object.entries(columns)
      .map(([code, label]) => (label && label !== code ? `${label} (${code})` : code))
      .join(', ');
  }
  return '';
}

interface DimensionBindingFieldsProps {
  form: ReturnType<typeof Form.useForm<MetricDefinition>>[0];
  name: number;
  rest: { fieldKey?: number };
  dimensionOptions: { label: string; value: string }[];
  dimensionByCode: Record<string, Dimension>;
  fieldsByTableId: Record<string, MetaTableField[]>;
  allFieldOptions: { label: string; value: string }[];
  onRemove: () => void;
}

function DimensionBindingFields({
  form,
  name,
  rest,
  dimensionOptions,
  dimensionByCode,
  fieldsByTableId,
  allFieldOptions,
  onRemove,
}: DimensionBindingFieldsProps) {
  const dimensionCode = Form.useWatch(['dimensions', name, 'dimensionCode'], form);

  const fieldOptions = useMemo(() => {
    const dim = dimensionCode ? dimensionByCode[dimensionCode] : undefined;
    const fields =
      dim?.metaTableId && fieldsByTableId[dim.metaTableId]
        ? fieldsByTableId[dim.metaTableId]
        : undefined;
    const source = fields ?? Object.values(fieldsByTableId).flat();
    const seen = new Set<string>();
    return source
      .filter((f) => {
        if (seen.has(f.fieldCode)) return false;
        seen.add(f.fieldCode);
        return true;
      })
      .map((f) => ({ label: `${f.fieldName} (${f.fieldCode})`, value: f.fieldCode }));
  }, [dimensionCode, dimensionByCode, fieldsByTableId]);

  const handleDimensionChange = (code: string) => {
    const dim = dimensionByCode[code];
    if (!dim) return;
    const dimensions = [...(form.getFieldValue('dimensions') || [])];
    const row = dimensions[name] || {};
    dimensions[name] = {
      ...row,
      dimensionCode: code,
      fieldName: row.fieldName || dim.name,
    };
    form.setFieldsValue({ dimensions });
  };

  return (
    <Space align="baseline" style={{ display: 'flex', marginBottom: 8 }}>
      <Form.Item {...rest} name={[name, 'dimensionCode']} rules={[{ required: true }]}>
        <Select
          placeholder="维度编码"
          style={{ width: 140 }}
          showSearch
          optionFilterProp="label"
          options={dimensionOptions}
          onChange={handleDimensionChange}
        />
      </Form.Item>
      <Form.Item {...rest} name={[name, 'fieldCode']} rules={[{ required: true }]}>
        <Select
          placeholder="物理字段"
          style={{ width: 160 }}
          showSearch
          optionFilterProp="label"
          options={fieldOptions.length > 0 ? fieldOptions : allFieldOptions}
        />
      </Form.Item>
      <Form.Item {...rest} name={[name, 'fieldName']}>
        <Input placeholder="展示名" style={{ width: 100 }} />
      </Form.Item>
      <Form.Item {...rest} name={[name, 'sort']}>
        <Input placeholder="排序" style={{ width: 60 }} />
      </Form.Item>
      <Button type="link" danger onClick={onRemove}>
        删除
      </Button>
    </Space>
  );
}

export default function MetricPage() {
  const [loading, setLoading] = useState(false);
  const [metrics, setMetrics] = useState<MetricDefinition[]>([]);
  const [datasources, setDatasources] = useState<DataSourceResponse[]>([]);
  const [catalogFilter, setCatalogFilter] = useState<string | undefined>();
  const [modalOpen, setModalOpen] = useState(false);
  const [sqlModalOpen, setSqlModalOpen] = useState(false);
  const [queryModalOpen, setQueryModalOpen] = useState(false);
  const [editing, setEditing] = useState<MetricDefinition | null>(null);
  const [sqlPreview, setSqlPreview] = useState<SqlPreviewResult | null>(null);
  const [queryResult, setQueryResult] = useState<QueryResult | null>(null);
  const [queryLoading, setQueryLoading] = useState(false);
  const [queryPreviewSql, setQueryPreviewSql] = useState<SqlPreviewResult | null>(null);
  const [querySqlLoading, setQuerySqlLoading] = useState(false);
  const [previewCode, setPreviewCode] = useState('');
  const [dimensions, setDimensions] = useState<Dimension[]>([]);
  const [fieldsByTableId, setFieldsByTableId] = useState<Record<string, MetaTableField[]>>({});
  const [form] = Form.useForm<MetricDefinition>();

  const { onSaveSuccess } = useTutorialDemo('metrics', async (outcome) => {
    if (outcome.type !== 'form') {
      return;
    }
    setEditing(null);
    form.resetFields();
    form.setFieldsValue(outcome.values as unknown as MetricDefinition);
    setModalOpen(true);
  });
  const [queryForm] = Form.useForm();
  const queryFilterGroups = Form.useWatch('filterGroups', queryForm);
  const metricType = Form.useWatch('type', form);
  const datasourceId = Form.useWatch('datasourceId', form);

  const refreshQuerySql = useCallback(async () => {
    if (!queryModalOpen || !previewCode) return;
    const metricCodes = queryForm.getFieldValue('metricCodes');
    const codes = Array.isArray(metricCodes) ? metricCodes : [previewCode];
    const filterGroups = queryForm.getFieldValue('filterGroups') || [];
    setQuerySqlLoading(true);
    try {
      setQueryPreviewSql(await metricApi.previewQuerySql(buildQueryRequest(codes, filterGroups)));
    } catch {
      setQueryPreviewSql(null);
    } finally {
      setQuerySqlLoading(false);
    }
  }, [queryModalOpen, previewCode, queryForm]);

  useEffect(() => {
    if (!queryModalOpen) return;
    const timer = setTimeout(refreshQuerySql, 300);
    return () => clearTimeout(timer);
  }, [queryFilterGroups, queryModalOpen, previewCode, refreshQuerySql]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setMetrics(await metricApi.listDefinitions());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    datasourceApi.list().then(setDatasources);
    dimensionApi.list().then(setDimensions);
  }, [load]);

  useEffect(() => {
    if (!datasourceId) {
      setFieldsByTableId({});
      return;
    }
    metadataApi.listTables(datasourceId).then(async (tables) => {
      const entries = await Promise.all(
        tables
          .filter((t) => t.id)
          .map(async (t) => [t.id!, await metadataApi.listFields(t.id!)] as const),
      );
      setFieldsByTableId(Object.fromEntries(entries));
    });
  }, [datasourceId]);

  const availableDimensions = useMemo(
    () => dimensions.filter((d) => d.datasourceId === datasourceId),
    [dimensions, datasourceId],
  );

  const dimensionOptions = useMemo(
    () => availableDimensions.map((d) => ({ label: `${d.name} (${d.code})`, value: d.code })),
    [availableDimensions],
  );

  const dimensionByCode = useMemo(
    () => Object.fromEntries(availableDimensions.map((d) => [d.code, d])),
    [availableDimensions],
  );

  const allFieldOptions = useMemo(() => {
    const seen = new Set<string>();
    return Object.values(fieldsByTableId)
      .flat()
      .filter((f) => {
        if (seen.has(f.fieldCode)) return false;
        seen.add(f.fieldCode);
        return true;
      })
      .map((f) => ({ label: `${f.fieldName} (${f.fieldCode})`, value: f.fieldCode }));
  }, [fieldsByTableId]);

  const queryFilterFieldOptions = useMemo(() => {
    const metric = metrics.find((m) => m.code === previewCode);
    return (metric?.dimensions || []).map((d) => ({
      label: d.fieldName ? `${d.fieldName} (${d.fieldCode})` : d.fieldCode,
      value: d.fieldCode,
    }));
  }, [metrics, previewCode]);

  const catalogs = [...new Set(metrics.map((m) => m.catalogCode).filter(Boolean))] as string[];

  const filtered = catalogFilter
    ? metrics.filter((m) => m.catalogCode === catalogFilter)
    : metrics;

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ type: 'TABLE', aggregation: 'SUM', dimensions: [] });
    setModalOpen(true);
  };

  const openEdit = (record: MetricDefinition) => {
    setEditing(record);
    form.setFieldsValue({
      ...record,
      dimensions: record.dimensions || [],
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    await metricApi.saveDefinition(values);
    message.success('指标已保存');
    setModalOpen(false);
    load();
    onSaveSuccess();
  };

  const handlePreviewSql = async (code: string) => {
    const result = await metricApi.previewSql(code);
    setSqlPreview(result);
    setPreviewCode(code);
    setSqlModalOpen(true);
  };

  const createDefaultFilterGroup = (metric?: MetricDefinition) => {
    const defaultField = metric?.dimensions?.[0]?.fieldCode || '';
    return {
      conditions: [{ field: defaultField, operator: 'IN', values: '' }],
    };
  };

  const openQueryPreview = (code: string) => {
    setPreviewCode(code);
    const metric = metrics.find((m) => m.code === code);
    queryForm.resetFields();
    queryForm.setFieldsValue({
      metricCodes: [code],
      filterGroups: [createDefaultFilterGroup(metric)],
    });
    setQueryResult(null);
    setQueryPreviewSql(null);
    setQueryModalOpen(true);
  };

  const handleQuery = async () => {
    const values = await queryForm.validateFields();
    const metricCodes = Array.isArray(values.metricCodes)
      ? values.metricCodes
      : [previewCode];
    const request = buildQueryRequest(metricCodes, values.filterGroups || []);
    setQueryLoading(true);
    try {
      const [result] = await Promise.all([
        metricApi.query({ ...request, pageIndex: 1, pageSize: 50 }),
        metricApi.previewQuerySql(request).then(setQueryPreviewSql),
      ]);
      setQueryResult(result);
    } finally {
      setQueryLoading(false);
    }
  };

  const columns: ColumnsType<MetricDefinition> = [
    { title: '编码', dataIndex: 'code', width: 110 },
    { title: '名称', dataIndex: 'name', width: 120 },
    {
      title: '类型',
      dataIndex: 'type',
      width: 100,
      render: (v: MetricType) => {
        const item = METRIC_TYPES.find((t) => t.value === v);
        return <Tag>{item?.label || v}</Tag>;
      },
    },
    { title: '目录', dataIndex: 'catalogCode', width: 90 },
    { title: '数据源', dataIndex: 'datasourceId', width: 110 },
    { title: '聚合', dataIndex: 'aggregation', width: 80 },
    {
      title: '操作',
      width: 260,
      fixed: 'right',
      render: (_, record) => (
        <Space wrap>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            icon={<CodeOutlined />}
            onClick={() => handlePreviewSql(record.code)}
          >
            SQL
          </Button>
          <Button
            type="link"
            size="small"
            icon={<SearchOutlined />}
            onClick={() => openQueryPreview(record.code)}
          >
            查询
          </Button>
          <Popconfirm
            title="确认删除该指标？"
            onConfirm={async () => {
              await metricApi.deleteDefinition(record.code);
              message.success('已删除');
              load();
            }}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const queryColumns: ColumnsType<Record<string, unknown>> = queryResult?.rows?.length
    ? Object.keys(queryResult.rows[0]).map((key) => ({
        title: queryResult.headers?.[key] || key,
        dataIndex: key,
        ellipsis: true,
      }))
    : [];

  return (
    <>
      <PageHeader
        title="指标管理"
        description="声明式指标定义，支持 SQL 预览与查询调试"
      />
      <div className="page-toolbar">
        <div className="page-toolbar-left">
          <Select
            allowClear
            placeholder="按目录筛选"
            style={{ width: 180 }}
            value={catalogFilter}
            onChange={setCatalogFilter}
            options={catalogs.map((c) => ({ label: c, value: c }))}
          />
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={load}>
            刷新
          </Button>
          <Button id="guide-primary-action" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建指标
          </Button>
        </Space>
      </div>
      <GuidePageShell>
        <Table
          rowKey="code"
          loading={loading}
          columns={columns}
          dataSource={filtered}
          scroll={{ x: 1000 }}
          pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
        />
      </GuidePageShell>

      <Modal
        title={editing ? '编辑指标' : '新建指标'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSave}
        width={720}
        styles={{ body: { maxHeight: '70vh', overflow: 'auto' } }}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="code" label="编码" rules={[{ required: true }]}>
            <Input disabled={!!editing} placeholder="如 revenue" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="catalogCode" label="目录编码">
            <Input placeholder="如 finance" />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select options={METRIC_TYPES} />
          </Form.Item>
          <Form.Item name="datasourceId" label="数据源" rules={[{ required: true }]}>
            <Select
              options={datasources.map((d) => ({ label: `${d.name} (${d.id})`, value: d.id }))}
            />
          </Form.Item>

          {metricType === 'TABLE' && (
            <>
              <Form.Item name="modelCode" label="模型编码">
                <Input placeholder="如 finance_model" />
              </Form.Item>
              <Form.Item name="tableCode" label="表编码">
                <Input placeholder="如 orders" />
              </Form.Item>
              <Form.Item name="fieldCode" label="字段编码">
                <Input placeholder="如 amount" />
              </Form.Item>
              <Form.Item name="aggregation" label="聚合方式">
                <Select options={AGGREGATIONS.map((a) => ({ label: a, value: a }))} />
              </Form.Item>
            </>
          )}

          {metricType === 'SQL' && (
            <Form.Item name="datasetSql" label="数据集 SQL">
              <Input.TextArea rows={4} placeholder="SELECT ..." />
            </Form.Item>
          )}

          {metricType === 'COMPOSITE' && (
            <Form.Item name="formula" label="复合公式">
              <Input placeholder="如 revenue - cost" />
            </Form.Item>
          )}

          <Form.Item name="alias" label="别名">
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>

          <Typography.Text strong>维度绑定</Typography.Text>
          <Form.List name="dimensions">
            {(fields, { add, remove }) => (
              <div style={{ marginTop: 8 }}>
                {fields.map(({ key, name, ...rest }) => (
                  <DimensionBindingFields
                    key={key}
                    form={form}
                    name={name}
                    rest={rest}
                    dimensionOptions={dimensionOptions}
                    dimensionByCode={dimensionByCode}
                    fieldsByTableId={fieldsByTableId}
                    allFieldOptions={allFieldOptions}
                    onRemove={() => remove(name)}
                  />
                ))}
                <Button type="dashed" onClick={() => add()} block>
                  添加维度绑定
                </Button>
              </div>
            )}
          </Form.List>
        </Form>
      </Modal>

      <Modal
        title={`SQL 预览 — ${previewCode}`}
        open={sqlModalOpen}
        onCancel={() => setSqlModalOpen(false)}
        footer={null}
        width={800}
        styles={{ body: { maxHeight: '70vh', overflow: 'auto' } }}
      >
        {sqlPreview && (
          <SqlPreviewBlock
            sql={sqlPreview.sql}
            meta={`列: ${formatSqlColumns(sqlPreview.columns)}`}
          />
        )}
      </Modal>

      <Modal
        title={`查询预览 — ${previewCode}`}
        open={queryModalOpen}
        onCancel={() => setQueryModalOpen(false)}
        width={800}
        footer={[
          <Button key="cancel" onClick={() => setQueryModalOpen(false)}>
            关闭
          </Button>,
          <Button key="query" type="primary" loading={queryLoading} onClick={handleQuery}>
            执行查询
          </Button>,
        ]}
      >
        <Form form={queryForm} layout="vertical">
          <Form.Item name="metricCodes" hidden>
            <Input />
          </Form.Item>
          <Form.List name="filterGroups">
            {(groups, { add: addGroup, remove: removeGroup }) => (
              <>
                <Typography.Text strong>过滤条件</Typography.Text>
                <Typography.Paragraph type="secondary" style={{ marginBottom: 12, fontSize: 13 }}>
                  组内条件以「且」组合，多个条件组之间以「或」组合
                </Typography.Paragraph>
                {groups.map((group, groupIndex) => (
                  <div key={group.key}>
                    {groupIndex > 0 && (
                      <div
                        style={{
                          textAlign: 'center',
                          margin: '8px 0',
                          color: '#1677ff',
                          fontWeight: 500,
                        }}
                      >
                        或
                      </div>
                    )}
                    <div
                      style={{
                        border: '1px solid #d9d9d9',
                        borderRadius: 6,
                        padding: 12,
                        marginBottom: 8,
                        background: '#fafafa',
                      }}
                    >
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          marginBottom: 8,
                        }}
                      >
                        <Typography.Text type="secondary">条件组 {groupIndex + 1}</Typography.Text>
                        {groups.length > 1 && (
                          <Button type="link" danger size="small" onClick={() => removeGroup(group.name)}>
                            删除条件组
                          </Button>
                        )}
                      </div>
                      <Form.List name={[group.name, 'conditions']}>
                        {(conditions, { add: addCond, remove: removeCond }) => (
                          <>
                            {conditions.map((cond, condIndex) => (
                              <div key={cond.key}>
                                {condIndex > 0 && (
                                  <Typography.Text
                                    type="secondary"
                                    style={{ display: 'block', margin: '4px 0 4px 4px' }}
                                  >
                                    且
                                  </Typography.Text>
                                )}
                                <Space align="baseline" style={{ display: 'flex' }}>
                                  <Form.Item
                                    {...cond}
                                    name={[cond.name, 'field']}
                                    label={condIndex === 0 ? '字段' : ''}
                                  >
                                    <Select
                                      placeholder="选择字段"
                                      style={{ width: 160 }}
                                      showSearch
                                      optionFilterProp="label"
                                      options={queryFilterFieldOptions}
                                    />
                                  </Form.Item>
                                  <Form.Item
                                    {...cond}
                                    name={[cond.name, 'operator']}
                                    label={condIndex === 0 ? '运算符' : ''}
                                  >
                                    <Select
                                      style={{ width: 100 }}
                                      options={['IN', 'EQ', 'GT', 'LT', 'GTE', 'LTE'].map((o) => ({
                                        label: o,
                                        value: o,
                                      }))}
                                    />
                                  </Form.Item>
                                  <Form.Item
                                    {...cond}
                                    name={[cond.name, 'values']}
                                    label={condIndex === 0 ? '值（逗号分隔）' : ''}
                                  >
                                    <Input placeholder="001,002" style={{ width: 180 }} />
                                  </Form.Item>
                                  <Button
                                    type="link"
                                    danger
                                    onClick={() => removeCond(cond.name)}
                                    disabled={conditions.length === 1}
                                  >
                                    删除
                                  </Button>
                                </Space>
                              </div>
                            ))}
                            <Button
                              type="dashed"
                              size="small"
                              onClick={() =>
                                addCond({
                                  field: queryFilterFieldOptions[0]?.value || '',
                                  operator: 'IN',
                                  values: '',
                                })
                              }
                              style={{ marginTop: 8 }}
                            >
                              添加条件
                            </Button>
                          </>
                        )}
                      </Form.List>
                    </div>
                  </div>
                ))}
                <Button
                  type="dashed"
                  onClick={() => addGroup(createDefaultFilterGroup(metrics.find((m) => m.code === previewCode)))}
                  block
                >
                  添加条件组
                </Button>
              </>
            )}
          </Form.List>
        </Form>
        <div style={{ marginTop: 16 }}>
          <Typography.Text strong>预览 SQL</Typography.Text>
          <Spin spinning={querySqlLoading}>
            {queryPreviewSql ? (
              <SqlPreviewBlock
                sql={queryPreviewSql.sql}
                meta={`数据源: ${queryPreviewSql.datasourceId}${
                  formatSqlColumns(queryPreviewSql.columns)
                    ? ` · 列: ${formatSqlColumns(queryPreviewSql.columns)}`
                    : ''
                }`}
                maxHeight={200}
              />
            ) : (
              <Typography.Paragraph type="secondary" style={{ margin: '8px 0 0', fontSize: 13 }}>
                {querySqlLoading ? '正在生成 SQL...' : '调整过滤条件后将自动生成 SQL'}
              </Typography.Paragraph>
            )}
          </Spin>
        </div>
        {queryResult && (
          <div style={{ marginTop: 16 }}>
            <Typography.Text type="secondary">共 {queryResult.total} 条</Typography.Text>
            <Table
              rowKey={(_, i) => String(i)}
              size="small"
              style={{ marginTop: 8 }}
              columns={queryColumns}
              dataSource={queryResult.rows}
              pagination={false}
              scroll={{ x: true }}
            />
          </div>
        )}
      </Modal>
    </>
  );
}
