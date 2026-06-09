import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Checkbox,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { CalendarOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import GuidePageShell from '../components/GuidePageShell';
import PageHeader from '../components/PageHeader';
import { buildDimensionValueDemo, TUTORIAL } from '../guide/demoTutorial';
import { useTutorialDemo } from '../guide/useTutorialDemo';
import { dimensionApi } from '../api/dimension';
import { datasourceApi } from '../api/datasource';
import { metadataApi } from '../api/metadata';
import type {
  DataSourceResponse,
  Dimension,
  DimensionType,
  DimensionValue,
  DimensionValueSource,
  MetaTable,
  MetaTableField,
  TimeGranularity,
  TimeValueGenerateRequest,
} from '../api/types';
import {
  TIME_FORMAT_PRESETS,
  countTimeValues,
  previewTimeValues,
} from '../utils/timeValueFormat';

const DIMENSION_TYPES: { label: string; value: DimensionType }[] = [
  { label: '列表维度', value: 'LIST' },
  { label: '树形维度', value: 'TREE' },
  { label: '时间维度', value: 'TIME_DIM' },
];

const VALUE_SOURCES: { label: string; value: DimensionValueSource }[] = [
  { label: '手动维护', value: 'MANUAL' },
  { label: '数据库表', value: 'TABLE' },
];

const TIME_GRANULARITIES: { label: string; value: TimeGranularity }[] = [
  { label: '年', value: 'YEAR' },
  { label: '季', value: 'QUARTER' },
  { label: '月', value: 'MONTH' },
];

type TimeGenerateFormValues = {
  granularity: TimeGranularity;
  startYear: number;
  endYear: number;
  startMonth?: number;
  endMonth?: number;
  formatPreset: string;
  codeFormat: string;
  nameFormat: string;
  skipExisting: boolean;
};

type DimensionFormValues = Dimension & {
  codeFieldCode?: string;
  nameFieldCode?: string;
  parentFieldCode?: string;
};

function buildFieldsFromMapping(values: DimensionFormValues) {
  const fields = [];
  let sort = 1;
  if (values.codeFieldCode) {
    fields.push({
      fieldCode: values.codeFieldCode,
      fieldName: values.codeFieldCode,
      codeField: true,
      sort: sort++,
    });
  }
  if (values.nameFieldCode) {
    fields.push({
      fieldCode: values.nameFieldCode,
      fieldName: values.nameFieldCode,
      nameField: true,
      sort: sort++,
    });
  }
  if (values.parentFieldCode) {
    fields.push({
      fieldCode: values.parentFieldCode,
      fieldName: values.parentFieldCode,
      parentField: true,
      sort: sort++,
    });
  }
  return fields;
}

export default function DimensionPage() {
  const [loading, setLoading] = useState(false);
  const [dimensions, setDimensions] = useState<Dimension[]>([]);
  const [datasources, setDatasources] = useState<DataSourceResponse[]>([]);
  const [metaTables, setMetaTables] = useState<MetaTable[]>([]);
  const [metaFields, setMetaFields] = useState<MetaTableField[]>([]);
  const [dimModalOpen, setDimModalOpen] = useState(false);
  const [valueModalOpen, setValueModalOpen] = useState(false);
  const [timeGenModalOpen, setTimeGenModalOpen] = useState(false);
  const [editingDim, setEditingDim] = useState<Dimension | null>(null);
  const [editingValue, setEditingValue] = useState<DimensionValue | null>(null);
  const [currentDimId, setCurrentDimId] = useState('');
  const [valuesMap, setValuesMap] = useState<Record<string, DimensionValue[]>>({});
  const [dimForm] = Form.useForm<DimensionFormValues>();
  const [valueForm] = Form.useForm<DimensionValue>();

  const { tutorialChain, setTutorialChain, onSaveSuccess } = useTutorialDemo(
    'dimensions',
    async (outcome) => {
      if (outcome.type !== 'form') {
        return;
      }
      setEditingDim(null);
      dimForm.resetFields();
      dimForm.setFieldsValue(outcome.values as unknown as DimensionFormValues);
      const dsId = (outcome.values as unknown as Dimension).datasourceId;
      if (dsId) {
        await loadMetaTables(dsId);
      }
      setDimModalOpen(true);
    },
  );
  const [timeGenForm] = Form.useForm<TimeGenerateFormValues>();
  const valueSource = Form.useWatch('valueSource', dimForm);
  const dimType = Form.useWatch('type', dimForm);
  const datasourceId = Form.useWatch('datasourceId', dimForm);
  const metaTableId = Form.useWatch('metaTableId', dimForm);
  const timeGranularity = Form.useWatch('granularity', timeGenForm);
  const timeStartYear = Form.useWatch('startYear', timeGenForm);
  const timeEndYear = Form.useWatch('endYear', timeGenForm);
  const timeStartMonth = Form.useWatch('startMonth', timeGenForm);
  const timeEndMonth = Form.useWatch('endMonth', timeGenForm);
  const timeCodeFormat = Form.useWatch('codeFormat', timeGenForm);
  const timeNameFormat = Form.useWatch('nameFormat', timeGenForm);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setDimensions(await dimensionApi.list());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    datasourceApi.list().then(setDatasources);
  }, [load]);

  const loadMetaTables = async (dsId?: string) => {
    if (!dsId) {
      setMetaTables([]);
      return;
    }
    setMetaTables(await metadataApi.listTables(dsId));
  };

  const loadMetaFields = async (tableId?: string) => {
    if (!tableId) {
      setMetaFields([]);
      return;
    }
    setMetaFields(await metadataApi.listFields(tableId));
  };

  useEffect(() => {
    if (dimModalOpen && datasourceId) {
      loadMetaTables(datasourceId);
    }
  }, [dimModalOpen, datasourceId]);

  useEffect(() => {
    if (dimModalOpen && metaTableId) {
      loadMetaFields(metaTableId);
    }
  }, [dimModalOpen, metaTableId]);

  useEffect(() => {
    if (!tutorialChain || tutorialChain.kind !== 'dimension-values') {
      return;
    }
    const value = tutorialChain.values[tutorialChain.index];
    if (!value) {
      return;
    }
    setCurrentDimId(tutorialChain.dimensionId);
    setEditingValue(null);
    valueForm.resetFields();
    valueForm.setFieldsValue(buildDimensionValueDemo(value));
    setValueModalOpen(true);
  }, [tutorialChain, valueForm]);

  const loadValues = async (dimId: string) => {
    const values = await dimensionApi.listValues(dimId);
    setValuesMap((prev) => ({ ...prev, [dimId]: values }));
  };

  const openCreateDim = () => {
    setEditingDim(null);
    dimForm.resetFields();
    dimForm.setFieldsValue({ type: 'LIST', valueSource: 'MANUAL' });
    setMetaTables([]);
    setMetaFields([]);
    setDimModalOpen(true);
  };

  const openEditDim = async (record: Dimension) => {
    const full = await dimensionApi.get(record.id!);
    setEditingDim(full);
    const codeField = full.fields?.find((f) => f.codeField);
    const nameField = full.fields?.find((f) => f.nameField);
    const parentField = full.fields?.find((f) => f.parentField);
    dimForm.setFieldsValue({
      ...full,
      codeFieldCode: codeField?.fieldCode,
      nameFieldCode: nameField?.fieldCode,
      parentFieldCode: parentField?.fieldCode,
    });
    if (full.datasourceId) {
      await loadMetaTables(full.datasourceId);
    }
    if (full.metaTableId) {
      await loadMetaFields(full.metaTableId);
    }
    setDimModalOpen(true);
  };

  const handleSaveDim = async () => {
    const values = await dimForm.validateFields();
    const payload: Dimension = {
      catalogCode: values.catalogCode,
      code: values.code,
      name: values.name,
      type: values.type,
      datasourceId: values.datasourceId,
      metaTableId: values.metaTableId,
      valueSource: values.valueSource || 'MANUAL',
      comments: values.comments,
    };
    if (editingDim?.id) {
      payload.id = editingDim.id;
    }
    if (payload.valueSource === 'TABLE') {
      payload.fields = buildFieldsFromMapping(values);
    }
    const saved = await dimensionApi.save(payload);
    message.success('维度已保存');
    setDimModalOpen(false);
    load();
    if (saved.id && payload.code === TUTORIAL.deptDimCode) {
      setTutorialChain({
        kind: 'dimension-values',
        dimensionId: saved.id,
        values: [
          { code: '001', name: '销售部', sort: 1 },
          { code: '002', name: '研发部', sort: 2 },
        ],
        index: 0,
      });
      return;
    }
    onSaveSuccess();
  };

  const openCreateValue = (dimId: string) => {
    setCurrentDimId(dimId);
    setEditingValue(null);
    valueForm.resetFields();
    setValueModalOpen(true);
  };

  const openEditValue = (dimId: string, value: DimensionValue) => {
    setCurrentDimId(dimId);
    setEditingValue(value);
    valueForm.setFieldsValue(value);
    setValueModalOpen(true);
  };

  const openTimeGenerate = (dimId: string) => {
    setCurrentDimId(dimId);
    timeGenForm.resetFields();
    const defaultPreset = TIME_FORMAT_PRESETS.YEAR[1];
    timeGenForm.setFieldsValue({
      granularity: 'YEAR',
      startYear: new Date().getFullYear() - 2,
      endYear: new Date().getFullYear() + 2,
      startMonth: 1,
      endMonth: 12,
      formatPreset: defaultPreset.label,
      codeFormat: defaultPreset.codeFormat,
      nameFormat: defaultPreset.nameFormat,
      skipExisting: true,
    });
    setTimeGenModalOpen(true);
  };

  const handleGranularityChange = (granularity: TimeGranularity) => {
    const preset = TIME_FORMAT_PRESETS[granularity][0];
    timeGenForm.setFieldsValue({
      granularity,
      formatPreset: preset.label,
      codeFormat: preset.codeFormat,
      nameFormat: preset.nameFormat,
    });
  };

  const handleFormatPresetChange = (label: string) => {
    const presets = TIME_FORMAT_PRESETS[timeGranularity || 'YEAR'];
    const preset = presets.find((item) => item.label === label);
    if (preset) {
      timeGenForm.setFieldsValue({
        formatPreset: label,
        codeFormat: preset.codeFormat,
        nameFormat: preset.nameFormat,
      });
    }
  };

  const timePreview = useMemo(() => {
    if (!timeGenModalOpen || !timeGranularity || !timeCodeFormat || !timeNameFormat) {
      return [];
    }
    return previewTimeValues(
      timeGranularity,
      timeStartYear || 0,
      timeEndYear || 0,
      timeCodeFormat,
      timeNameFormat,
      timeStartMonth || 1,
      timeEndMonth || 12,
    );
  }, [
    timeGenModalOpen,
    timeGranularity,
    timeStartYear,
    timeEndYear,
    timeStartMonth,
    timeEndMonth,
    timeCodeFormat,
    timeNameFormat,
  ]);

  const timeGenerateCount = useMemo(() => {
    if (!timeGranularity || timeStartYear == null || timeEndYear == null) {
      return 0;
    }
    return countTimeValues(
      timeGranularity,
      timeStartYear,
      timeEndYear,
      timeStartMonth || 1,
      timeEndMonth || 12,
    );
  }, [timeGranularity, timeStartYear, timeEndYear, timeStartMonth, timeEndMonth]);

  const handleGenerateTimeValues = async () => {
    const values = await timeGenForm.validateFields();
    const payload: TimeValueGenerateRequest = {
      granularity: values.granularity,
      startYear: values.startYear,
      endYear: values.endYear,
      codeFormat: values.codeFormat,
      nameFormat: values.nameFormat,
      skipExisting: values.skipExisting,
    };
    if (values.granularity === 'MONTH') {
      payload.startMonth = values.startMonth;
      payload.endMonth = values.endMonth;
    }
    const result = await dimensionApi.generateTimeValues(currentDimId, payload);
    message.success(`已生成 ${result.generated} 条${result.skipped ? `，跳过 ${result.skipped} 条已存在` : ''}`);
    setTimeGenModalOpen(false);
    loadValues(currentDimId);
  };

  const handleSaveValue = async () => {
    const values = await valueForm.validateFields();
    if (editingValue?.id) {
      values.id = editingValue.id;
    }
    await dimensionApi.saveValue(currentDimId, values);
    message.success('维度值已保存');
    setValueModalOpen(false);
    loadValues(currentDimId);
    if (tutorialChain?.kind === 'dimension-values') {
      const nextIndex = tutorialChain.index + 1;
      if (nextIndex < tutorialChain.values.length) {
        setTutorialChain({
          ...tutorialChain,
          index: nextIndex,
        });
        return;
      }
      setTutorialChain(null);
    }
    onSaveSuccess();
  };

  const dimColumns: ColumnsType<Dimension> = [
    { title: '编码', dataIndex: 'code', width: 100 },
    { title: '名称', dataIndex: 'name', width: 120 },
    {
      title: '类型',
      dataIndex: 'type',
      width: 110,
      render: (v: DimensionType) => {
        const item = DIMENSION_TYPES.find((t) => t.value === v);
        return <Tag color="blue">{item?.label || v}</Tag>;
      },
    },
    {
      title: '值来源',
      dataIndex: 'valueSource',
      width: 100,
      render: (v: DimensionValueSource) =>
        v === 'TABLE' ? <Tag color="green">数据库表</Tag> : <Tag>手动</Tag>,
    },
    { title: '目录', dataIndex: 'catalogCode', width: 90 },
    { title: '数据源', dataIndex: 'datasourceId', width: 100 },
    { title: '备注', dataIndex: 'comments', ellipsis: true },
    {
      title: '操作',
      width: 140,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEditDim(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该维度？"
            onConfirm={async () => {
              await dimensionApi.delete(record.id!);
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

  const valueColumns = (dim: Dimension): ColumnsType<DimensionValue> => {
    const fromTable = dim.valueSource === 'TABLE';
    const cols: ColumnsType<DimensionValue> = [
      { title: '编码', dataIndex: 'code', width: 120 },
      { title: '名称', dataIndex: 'name', width: 140 },
      { title: '父编码', dataIndex: 'parentCode', width: 100 },
      { title: '排序', dataIndex: 'sort', width: 70 },
    ];
    if (!fromTable) {
      cols.push({
        title: '操作',
        width: 140,
        render: (_, value) => (
          <Space>
            <Button type="link" size="small" onClick={() => openEditValue(dim.id!, value)}>
              编辑
            </Button>
            <Popconfirm
              title="确认删除该维度值？"
              onConfirm={async () => {
                await dimensionApi.deleteValue(value.id!);
                message.success('已删除');
                loadValues(dim.id!);
              }}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          </Space>
        ),
      });
    }
    return cols;
  };

  const fieldOptions = metaFields.map((f) => ({
    label: `${f.fieldName || f.fieldCode} (${f.fieldCode})`,
    value: f.fieldCode,
  }));

  return (
    <>
      <PageHeader
        title="维度管理"
        description="维护 LIST / TREE / TIME_DIM 维度；支持手动维护或从数据库表读取维度值"
      />
      <div className="page-toolbar">
        <div className="page-toolbar-left" />
        <Space>
          <Button icon={<ReloadOutlined />} onClick={load}>
            刷新
          </Button>
          <Button id="guide-primary-action" type="primary" icon={<PlusOutlined />} onClick={openCreateDim}>
            新建维度
          </Button>
        </Space>
      </div>
      <GuidePageShell>
        <Table
          rowKey="id"
          loading={loading}
          columns={dimColumns}
          dataSource={dimensions}
          expandable={{
          onExpand: (expanded, record) => {
            if (expanded && record.id) {
              loadValues(record.id);
            }
          },
          expandedRowRender: (record) => {
            const fromTable = record.valueSource === 'TABLE';
            const isTimeDim = record.type === 'TIME_DIM';
            return (
              <div style={{ padding: '8px 0' }}>
                <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'space-between' }}>
                  <Space>
                    <span style={{ fontWeight: 500 }}>维度值</span>
                    {fromTable && (
                      <Tag color="green">数据来自物理表，只读</Tag>
                    )}
                  </Space>
                  <Space>
                    <Button size="small" icon={<ReloadOutlined />} onClick={() => loadValues(record.id!)}>
                      刷新
                    </Button>
                    {!fromTable && isTimeDim && (
                      <Button
                        size="small"
                        icon={<CalendarOutlined />}
                        onClick={() => openTimeGenerate(record.id!)}
                      >
                        批量生成
                      </Button>
                    )}
                    {!fromTable && (
                      <Button
                        size="small"
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={() => openCreateValue(record.id!)}
                      >
                        添加维度值
                      </Button>
                    )}
                  </Space>
                </div>
                <Table
                  rowKey={(row) => row.id || row.code}
                  size="small"
                  columns={valueColumns(record)}
                  dataSource={valuesMap[record.id!] || []}
                  pagination={false}
                />
              </div>
            );
          },
        }}
        pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
        />
      </GuidePageShell>

      <Modal
        title={editingDim ? '编辑维度' : '新建维度'}
        open={dimModalOpen}
        onCancel={() => setDimModalOpen(false)}
        onOk={handleSaveDim}
        width={600}
      >
        <Form form={dimForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="code" label="编码" rules={[{ required: true }]}>
            <Input placeholder="如 dept" disabled={!!editingDim} />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select options={DIMENSION_TYPES} />
          </Form.Item>
          <Form.Item name="valueSource" label="维度值来源" rules={[{ required: true }]}>
            <Select options={VALUE_SOURCES} />
          </Form.Item>
          <Form.Item name="catalogCode" label="目录编码">
            <Input placeholder="如 finance" />
          </Form.Item>
          <Form.Item name="datasourceId" label="数据源" rules={[{ required: true }]}>
            <Select
              options={datasources.map((d) => ({ label: `${d.name} (${d.id})`, value: d.id }))}
              onChange={() => {
                dimForm.setFieldsValue({
                  metaTableId: undefined,
                  codeFieldCode: undefined,
                  nameFieldCode: undefined,
                  parentFieldCode: undefined,
                });
                setMetaFields([]);
              }}
            />
          </Form.Item>
          {valueSource === 'TABLE' && (
            <>
              <Alert
                type="info"
                showIcon
                style={{ marginBottom: 16 }}
                message="选择元数据表并映射编码/名称列，维度值将从物理表 DISTINCT 查询获得"
              />
              <Form.Item
                name="metaTableId"
                label="关联元数据表"
                rules={[{ required: true, message: '请选择元数据表' }]}
              >
                <Select
                  showSearch
                  placeholder="选择元数据表"
                  optionFilterProp="label"
                  options={metaTables.map((t) => ({
                    label: `${t.tableName || t.tableCode} (${t.tableCode})`,
                    value: t.id,
                  }))}
                  onChange={() => {
                    dimForm.setFieldsValue({
                      codeFieldCode: undefined,
                      nameFieldCode: undefined,
                      parentFieldCode: undefined,
                    });
                  }}
                />
              </Form.Item>
              <Form.Item
                name="codeFieldCode"
                label="编码列"
                rules={[{ required: true, message: '请选择编码列' }]}
              >
                <Select placeholder="物理表中的编码字段" options={fieldOptions} />
              </Form.Item>
              <Form.Item
                name="nameFieldCode"
                label="名称列"
                rules={[{ required: true, message: '请选择名称列' }]}
              >
                <Select placeholder="物理表中的名称字段" options={fieldOptions} />
              </Form.Item>
              {dimType === 'TREE' && (
                <Form.Item name="parentFieldCode" label="父编码列">
                  <Select allowClear placeholder="树形维度可选" options={fieldOptions} />
                </Form.Item>
              )}
            </>
          )}
          <Form.Item name="comments" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="批量生成时间维度值"
        open={timeGenModalOpen}
        onCancel={() => setTimeGenModalOpen(false)}
        onOk={handleGenerateTimeValues}
        width={560}
        okText="生成"
      >
        <Form form={timeGenForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="granularity" label="时间粒度" rules={[{ required: true }]}>
            <Select options={TIME_GRANULARITIES} onChange={handleGranularityChange} />
          </Form.Item>
          <Space align="start" style={{ display: 'flex' }}>
            <Form.Item name="startYear" label="起始年份" rules={[{ required: true }]} style={{ flex: 1 }}>
              <InputNumber min={1900} max={2100} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="endYear" label="结束年份" rules={[{ required: true }]} style={{ flex: 1 }}>
              <InputNumber min={1900} max={2100} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          {timeGranularity === 'MONTH' && (
            <Space align="start" style={{ display: 'flex' }}>
              <Form.Item name="startMonth" label="起始月份" rules={[{ required: true }]} style={{ flex: 1 }}>
                <InputNumber min={1} max={12} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="endMonth" label="结束月份" rules={[{ required: true }]} style={{ flex: 1 }}>
                <InputNumber min={1} max={12} style={{ width: '100%' }} />
              </Form.Item>
            </Space>
          )}
          <Form.Item name="formatPreset" label="格式预设">
            <Select
              options={(TIME_FORMAT_PRESETS[timeGranularity || 'YEAR'] || []).map((item) => ({
                label: item.label,
                value: item.label,
              }))}
              onChange={handleFormatPresetChange}
            />
          </Form.Item>
          <Space align="start" style={{ display: 'flex' }}>
            <Form.Item
              name="codeFormat"
              label="编码格式"
              rules={[{ required: true }]}
              style={{ flex: 1 }}
              extra="YYYY/YY/MM/M/QN/Q"
            >
              <Input placeholder="如 YYYY 或 FYYYYY" />
            </Form.Item>
            <Form.Item
              name="nameFormat"
              label="名称格式"
              rules={[{ required: true }]}
              style={{ flex: 1 }}
              extra="可与编码不同"
            >
              <Input placeholder="如 YYYY年" />
            </Form.Item>
          </Space>
          <Alert
            type="info"
            showIcon
            message={`将生成约 ${timeGenerateCount} 条维度值`}
            description={
              timePreview.length > 0 ? (
                <div>
                  <div>预览：</div>
                  {timePreview.map((item) => (
                    <Typography.Text key={item.code} code style={{ display: 'block' }}>
                      {item.code} / {item.name}
                    </Typography.Text>
                  ))}
                  {timeGenerateCount > timePreview.length && (
                    <Typography.Text type="secondary">… 等共 {timeGenerateCount} 条</Typography.Text>
                  )}
                </div>
              ) : (
                '请填写时间范围与格式'
              )
            }
          />
          <Form.Item name="skipExisting" valuePropName="checked" style={{ marginTop: 16, marginBottom: 0 }}>
            <Checkbox>跳过已存在相同编码的维度值</Checkbox>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editingValue ? '编辑维度值' : '新建维度值'}
        open={valueModalOpen}
        onCancel={() => setValueModalOpen(false)}
        onOk={handleSaveValue}
        width={440}
      >
        <Form form={valueForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="code" label="编码" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="parentCode" label="父编码">
            <Input placeholder="树形维度使用" />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <Input type="number" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
