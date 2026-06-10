import { useEffect, useMemo, useState } from 'react';
import { Button, Form, Input, Modal, Space, Switch, Table, Typography, message } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { datasourceApi } from '../api/datasource';
import type { DbCreateTableColumn, SqlExecuteResult } from '../api/types';
import SqlPreviewBlock from './SqlPreviewBlock';

interface CreateTableModalProps {
  datasourceId: string;
  schema?: string;
  open: boolean;
  onClose: () => void;
  onSuccess?: (result: SqlExecuteResult) => void;
}

type ColumnRow = DbCreateTableColumn & { key: string };

function buildPreviewDdl(schema: string | undefined, tableName: string, columns: ColumnRow[]) {
  const validColumns = columns.filter((item) => item.name?.trim() && item.type?.trim());
  if (!tableName.trim() || validColumns.length === 0) {
    return '';
  }
  const qualified = schema?.trim() ? `${schema.trim()}.${tableName.trim()}` : tableName.trim();
  const defs = validColumns.map((column) => {
    let def = `${column.name} ${column.type.trim().toUpperCase()}`;
    if (column.primaryKey) {
      def += ' PRIMARY KEY';
    } else if (column.nullable === false) {
      def += ' NOT NULL';
    }
    return `  ${def}`;
  });
  return `CREATE TABLE IF NOT EXISTS ${qualified} (\n${defs.join(',\n')}\n)`;
}

export default function CreateTableModal({
  datasourceId,
  schema,
  open,
  onClose,
  onSuccess,
}: CreateTableModalProps) {
  const [form] = Form.useForm<{ tableName: string; ifNotExists: boolean }>();
  const [columns, setColumns] = useState<ColumnRow[]>([
    { key: '1', name: 'id', type: 'VARCHAR(32)', nullable: false, primaryKey: true },
    { key: '2', name: '', type: 'VARCHAR(255)', nullable: true, primaryKey: false },
  ]);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    form.setFieldsValue({ tableName: '', ifNotExists: true });
    setColumns([
      { key: '1', name: 'id', type: 'VARCHAR(32)', nullable: false, primaryKey: true },
      { key: '2', name: '', type: 'VARCHAR(255)', nullable: true, primaryKey: false },
    ]);
  }, [open, form]);

  const tableName = Form.useWatch('tableName', form) || '';
  const previewDdl = useMemo(
    () => buildPreviewDdl(schema, tableName, columns),
    [schema, tableName, columns],
  );

  const columnTableColumns: ColumnsType<ColumnRow> = [
    {
      title: '字段名',
      dataIndex: 'name',
      width: 140,
      render: (_, record, index) => (
        <Input
          value={record.name}
          placeholder="field_name"
          onChange={(event) => {
            const next = [...columns];
            next[index] = { ...next[index], name: event.target.value };
            setColumns(next);
          }}
        />
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 160,
      render: (_, record, index) => (
        <Input
          value={record.type}
          placeholder="VARCHAR(50)"
          onChange={(event) => {
            const next = [...columns];
            next[index] = { ...next[index], type: event.target.value };
            setColumns(next);
          }}
        />
      ),
    },
    {
      title: '主键',
      dataIndex: 'primaryKey',
      width: 70,
      render: (_, record, index) => (
        <Switch
          size="small"
          checked={record.primaryKey}
          onChange={(checked) => {
            const next = columns.map((item, itemIndex) => ({
              ...item,
              primaryKey: itemIndex === index ? checked : checked ? false : item.primaryKey,
            }));
            setColumns(next);
          }}
        />
      ),
    },
    {
      title: '可空',
      dataIndex: 'nullable',
      width: 70,
      render: (_, record, index) => (
        <Switch
          size="small"
          checked={record.nullable !== false}
          disabled={record.primaryKey}
          onChange={(checked) => {
            const next = [...columns];
            next[index] = { ...next[index], nullable: checked };
            setColumns(next);
          }}
        />
      ),
    },
    {
      title: '',
      width: 48,
      render: (_, __, index) => (
        <Button
          type="text"
          danger
          icon={<MinusCircleOutlined />}
          disabled={columns.length <= 1}
          onClick={() => setColumns(columns.filter((_, itemIndex) => itemIndex !== index))}
        />
      ),
    },
  ];

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const validColumns = columns
      .map(({ key: _key, ...column }) => column)
      .filter((column) => column.name?.trim() && column.type?.trim());
    if (validColumns.length === 0) {
      message.warning('请至少填写一个字段');
      return;
    }
    setSubmitting(true);
    try {
      const result = await datasourceApi.browseCreateTable(datasourceId, {
        schema,
        tableName: values.tableName.trim(),
        ifNotExists: values.ifNotExists,
        columns: validColumns.map((column) => ({
          name: column.name!.trim(),
          type: column.type!.trim(),
          nullable: column.nullable,
          primaryKey: column.primaryKey,
        })),
      });
      message.success(result.message || '建表成功');
      onSuccess?.(result);
      onClose();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '建表失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title="新建物理表"
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      confirmLoading={submitting}
      width={760}
      destroyOnHidden
    >
      <Typography.Paragraph type="secondary">
        Schema: {schema || '（默认）'} · 将在目标数据源中执行 CREATE TABLE
      </Typography.Paragraph>
      <Form form={form} layout="vertical">
        <Space align="start" style={{ width: '100%' }} size="large">
          <Form.Item
            label="表名"
            name="tableName"
            rules={[
              { required: true, message: '请输入表名' },
              { pattern: /^[A-Za-z0-9_]+$/, message: '仅允许字母、数字与下划线' },
            ]}
            style={{ flex: 1, minWidth: 220 }}
          >
            <Input placeholder="demo_table" />
          </Form.Item>
          <Form.Item label="IF NOT EXISTS" name="ifNotExists" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Space>
      </Form>
      <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography.Text strong>字段定义</Typography.Text>
        <Button
          type="dashed"
          size="small"
          icon={<PlusOutlined />}
          onClick={() =>
            setColumns([
              ...columns,
              {
                key: String(Date.now()),
                name: '',
                type: 'VARCHAR(255)',
                nullable: true,
                primaryKey: false,
              },
            ])
          }
        >
          添加字段
        </Button>
      </div>
      <Table
        rowKey="key"
        size="small"
        columns={columnTableColumns}
        dataSource={columns}
        pagination={false}
        scroll={{ y: 220 }}
      />
      {previewDdl && (
        <div style={{ marginTop: 12 }}>
          <Typography.Text type="secondary">DDL 预览</Typography.Text>
          <SqlPreviewBlock sql={previewDdl} maxHeight={160} />
        </div>
      )}
    </Modal>
  );
}
