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
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '../components/PageHeader';
import { metadataApi } from '../api/metadata';
import { datasourceApi } from '../api/datasource';
import type { DataSourceResponse, MetaTable, MetaTableField } from '../api/types';

export default function MetadataPage() {
  const [loading, setLoading] = useState(false);
  const [tables, setTables] = useState<MetaTable[]>([]);
  const [datasources, setDatasources] = useState<DataSourceResponse[]>([]);
  const [filterDs, setFilterDs] = useState<string | undefined>();
  const [tableModalOpen, setTableModalOpen] = useState(false);
  const [fieldModalOpen, setFieldModalOpen] = useState(false);
  const [editingTable, setEditingTable] = useState<MetaTable | null>(null);
  const [editingField, setEditingField] = useState<MetaTableField | null>(null);
  const [currentTableId, setCurrentTableId] = useState<string>('');
  const [fieldsMap, setFieldsMap] = useState<Record<string, MetaTableField[]>>({});
  const [tableForm] = Form.useForm<MetaTable>();
  const [fieldForm] = Form.useForm<MetaTableField>();

  const loadTables = useCallback(async () => {
    setLoading(true);
    try {
      setTables(await metadataApi.listTables(filterDs));
    } finally {
      setLoading(false);
    }
  }, [filterDs]);

  const loadDatasources = useCallback(async () => {
    setDatasources(await datasourceApi.list());
  }, []);

  useEffect(() => {
    loadDatasources();
  }, [loadDatasources]);

  useEffect(() => {
    loadTables();
  }, [loadTables]);

  const loadFields = async (tableId: string) => {
    const fields = await metadataApi.listFields(tableId);
    setFieldsMap((prev) => ({ ...prev, [tableId]: fields }));
  };

  const openCreateTable = () => {
    setEditingTable(null);
    tableForm.resetFields();
    tableForm.setFieldsValue({ datasourceId: filterDs });
    setTableModalOpen(true);
  };

  const openEditTable = (record: MetaTable) => {
    setEditingTable(record);
    tableForm.setFieldsValue(record);
    setTableModalOpen(true);
  };

  const handleSaveTable = async () => {
    const values = await tableForm.validateFields();
    if (editingTable?.id) {
      values.id = editingTable.id;
    }
    await metadataApi.saveTable(values);
    message.success('元数据表已保存');
    setTableModalOpen(false);
    loadTables();
  };

  const openCreateField = (tableId: string) => {
    setCurrentTableId(tableId);
    setEditingField(null);
    fieldForm.resetFields();
    setFieldModalOpen(true);
  };

  const openEditField = (tableId: string, field: MetaTableField) => {
    setCurrentTableId(tableId);
    setEditingField(field);
    fieldForm.setFieldsValue(field);
    setFieldModalOpen(true);
  };

  const handleSaveField = async () => {
    const values = await fieldForm.validateFields();
    if (editingField?.id) {
      values.id = editingField.id;
    }
    await metadataApi.saveField(currentTableId, values);
    message.success('字段已保存');
    setFieldModalOpen(false);
    loadFields(currentTableId);
  };

  const tableColumns: ColumnsType<MetaTable> = [
    { title: '表编码', dataIndex: 'tableCode', width: 120 },
    { title: '表名称', dataIndex: 'tableName', width: 140 },
    { title: '目录', dataIndex: 'catalogCode', width: 100 },
    { title: '数据源', dataIndex: 'datasourceId', width: 120 },
    { title: '备注', dataIndex: 'comments', ellipsis: true },
    {
      title: '操作',
      width: 140,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEditTable(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该表及所有字段？"
            onConfirm={async () => {
              await metadataApi.deleteTable(record.id!);
              message.success('已删除');
              loadTables();
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

  const fieldColumns = (tableId: string): ColumnsType<MetaTableField> => [
    { title: '字段编码', dataIndex: 'fieldCode', width: 140 },
    { title: '字段名称', dataIndex: 'fieldName', width: 140 },
    { title: '类型', dataIndex: 'fieldType', width: 100 },
    { title: '排序', dataIndex: 'sort', width: 70 },
    {
      title: '操作',
      width: 140,
      render: (_, field) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEditField(tableId, field)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该字段？"
            onConfirm={async () => {
              await metadataApi.deleteField(field.id!);
              message.success('已删除');
              loadFields(tableId);
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
        title="元数据管理"
        description="维护元数据表与字段定义，可按数据源筛选"
      />
      <div className="page-toolbar">
        <div className="page-toolbar-left">
          <Select
            allowClear
            placeholder="按数据源筛选"
            style={{ width: 220 }}
            value={filterDs}
            onChange={setFilterDs}
            options={datasources.map((d) => ({ label: d.name, value: d.id }))}
          />
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadTables}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateTable}>
            新建元数据表
          </Button>
        </Space>
      </div>
      <Table
        rowKey="id"
        loading={loading}
        columns={tableColumns}
        dataSource={tables}
        expandable={{
          onExpand: (expanded, record) => {
            if (expanded && record.id) {
              loadFields(record.id);
            }
          },
          expandedRowRender: (record) => (
            <div style={{ padding: '8px 0' }}>
              <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ fontWeight: 500 }}>字段列表</span>
                <Button
                  size="small"
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => openCreateField(record.id!)}
                >
                  添加字段
                </Button>
              </div>
              <Table
                rowKey="id"
                size="small"
                columns={fieldColumns(record.id!)}
                dataSource={fieldsMap[record.id!] || []}
                pagination={false}
              />
            </div>
          ),
        }}
        pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
      />

      <Modal
        title={editingTable ? '编辑元数据表' : '新建元数据表'}
        open={tableModalOpen}
        onCancel={() => setTableModalOpen(false)}
        onOk={handleSaveTable}
        width={560}
      >
        <Form form={tableForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="tableCode" label="表编码" rules={[{ required: true }]}>
            <Input placeholder="如 orders" />
          </Form.Item>
          <Form.Item name="tableName" label="表名称" rules={[{ required: true }]}>
            <Input placeholder="如 订单事实表" />
          </Form.Item>
          <Form.Item name="catalogCode" label="目录编码">
            <Input placeholder="如 finance" />
          </Form.Item>
          <Form.Item name="datasourceId" label="数据源" rules={[{ required: true }]}>
            <Select
              options={datasources.map((d) => ({ label: `${d.name} (${d.id})`, value: d.id }))}
            />
          </Form.Item>
          <Form.Item name="comments" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editingField ? '编辑字段' : '新建字段'}
        open={fieldModalOpen}
        onCancel={() => setFieldModalOpen(false)}
        onOk={handleSaveField}
        width={480}
      >
        <Form form={fieldForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="fieldCode" label="字段编码" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="fieldName" label="字段名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="fieldType" label="字段类型" rules={[{ required: true }]}>
            <Select
              options={['VARCHAR', 'INTEGER', 'DECIMAL', 'DATE', 'TIMESTAMP', 'BOOLEAN'].map(
                (t) => ({ label: t, value: t }),
              )}
            />
          </Form.Item>
          <Form.Item name="sort" label="排序">
            <Input type="number" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
