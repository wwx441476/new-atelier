import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Radio,
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
import DimensionFilterGroupsForm from '../components/DimensionFilterGroupsForm';
import SqlPreviewBlock from '../components/SqlPreviewBlock';
import WarningExpressionField, {
  validateWarningExpressionField,
} from '../components/WarningExpressionField';
import SemanticRuleConfigForm from '../components/SemanticRuleConfigForm';
import SemanticLlmSettingsModal from '../components/SemanticLlmSettingsModal';
import { dimensionApi } from '../api/dimension';
import { warningApi } from '../api/warning';
import { metricApi } from '../api/metric';
import type {
  Dimension,
  MetricDefinition,
  WarningRule,
  WarningRulePreviewResult,
  WarningRuleType,
} from '../api/types';
import { formatDimensionDisplayValue, resolveCommonDimensions } from '../utils/metricDimensions';
import {
  buildFilterRequest,
  createDefaultFilterGroup,
  hasActiveFilterQuery,
  type FilterGroupForm,
  type FilterQuery,
} from '../utils/queryFilters';
import { createDefaultSemanticGroup, normalizeSemanticConfig } from '../utils/semanticRuleForm';

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
  const [dimensions, setDimensions] = useState<Dimension[]>([]);
  const [previewFilterForm] = Form.useForm<{ filterGroups: FilterGroupForm[] }>();
  const [previewActiveFilterQuery, setPreviewActiveFilterQuery] = useState<FilterQuery>({});
  const [dimensionValueOptions, setDimensionValueOptions] = useState<
    Record<string, { label: string; value: string }[]>
  >({});
  const [dimensionValueNames, setDimensionValueNames] = useState<
    Record<string, Record<string, string>>
  >({});
  const selectedMetricCodes = Form.useWatch('metricCodes', form) as string[] | undefined;
  const selectedRuleType = (Form.useWatch('ruleType', form) as WarningRuleType | undefined) || 'METRIC';
  const [llmSettingsOpen, setLlmSettingsOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [ruleList, metricList, dimensionList] = await Promise.all([
        warningApi.list(),
        metricApi.listDefinitions(),
        dimensionApi.list(),
      ]);
      setRules(ruleList);
      setMetrics(metricList);
      setDimensions(dimensionList);
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
    form.setFieldsValue({
      enabled: true,
      warningLevel: 2,
      ruleType: 'METRIC',
      ruleConfig: {
        triggerLogic: 'AND',
        semantic: { semanticGroups: [createDefaultSemanticGroup()] },
      },
    });
    setModalOpen(true);
  };

  const openEdit = (record: WarningRule) => {
    setEditing(record);
    const ruleType = (record.ruleType || 'METRIC') as WarningRuleType;
    form.resetFields();
    form.setFieldsValue({
      name: record.name,
      code: record.code,
      catalogCode: record.catalogCode,
      ruleType,
      metricCodes: record.metricCodes ?? [],
      expression: record.expression ?? '',
      warningLevel: record.warningLevel,
      enabled: record.enabled,
      comments: record.comments,
      ruleConfig: {
        triggerLogic: record.ruleConfig?.triggerLogic ?? 'AND',
        semantic: normalizeSemanticConfig(record.ruleConfig?.semantic),
      },
    });
    setModalOpen(true);
  };

  const formatRuleSummary = (record: WarningRule) => {
    const type = record.ruleType || 'METRIC';
    if (type === 'SEMANTIC') {
      const semantic = normalizeSemanticConfig(record.ruleConfig?.semantic);
      const fields =
        semantic.semanticGroups?.flatMap((g) => g.checks?.map((c) => c.fieldCode).filter(Boolean) || []) ||
        [];
      const label = fields.length ? fields.join('+') : '多字段';
      return `[语义] ${label} · 合规检测`;
    }
    if (type === 'COMPOSITE') {
      const logic = record.ruleConfig?.triggerLogic === 'OR' ? '或' : '且';
      const semantic = normalizeSemanticConfig(record.ruleConfig?.semantic);
      const fields =
        semantic.semanticGroups?.flatMap((g) => g.checks?.map((c) => c.fieldCode).filter(Boolean) || []) ||
        [];
      const label = fields.length ? fields.join('+') : '语义';
      return `[组合] ${record.expression || ''} ${logic} ${label}`;
    }
    return record.expression || '-';
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    if (editing?.id) {
      values.id = editing.id;
      values.code = editing.code;
    }
    const ruleType = values.ruleType || 'METRIC';
    const payload: WarningRule = { ...values, ruleType };
    const normalizedSemantic = normalizeSemanticConfig(values.ruleConfig?.semantic);
    if (ruleType === 'SEMANTIC') {
      payload.metricCodes = [];
      payload.expression = undefined;
      payload.ruleConfig = { semantic: normalizedSemantic };
    } else if (ruleType === 'COMPOSITE') {
      payload.ruleConfig = {
        triggerLogic: values.ruleConfig?.triggerLogic || 'AND',
        semantic: normalizedSemantic,
      };
    } else {
      payload.ruleConfig = undefined;
    }
    await warningApi.save(payload);
    message.success('预警规则已保存');
    setModalOpen(false);
    load();
    onSaveSuccess();
  };

  const metricOptions = metrics.map((m) => ({
    label: `${m.name} (${m.code})`,
    value: m.code,
  }));

  const loadDimensionValueOptions = useCallback(
    async (metricCodes: string[]) => {
      const bindings = resolveCommonDimensions(metricCodes, metrics);
      const options: Record<string, { label: string; value: string }[]> = {};
      const names: Record<string, Record<string, string>> = {};
      await Promise.all(
        bindings.map(async (binding) => {
          const dim = dimensions.find((d) => d.code === binding.dimensionCode);
          if (!dim?.id) {
            return;
          }
          const values = await dimensionApi.listValues(dim.id);
          const codeToName: Record<string, string> = {};
          values.forEach((v) => {
            codeToName[v.code] = v.name || v.code;
          });
          options[binding.fieldCode] = values.map((v) => ({
            label: v.name ? `${v.name} (${v.code})` : v.code,
            value: v.code,
          }));
          names[binding.fieldCode] = codeToName;
          names[binding.dimensionCode] = codeToName;
        }),
      );
      setDimensionValueOptions(options);
      setDimensionValueNames(names);
    },
    [dimensions, metrics],
  );

  const loadPreview = useCallback(
    async (
      rule: WarningRule,
      pageIndex: number,
      pageSize: number,
      filterQuery: FilterQuery = {},
    ) => {
      if (!rule.id) {
        return;
      }
      setPreviewLoading(true);
      try {
        const result = await warningApi.previewRule(rule.id, {
          pageIndex,
          pageSize,
          filters: filterQuery.filters,
          filterGroups: filterQuery.filterGroups,
        });
        setPreviewResult(result);
        setPreviewPage({ pageIndex, pageSize });
      } catch {
        setPreviewResult(null);
      } finally {
        setPreviewLoading(false);
      }
    },
    [],
  );

  const previewCommonDimensions = useMemo(
    () => resolveCommonDimensions(previewRule?.metricCodes || [], metrics),
    [previewRule, metrics],
  );

  const previewFilterFieldOptions = useMemo(
    () =>
      previewCommonDimensions.map((d) => ({
        label: d.fieldName ? `${d.fieldName} (${d.fieldCode})` : d.fieldCode,
        value: d.fieldCode,
      })),
    [previewCommonDimensions],
  );

  const openPreview = async (record: WarningRule) => {
    const commonDims = resolveCommonDimensions(record.metricCodes || [], metrics);
    setPreviewRule(record);
    setPreviewResult(null);
    setPreviewPage({ pageIndex: 1, pageSize: 20 });
    setPreviewActiveFilterQuery({});
    setDimensionValueOptions({});
    setDimensionValueNames({});
    previewFilterForm.resetFields();
    previewFilterForm.setFieldsValue({
      filterGroups: [createDefaultFilterGroup(commonDims[0]?.fieldCode || '')],
    });
    setPreviewModalOpen(true);
    await loadDimensionValueOptions(record.metricCodes || []);
    loadPreview(record, 1, 20, {});
  };

  const applyPreviewFilters = () => {
    if (!previewRule) {
      return;
    }
    const values = previewFilterForm.getFieldsValue();
    const filterQuery = buildFilterRequest(values.filterGroups || []);
    setPreviewActiveFilterQuery(filterQuery);
    loadPreview(previewRule, 1, previewPage.pageSize, filterQuery);
  };

  const clearPreviewFilters = () => {
    previewFilterForm.setFieldsValue({
      filterGroups: [createDefaultFilterGroup(previewFilterFieldOptions[0]?.value || '')],
    });
    setPreviewActiveFilterQuery({});
    if (previewRule) {
      loadPreview(previewRule, 1, previewPage.pageSize, {});
    }
  };

  const previewDimensionKeys = useMemo(() => {
    const rowKeys = previewResult?.rows?.length
      ? Object.keys(previewResult.rows[0])
      : Object.keys(previewResult?.headers || {});
    const metricCodes = previewRule?.metricCodes || [];
    return rowKeys.filter(
      (key) =>
        !['_triggered', '_matchReason', '_matchLayer', '_llmInvoked', '_metricTriggered', '_semanticTriggered'].includes(
          key,
        ) &&
        !key.startsWith('_semanticCheck.') &&
        !key.startsWith('_matchReason.') &&
        !key.startsWith('_matchLayer.') &&
        !key.startsWith('_llmInvoked.') &&
        !metricCodes.includes(key),
    );
  }, [previewResult, previewRule]);

  const previewColumns: ColumnsType<Record<string, unknown>> = useMemo(() => {
    const rowKeys = previewResult?.rows?.length
      ? Object.keys(previewResult.rows[0])
      : Object.keys(previewResult?.headers || {});
    const metricCodes = previewRule?.metricCodes || [];
    const dimensionKeys = previewDimensionKeys;
    const semanticDetailKeys = rowKeys.filter(
      (key) =>
        key.startsWith('_semanticCheck.') ||
        key.startsWith('_matchReason.') ||
        key.startsWith('_matchLayer.') ||
        key.startsWith('_llmInvoked.'),
    );
    const extraKeys = [
      '_metricTriggered',
      '_semanticTriggered',
      '_matchReason',
      '_matchLayer',
      '_llmInvoked',
      '_triggered',
    ];
    const orderedKeys = [
      ...dimensionKeys,
      ...metricCodes.filter((code) => rowKeys.includes(code)),
      ...semanticDetailKeys,
      ...extraKeys.filter((key) => rowKeys.includes(key)),
    ];
    const dimensionKeySet = new Set(dimensionKeys);
    return orderedKeys.map((key) => ({
      title: previewResult?.headers?.[key] || key,
      dataIndex: key,
      width: key === '_triggered' ? 100 : undefined,
      ellipsis: key !== '_triggered',
      render: (value: unknown) => {
        if (
          key === '_triggered' ||
          key === '_metricTriggered' ||
          key === '_semanticTriggered' ||
          key.startsWith('_semanticCheck.')
        ) {
          return value ? (
            <Tag color="error">是</Tag>
          ) : (
            <Tag color="default">否</Tag>
          );
        }
        if (key === '_llmInvoked' || key.startsWith('_llmInvoked.')) {
          return value ? (
            <Tag color="processing">是</Tag>
          ) : (
            <Tag color="default">否</Tag>
          );
        }
        if (dimensionKeySet.has(key)) {
          return formatDimensionDisplayValue(key, value, dimensionValueNames);
        }
        return value != null ? String(value) : '-';
      },
    }));
  }, [previewResult, previewRule, previewDimensionKeys, dimensionValueNames]);

  const columns: ColumnsType<WarningRule> = [
    { title: '名称', dataIndex: 'name', width: 160 },
    { title: '编码', dataIndex: 'code', width: 120 },
    {
      title: '类型',
      dataIndex: 'ruleType',
      width: 90,
      render: (v: WarningRuleType | string) => {
        const key = (v || 'METRIC').toUpperCase() as WarningRuleType;
        const map: Record<WarningRuleType, string> = {
          METRIC: '指标',
          SEMANTIC: '语义',
          COMPOSITE: '组合',
        };
        return <Tag>{map[key] ?? key}</Tag>;
      },
    },
    {
      title: '关联指标',
      dataIndex: 'metricCodes',
      width: 200,
      render: (codes: string[]) =>
        codes?.length
          ? codes.map((c) => (
              <Tag key={c} color="blue">
                {c}
              </Tag>
            ))
          : '-',
    },
    {
      title: '规则摘要',
      dataIndex: 'expression',
      ellipsis: true,
      render: (_, record) => formatRuleSummary(record),
    },
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
          {previewDimensionKeys.length > 0 && <span> · 已展示公共维度列</span>}
          {hasActiveFilterQuery(previewActiveFilterQuery) && <span> · 已应用维度筛选</span>}
        </Typography.Paragraph>
        {previewCommonDimensions.length > 0 && (
          <div style={{ marginBottom: 16 }}>
            <Typography.Text strong>维度筛选（可选）</Typography.Text>
            <Form form={previewFilterForm} layout="vertical" style={{ marginTop: 8 }}>
              <DimensionFilterGroupsForm
                fieldOptions={previewFilterFieldOptions}
                valueOptionsByField={dimensionValueOptions}
                onCreateGroup={() =>
                  createDefaultFilterGroup(previewFilterFieldOptions[0]?.value || '')
                }
              />
            </Form>
            <Space style={{ marginTop: 8 }}>
              <Button type="primary" onClick={applyPreviewFilters} loading={previewLoading}>
                应用筛选
              </Button>
              <Button onClick={clearPreviewFilters} disabled={previewLoading}>
                清空
              </Button>
            </Space>
          </div>
        )}
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
                loadPreview(previewRule, page, pageSize, previewActiveFilterQuery);
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
        width={640}
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
          <Form.Item name="ruleType" label="规则类型" initialValue="METRIC">
            <Radio.Group>
              <Radio value="METRIC">指标表达式</Radio>
              <Radio value="SEMANTIC">语义合规</Radio>
              <Radio value="COMPOSITE">组合规则</Radio>
            </Radio.Group>
          </Form.Item>
          {(selectedRuleType === 'METRIC' || selectedRuleType === 'COMPOSITE') && (
            <>
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
                rules={[
                  { required: true, message: '请输入预警表达式' },
                  {
                    validator: async (_, expression) => {
                      await validateWarningExpressionField(
                        expression,
                        selectedMetricCodes || [],
                      );
                    },
                  },
                ]}
              >
                <WarningExpressionField
                  metricCodes={selectedMetricCodes || []}
                  metrics={metrics}
                />
              </Form.Item>
            </>
          )}
          {selectedRuleType === 'COMPOSITE' && (
            <Form.Item name={['ruleConfig', 'triggerLogic']} label="组合逻辑" initialValue="AND">
              <Radio.Group>
                <Radio value="AND">指标条件 且 语义违规</Radio>
                <Radio value="OR">指标条件 或 语义违规</Radio>
              </Radio.Group>
            </Form.Item>
          )}
          {(selectedRuleType === 'SEMANTIC' || selectedRuleType === 'COMPOSITE') && (
            <SemanticRuleConfigForm
              configActive={modalOpen}
              llmSettingsOpen={() => setLlmSettingsOpen(true)}
            />
          )}
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
      <SemanticLlmSettingsModal
        open={llmSettingsOpen}
        onClose={() => setLlmSettingsOpen(false)}
        onSaved={() => setLlmSettingsOpen(false)}
      />
    </>
  );
}
