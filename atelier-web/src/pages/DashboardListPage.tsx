import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  message,
} from 'antd';
import {
  EditOutlined,
  FullscreenOutlined,
  PlusOutlined,
  ReloadOutlined,
  RobotOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate } from 'react-router-dom';
import GuidePageShell from '../components/GuidePageShell';
import PageHeader from '../components/PageHeader';
import { dashboardApi } from '../api/dashboard';
import type { DashboardScreen } from '../api/types';
import DashboardGenerateModal from '../components/dashboard/DashboardGenerateModal';

export default function DashboardListPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [list, setList] = useState<DashboardScreen[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [generateOpen, setGenerateOpen] = useState(false);
  const [form] = Form.useForm<DashboardScreen>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setList(await dashboardApi.list());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    form.resetFields();
    form.setFieldsValue({
      code: `screen-${Date.now().toString(36)}`,
      name: '未命名大屏',
      enabled: true,
      layout: { width: 1920, height: 1080, backgroundColor: '#0a1628', gridCols: 24, rowHeight: 30 },
      widgets: [],
    });
    setModalOpen(true);
  };

  const handleCreate = async () => {
    const values = await form.validateFields();
    try {
      const saved = await dashboardApi.save(values);
      message.success('大屏已创建');
      setModalOpen(false);
      await load();
      navigate(`/dashboards/${saved.id}/edit`);
    } catch {
      /* handled by interceptor */
    }
  };

  const columns: ColumnsType<DashboardScreen> = [
    { title: '编码', dataIndex: 'code', width: 180 },
    { title: '名称', dataIndex: 'name' },
    {
      title: '画布',
      key: 'layout',
      width: 140,
      render: (_, row) =>
        `${row.layout?.width ?? 1920} × ${row.layout?.height ?? 1080}`,
    },
    {
      title: '组件数',
      key: 'widgets',
      width: 90,
      render: (_, row) => row.widgets?.length ?? 0,
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      render: (v: boolean | undefined) => (v === false ? '否' : '是'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      render: (_, row) => (
        <Space>
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => navigate(`/dashboards/${row.id}/edit`)}
          >
            设计
          </Button>
          <Button
            size="small"
            icon={<FullscreenOutlined />}
            onClick={() => window.open(`/screen/${row.code}`, '_blank')}
          >
            演示
          </Button>
          <Popconfirm
            title="确定删除该大屏？"
            onConfirm={async () => {
              await dashboardApi.delete(row.code);
              message.success('已删除');
              void load();
            }}
          >
            <Button size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <GuidePageShell>
      <div className="dashboard-page">
        <PageHeader
          title="可视化大屏"
          description="自由拖拽布局，绑定指标与预警规则，构建数据驾驶舱"
        />
        <Space style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建大屏
          </Button>
          <Button icon={<RobotOutlined />} onClick={() => setGenerateOpen(true)}>
            AI 生成
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
        </Space>
        <Table
          rowKey={(row) => row.id ?? row.code}
          loading={loading}
          columns={columns}
          dataSource={list}
          pagination={{ pageSize: 10 }}
        />

        <Modal
          title="新建大屏"
          open={modalOpen}
          onCancel={() => setModalOpen(false)}
          onOk={() => void handleCreate()}
          okText="创建并设计"
        >
          <Form form={form} layout="vertical">
            <Form.Item
              name="code"
              label="编码"
              rules={[{ required: true, message: '请输入编码' }]}
            >
              <Input placeholder="唯一编码，如 sales-overview" />
            </Form.Item>
            <Form.Item
              name="name"
              label="名称"
              rules={[{ required: true, message: '请输入名称' }]}
            >
              <Input />
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input.TextArea rows={2} />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Form>
        </Modal>

        <DashboardGenerateModal
          open={generateOpen}
          onClose={() => setGenerateOpen(false)}
          onGenerated={async (dashboardId) => {
            setGenerateOpen(false);
            await load();
            navigate(`/dashboards/${dashboardId}/edit`);
          }}
        />
      </div>
    </GuidePageShell>
  );
}
