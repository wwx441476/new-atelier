import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined, CodeOutlined, SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '../components/PageHeader';
import { metricApi } from '../api/metric';
import { datasourceApi } from '../api/datasource';
import type {
  AggregationType,
  DataSourceResponse,
  MetricDefinition,
  MetricType,
  QueryResult,
} from '../api/types';

const METRIC_TYPES: { label: string; value: MetricType }[] = [
  { label: '表指标', value: 'TABLE' },
  { label: 'SQL 指标', value: 'SQL' },
  { label: '复合指标', value: 'COMPOSITE' },
];

const AGGREGATIONS: AggregationType[] = ['NONE', 'SUM', 'COUNT', 'AVG', 'MAX', 'MIN'];

export default function MetricPage() {
  const [loading, setLoading] = useState(false);
  const [metrics, setMetrics] = useState<MetricDefinition[]>([]);
  const [datasources, setDatasources] = useState<DataSourceResponse[]>([]);
  const [catalogFilter, setCatalogFilter] = useState<string | undefined>();
  const [modalOpen, setModalOpen] = useState(false);
  const [sqlModalOpen, setSqlModalOpen] = useState(false);
  const [queryModalOpen, setQueryModalOpen] = useState(false);
  const [editing, setEditing] = useState<MetricDefinition | null>(null);
  const [sqlPreview, setSqlPreview] = useState<{ sql: string; columns: string[] } | null>(null);
  const [queryResult, setQueryResult] = useState<QueryResult | null>(null);
  const [queryLoading, setQueryLoading] = useState(false);
  const [previewCode, setPreviewCode] = useState('');
  const [form] = Form.useForm<MetricDefinition>();
  const [queryForm] = Form.useForm();
  const metricType = Form.useWatch('type', form);

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
  }, [load]);

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
  };

  const handlePreviewSql = async (code: string) => {
    const result = await metricApi.previewSql(code);
    setSqlPreview({ sql: result.sql, columns: result.columns });
    setPreviewCode(code);
    setSqlModalOpen(true);
  };

  const openQueryPreview = (code: string) => {
    setPreviewCode(code);
    queryForm.resetFields();
    queryForm.setFieldsValue({
      metricCodes: [code],
      filters: [{ field: '', operator: 'IN', values: '' }],
    });
    setQueryResult(null);
    setQueryModalOpen(true);
  };

  const handleQuery = async () => {
    const values = await queryForm.validateFields();
    const filters = (values.filters || [])
      .filter((f: { field?: string; values?: string }) => f.field && f.values)
      .map((f: { field: string; operator: string; values: string }) => ({
        field: f.field,
        operator: f.operator || 'IN',
        values: f.values.split(',').map((v: string) => v.trim()).filter(Boolean),
      }));
    setQueryLoading(true);
    try {
      const result = await metricApi.query({
        metricCodes: values.metricCodes,
        filters,
        pageIndex: 1,
        pageSize: 50,
      });
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
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建指标
          </Button>
        </Space>
      </div>
      <Table
        rowKey="code"
        loading={loading}
        columns={columns}
        dataSource={filtered}
        scroll={{ x: 1000 }}
        pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
      />

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
                  <Space key={key} align="baseline" style={{ display: 'flex', marginBottom: 8 }}>
                    <Form.Item {...rest} name={[name, 'dimensionCode']} rules={[{ required: true }]}>
                      <Input placeholder="维度编码" style={{ width: 120 }} />
                    </Form.Item>
                    <Form.Item {...rest} name={[name, 'fieldCode']} rules={[{ required: true }]}>
                      <Input placeholder="物理字段" style={{ width: 120 }} />
                    </Form.Item>
                    <Form.Item {...rest} name={[name, 'fieldName']}>
                      <Input placeholder="展示名" style={{ width: 100 }} />
                    </Form.Item>
                    <Form.Item {...rest} name={[name, 'sort']}>
                      <Input placeholder="排序" style={{ width: 60 }} />
                    </Form.Item>
                    <Button type="link" danger onClick={() => remove(name)}>
                      删除
                    </Button>
                  </Space>
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
        width={720}
      >
        {sqlPreview && (
          <>
            <Typography.Paragraph type="secondary">
              列: {sqlPreview.columns.join(', ')}
            </Typography.Paragraph>
            <pre
              style={{
                background: '#f6f8fa',
                padding: 16,
                borderRadius: 6,
                overflow: 'auto',
                fontSize: 13,
              }}
            >
              {sqlPreview.sql}
            </pre>
          </>
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
          <Form.List name="filters">
            {(fields, { add, remove }) => (
              <>
                <Typography.Text strong>过滤条件</Typography.Text>
                {fields.map(({ key, name, ...rest }) => (
                  <Space key={key} align="baseline" style={{ display: 'flex', marginTop: 8 }}>
                    <Form.Item {...rest} name={[name, 'field']} label={name === 0 ? '字段' : ''}>
                      <Input placeholder="dept_code" style={{ width: 140 }} />
                    </Form.Item>
                    <Form.Item {...rest} name={[name, 'operator']} label={name === 0 ? '运算符' : ''}>
                      <Select
                        style={{ width: 100 }}
                        options={['IN', 'EQ', 'GT', 'LT', 'GTE', 'LTE'].map((o) => ({
                          label: o,
                          value: o,
                        }))}
                      />
                    </Form.Item>
                    <Form.Item
                      {...rest}
                      name={[name, 'values']}
                      label={name === 0 ? '值（逗号分隔）' : ''}
                    >
                      <Input placeholder="001,002" style={{ width: 180 }} />
                    </Form.Item>
                    <Button type="link" danger onClick={() => remove(name)}>
                      删除
                    </Button>
                  </Space>
                ))}
                <Button type="dashed" onClick={() => add()} style={{ marginTop: 8 }}>
                  添加过滤条件
                </Button>
              </>
            )}
          </Form.List>
        </Form>
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
