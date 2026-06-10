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
import { PlusOutlined, ReloadOutlined, ApiOutlined, DatabaseOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import GuidePageShell from '../components/GuidePageShell';
import PageHeader from '../components/PageHeader';
import { useTutorialDemo } from '../guide/useTutorialDemo';
import DatabaseBrowseModal from '../components/DatabaseBrowseModal';
import { datasourceApi } from '../api/datasource';
import type { DataSourceRequest, DataSourceResponse } from '../api/types';
import { getJdbcTemplate, isJdbcTemplate } from '../constants/jdbcTemplates';

const DB_TYPES = ['H2', 'MYSQL', 'ORACLE', 'POSTGRESQL', 'DM', 'KINGBASE', 'DB2', 'STARROCKS'];

type ConnectionPropertyRow = { key?: string; value?: string };

type DataSourceFormValues = DataSourceRequest & {
  connectionPropertyRows?: ConnectionPropertyRow[];
};

function rowsFromProps(props?: Record<string, string>): ConnectionPropertyRow[] {
  if (!props) {
    return [];
  }
  return Object.entries(props).map(([key, value]) => ({ key, value }));
}

function propsFromRows(rows?: ConnectionPropertyRow[]): Record<string, string> | undefined {
  if (!rows?.length) {
    return undefined;
  }
  const result: Record<string, string> = {};
  rows.forEach((row) => {
    const key = row.key?.trim();
    if (!key) {
      return;
    }
    result[key] = row.value?.trim() ?? '';
  });
  return Object.keys(result).length > 0 ? result : undefined;
}

function toRequestPayload(values: DataSourceFormValues): DataSourceRequest {
  const { connectionPropertyRows, ...rest } = values;
  return {
    ...rest,
    password: values.password ?? '',
    connectionProperties: propsFromRows(connectionPropertyRows),
  };
}

export default function DataSourcePage() {
  const [loading, setLoading] = useState(false);
  const [testing, setTesting] = useState(false);
  const [data, setData] = useState<DataSourceResponse[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [browseOpen, setBrowseOpen] = useState(false);
  const [browseTarget, setBrowseTarget] = useState<DataSourceResponse | null>(null);
  const [editing, setEditing] = useState<DataSourceResponse | null>(null);
  const [initialEditDbType, setInitialEditDbType] = useState<string | null>(null);
  const [form] = Form.useForm<DataSourceFormValues>();

  const { onSaveSuccess } = useTutorialDemo('datasources', async (outcome) => {
    if (outcome.type !== 'form') {
      return;
    }
    setEditing(null);
    setInitialEditDbType(null);
    form.resetFields();
    form.setFieldsValue(outcome.values as unknown as DataSourceRequest);
    setModalOpen(true);
  });

  const dbType = Form.useWatch('dbType', form);
  const jdbcUrlPlaceholder = getJdbcTemplate(dbType);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await datasourceApi.list());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleDbTypeChange = (newDbType: string) => {
    const currentUrl = form.getFieldValue('jdbcUrl') as string | undefined;
    const previousTemplate = initialEditDbType
      ? getJdbcTemplate(initialEditDbType)
      : undefined;

    const shouldUpdate =
      isJdbcTemplate(currentUrl) ||
      (editing && previousTemplate !== undefined && currentUrl === previousTemplate);

    if (shouldUpdate) {
      form.setFieldsValue({ jdbcUrl: getJdbcTemplate(newDbType) });
    }
  };

  const openCreate = () => {
    setEditing(null);
    setInitialEditDbType(null);
    form.resetFields();
    const defaultDbType = 'H2';
    form.setFieldsValue({
      enabled: true,
      dbType: defaultDbType,
      jdbcUrl: getJdbcTemplate(defaultDbType),
      connectionPropertyRows: [],
    });
    setModalOpen(true);
  };

  const openEdit = (record: DataSourceResponse) => {
    setEditing(record);
    setInitialEditDbType(record.dbType);
    form.setFieldsValue({
      ...record,
      password: '',
      connectionPropertyRows: rowsFromProps(record.connectionProperties),
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    await datasourceApi.save(toRequestPayload(values));
    message.success(editing ? '数据源已更新' : '数据源已创建');
    setModalOpen(false);
    load();
    onSaveSuccess();
  };

  const handleTest = async () => {
    const values = await form.validateFields();
    setTesting(true);
    try {
      const result = await datasourceApi.test(toRequestPayload(values));
      if (result.success) {
        message.success(result.message);
      } else {
        message.error(result.message);
      }
    } finally {
      setTesting(false);
    }
  };

  const openBrowse = (record: DataSourceResponse) => {
    if (!record.enabled) {
      message.warning('请先启用数据源后再浏览');
      return;
    }
    setBrowseTarget(record);
    setBrowseOpen(true);
  };

  const columns: ColumnsType<DataSourceResponse> = [
    { title: 'ID', dataIndex: 'id', width: 120 },
    { title: '名称', dataIndex: 'name', width: 140 },
    {
      title: 'JDBC URL',
      dataIndex: 'jdbcUrl',
      ellipsis: true,
    },
    { title: '用户名', dataIndex: 'username', width: 100 },
    {
      title: '数据库类型',
      dataIndex: 'dbType',
      width: 110,
      render: (v: string) => <Tag>{v}</Tag>,
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
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<DatabaseOutlined />}
            disabled={!record.enabled}
            onClick={() => openBrowse(record)}
          >
            浏览
          </Button>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该数据源？"
            onConfirm={async () => {
              await datasourceApi.delete(record.id);
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
        title="数据源管理"
        description="配置 JDBC 连接，支持连接测试与热加载"
      />
      <div className="page-toolbar">
        <div className="page-toolbar-left" />
        <Space>
          <Button icon={<ReloadOutlined />} onClick={load}>
            刷新
          </Button>
          <Button id="guide-primary-action" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建数据源
          </Button>
        </Space>
      </div>
      <GuidePageShell>
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={data}
          scroll={{ x: 900 }}
          pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
        />
      </GuidePageShell>
      <Modal
        title={editing ? '编辑数据源' : '新建数据源'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSave}
        width={720}
        footer={[
          <Button key="test" icon={<ApiOutlined />} loading={testing} onClick={handleTest}>
            测试连接
          </Button>,
          <Button key="cancel" onClick={() => setModalOpen(false)}>
            取消
          </Button>,
          <Button key="ok" type="primary" onClick={handleSave}>
            保存
          </Button>,
        ]}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="id" label="ID" rules={[{ required: true, message: '请输入 ID' }]}>
            <Input disabled={!!editing} placeholder="如 ds-demo" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input placeholder="数据源显示名称" />
          </Form.Item>
          <Form.Item name="jdbcUrl" label="JDBC URL" rules={[{ required: true }]}>
            <Input placeholder={jdbcUrlPlaceholder} />
          </Form.Item>
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label="密码" extra={editing ? '留空则保持原密码' : undefined}>
            <Input.Password placeholder={editing ? '留空保持不变' : ''} />
          </Form.Item>
          <Form.Item name="dbType" label="数据库类型" rules={[{ required: true }]}>
            <Select
              placeholder={jdbcUrlPlaceholder}
              onChange={handleDbTypeChange}
              options={DB_TYPES.map((t) => ({
                label: t,
                value: t,
                title: getJdbcTemplate(t),
              }))}
            />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Typography.Text strong>连接属性（可选）</Typography.Text>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 8, fontSize: 13 }}>
            追加 JDBC 参数，如 MySQL 的 useSSL、serverTimezone、characterEncoding 等
          </Typography.Paragraph>
          <Form.List name="connectionPropertyRows">
            {(rows, { add, remove }) => (
              <>
                {rows.map((row) => (
                  <Space key={row.key} align="baseline" style={{ display: 'flex', marginBottom: 8 }}>
                    <Form.Item
                      {...row}
                      name={[row.name, 'key']}
                      rules={[{ required: true, message: '请输入属性名' }]}
                    >
                      <Input placeholder="useSSL" style={{ width: 160 }} />
                    </Form.Item>
                    <Form.Item
                      {...row}
                      name={[row.name, 'value']}
                      rules={[{ required: true, message: '请输入属性值' }]}
                    >
                      <Input placeholder="true" style={{ width: 220 }} />
                    </Form.Item>
                    <Button type="link" danger onClick={() => remove(row.name)}>
                      删除
                    </Button>
                  </Space>
                ))}
                <Button
                  type="dashed"
                  onClick={() => add({ key: dbType === 'MYSQL' ? 'useSSL' : '', value: 'true' })}
                  block
                >
                  添加属性
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>
      <DatabaseBrowseModal
        datasource={browseTarget}
        open={browseOpen}
        onClose={() => setBrowseOpen(false)}
      />
    </>
  );
}
