import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { EyeOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import GuidePageShell from '../components/GuidePageShell';
import PageHeader from '../components/PageHeader';
import { useTutorialDemo } from '../guide/useTutorialDemo';
import SqlPreviewBlock from '../components/SqlPreviewBlock';
import { warningApi } from '../api/warning';
import { metricApi } from '../api/metric';
import type { MetricDefinition, WarningRule, WarningRulePreviewResult } from '../api/types';

export default function WarningRulePage() {
  const [loading, setLoading] = useState(false);
  const [rules, setRules] = useState<WarningRule[]>([]);
  const [metrics, setMetrics] = useState<MetricDefinition[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<WarningRule | null>(null);
  const [form] = Form.useForm<WarningRule>();

  const { onSaveSuccess } = useTutorialDemo('warning-rules', async (outcome) => {
    if (outcome.type !== 'form') {
      return;
    }
    setEditing(null);
    form.resetFields();
    form.setFieldsValue(outcome.values as unknown as WarningRule);
    setModalOpen(true);
  });
  const [previewModalOpen, setPreviewModalOpen] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewRule, setPreviewRule] = useState<WarningRule | null>(null);
  const [previewResult, setPreviewResult] = useState<WarningRulePreviewResult | null>(null);
  const [previewPage, setPreviewPage] = useState({ pageIndex: 1, pageSize: 20 });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [ruleList, metricList] = await Promise.all([
        warningApi.list(),
        metricApi.listDefinitions(),
      ]);
      setRules(ruleList);
      setMetrics(metricList);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ enabled: true, warningLevel: 2 });
    setModalOpen(true);
  };

  const openEdit = (record: WarningRule) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    if (editing?.id) {
      values.id = editing.id;
      values.code = editing.code;
    }
    await warningApi.save(values);
    message.success('预警规则已保存');
    setModalOpen(false);
    load();
    onSaveSuccess();
  };

  const metricOptions = metrics.map((m) => ({
    label: `${m.name} (${m.code})`,
    value: m.code,
  }));

  const loadPreview = async (rule: WarningRule, pageIndex: number, pageSize: number) => {
    if (!rule.id) {
      return;
    }
    setPreviewLoading(true);
    try {
      const result = await warningApi.previewRule(rule.id, pageIndex, pageSize);
      setPreviewResult(result);
      setPreviewPage({ pageIndex, pageSize });
    } catch {
      setPreviewResult(null);
    } finally {
      setPreviewLoading(false);
    }
  };

  const openPreview = (record: WarningRule) => {
    setPreviewRule(record);
    setPreviewResult(null);
    setPreviewPage({ pageIndex: 1, pageSize: 20 });
    setPreviewModalOpen(true);
    loadPreview(record, 1, 20);
  };

  const previewColumns: ColumnsType<Record<string, unknown>> = (() => {
    const keys = previewResult?.rows?.length
      ? Object.keys(previewResult.rows[0])
      : Object.keys(previewResult?.headers || {});
    return keys.map((key) => ({
      title: previewResult?.headers?.[key] || key,
      dataIndex: key,
      ellipsis: key !== '_triggered',
      render: (value: unknown) => {
        if (key === '_triggered') {
          return value ? (
            <Tag color="error">触发</Tag>
          ) : (
            <Tag color="default">未触发</Tag>
          );
        }
        return value != null ? String(value) : '-';
      },
    }));
  })();

  const columns: ColumnsType<WarningRule> = [
    { title: '名称', dataIndex: 'name', width: 160 },
    { title: '编码', dataIndex: 'code', width: 120 },
    {
      title: '关联指标',
      dataIndex: 'metricCodes',
      width: 200,
      render: (codes: string[]) =>
        codes?.map((c) => (
          <Tag key={c} color="blue">
            {c}
          </Tag>
        )),
    },
    { title: '表达式', dataIndex: 'expression', ellipsis: true },
    {
      title: '级别',
      dataIndex: 'warningLevel',
      width: 70,
      render: (v: number) => (v != null ? <Tag color="orange">L{v}</Tag> : '-'),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 80,
      render: (v: boolean) =>
        v ? <Tag color="success">启用</Tag> : <Tag color="default">禁用</Tag>,
    },
    {
      title: '操作',
      width: 200,
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => openPreview(record)}
          >
            预览数据
          </Button>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该预警规则？"
            onConfirm={async () => {
              await warningApi.delete(record.id!);
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

  return (
    <>
      <PageHeader
        title="预警规则"
        description="基于指标表达式配置预警，关联多个指标 code"
      />
      <div className="page-toolbar">
        <div className="page-toolbar-left" />
        <Space>
          <Button icon={<ReloadOutlined />} onClick={load}>
            刷新
          </Button>
          <Button id="guide-primary-action" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建规则
          </Button>
        </Space>
      </div>
      <GuidePageShell>
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rules}
          pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
        />
      </GuidePageShell>

      <Modal
        title={`数据预览 — ${previewRule?.name || ''}`}
        open={previewModalOpen}
        onCancel={() => setPreviewModalOpen(false)}
        footer={null}
        width={900}
      >
        <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
          表达式: {previewResult?.expression || previewRule?.expression} · 本页触发{' '}
          {previewResult?.matchedCount ?? 0} 条
        </Typography.Paragraph>
        {previewResult?.sql && (
          <div style={{ marginBottom: 16 }}>
            <Typography.Text strong>预警 SQL</Typography.Text>
            <SqlPreviewBlock sql={previewResult.sql} maxHeight={180} />
          </div>
        )}
        <Table
          rowKey={(_, i) => String(i)}
          size="small"
          loading={previewLoading}
          columns={previewColumns}
          dataSource={previewResult?.rows || []}
          scroll={{ x: true }}
          rowClassName={(record) => (record._triggered ? 'warning-row-triggered' : '')}
          locale={{ emptyText: previewLoading ? '加载中...' : '暂无数据' }}
          pagination={{
            current: previewPage.pageIndex,
            pageSize: previewPage.pageSize,
            total: previewResult?.total ?? 0,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, pageSize) => {
              if (previewRule) {
                loadPreview(previewRule, page, pageSize);
              }
            },
          }}
        />
      </Modal>

      <Modal
        title={editing ? '编辑预警规则' : '新建预警规则'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSave}
        width={600}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="规则名称" rules={[{ required: true }]}>
            <Input placeholder="如 利润过低预警" />
          </Form.Item>
          <Form.Item
            name="code"
            label="规则编码"
            rules={[{ required: true, message: '请输入规则编码' }]}
          >
            <Input disabled={!!editing} placeholder="如 low_profit" />
          </Form.Item>
          <Form.Item name="catalogCode" label="目录编码">
            <Input placeholder="如 finance" />
          </Form.Item>
          <Form.Item
            name="metricCodes"
            label="关联指标"
            rules={[{ required: true, message: '请选择至少一个指标' }]}
          >
            <Select
              mode="multiple"
              placeholder="选择指标"
              options={metricOptions}
              optionFilterProp="label"
            />
          </Form.Item>
          <Form.Item
            name="expression"
            label="预警表达式"
            rules={[{ required: true }]}
            extra="如 profit < 500 或 revenue > 1000"
          >
            <Input.TextArea rows={3} placeholder="profit < 500" />
          </Form.Item>
          <Form.Item name="warningLevel" label="预警级别">
            <Select
              options={[1, 2, 3].map((l) => ({ label: `级别 ${l}`, value: l }))}
            />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="comments" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
