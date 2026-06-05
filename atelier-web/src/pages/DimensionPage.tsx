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
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '../components/PageHeader';
import { dimensionApi } from '../api/dimension';
import { datasourceApi } from '../api/datasource';
import type { DataSourceResponse, Dimension, DimensionType, DimensionValue } from '../api/types';

const DIMENSION_TYPES: { label: string; value: DimensionType }[] = [
  { label: '列表维度', value: 'LIST' },
  { label: '树形维度', value: 'TREE' },
  { label: '时间维度', value: 'TIME_DIM' },
];

export default function DimensionPage() {
  const [loading, setLoading] = useState(false);
  const [dimensions, setDimensions] = useState<Dimension[]>([]);
  const [datasources, setDatasources] = useState<DataSourceResponse[]>([]);
  const [dimModalOpen, setDimModalOpen] = useState(false);
  const [valueModalOpen, setValueModalOpen] = useState(false);
  const [editingDim, setEditingDim] = useState<Dimension | null>(null);
  const [editingValue, setEditingValue] = useState<DimensionValue | null>(null);
  const [currentDimId, setCurrentDimId] = useState('');
  const [valuesMap, setValuesMap] = useState<Record<string, DimensionValue[]>>({});
  const [dimForm] = Form.useForm<Dimension>();
  const [valueForm] = Form.useForm<DimensionValue>();

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

  const loadValues = async (dimId: string) => {
    const values = await dimensionApi.listValues(dimId);
    setValuesMap((prev) => ({ ...prev, [dimId]: values }));
  };

  const openCreateDim = () => {
    setEditingDim(null);
    dimForm.resetFields();
    dimForm.setFieldsValue({ type: 'LIST' });
    setDimModalOpen(true);
  };

  const openEditDim = (record: Dimension) => {
    setEditingDim(record);
    dimForm.setFieldsValue(record);
    setDimModalOpen(true);
  };

  const handleSaveDim = async () => {
    const values = await dimForm.validateFields();
    if (editingDim?.id) {
      values.id = editingDim.id;
    }
    await dimensionApi.save(values);
    message.success('维度已保存');
    setDimModalOpen(false);
    load();
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

  const handleSaveValue = async () => {
    const values = await valueForm.validateFields();
    if (editingValue?.id) {
      values.id = editingValue.id;
    }
    await dimensionApi.saveValue(currentDimId, values);
    message.success('维度值已保存');
    setValueModalOpen(false);
    loadValues(currentDimId);
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
    { title: '目录', dataIndex: 'catalogCode', width: 100 },
    { title: '数据源', dataIndex: 'datasourceId', width: 120 },
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

  const valueColumns = (dimId: string): ColumnsType<DimensionValue> => [
    { title: '编码', dataIndex: 'code', width: 120 },
    { title: '名称', dataIndex: 'name', width: 140 },
    { title: '父编码', dataIndex: 'parentCode', width: 100 },
    { title: '排序', dataIndex: 'sort', width: 70 },
    {
      title: '操作',
      width: 140,
      render: (_, value) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEditValue(dimId, value)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该维度值？"
            onConfirm={async () => {
              await dimensionApi.deleteValue(value.id!);
              message.success('已删除');
              loadValues(dimId);
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
      <PageHeader title="维度管理" description="维护 LIST / TREE / TIME_DIM 维度及演示数据" />
      <div className="page-toolbar">
        <div className="page-toolbar-left" />
        <Space>
          <Button icon={<ReloadOutlined />} onClick={load}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateDim}>
            新建维度
          </Button>
        </Space>
      </div>
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
          expandedRowRender: (record) => (
            <div style={{ padding: '8px 0' }}>
              <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ fontWeight: 500 }}>维度值</span>
                <Button
                  size="small"
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => openCreateValue(record.id!)}
                >
                  添加维度值
                </Button>
              </div>
              <Table
                rowKey="id"
                size="small"
                columns={valueColumns(record.id!)}
                dataSource={valuesMap[record.id!] || []}
                pagination={false}
              />
            </div>
          ),
        }}
        pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
      />

      <Modal
        title={editingDim ? '编辑维度' : '新建维度'}
        open={dimModalOpen}
        onCancel={() => setDimModalOpen(false)}
        onOk={handleSaveDim}
        width={560}
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
          <Form.Item name="catalogCode" label="目录编码">
            <Input placeholder="如 finance" />
          </Form.Item>
          <Form.Item name="datasourceId" label="数据源" rules={[{ required: true }]}>
            <Select
              options={datasources.map((d) => ({ label: `${d.name} (${d.id})`, value: d.id }))}
            />
          </Form.Item>
          <Form.Item name="metaTableId" label="关联元数据表 ID">
            <Input placeholder="可选" />
          </Form.Item>
          <Form.Item name="comments" label="备注">
            <Input.TextArea rows={2} />
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
