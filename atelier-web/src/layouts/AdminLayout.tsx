import { useState } from 'react';
import { Layout, Menu, theme, Typography } from 'antd';
import {
  DatabaseOutlined,
  TableOutlined,
  PartitionOutlined,
  LineChartOutlined,
  AlertOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const { Header, Sider, Content } = Layout;

const menuItems = [
  { key: '/datasources', icon: <DatabaseOutlined />, label: '数据源管理' },
  { key: '/metadata', icon: <TableOutlined />, label: '元数据管理' },
  { key: '/dimensions', icon: <PartitionOutlined />, label: '维度管理' },
  { key: '/metrics', icon: <LineChartOutlined />, label: '指标管理' },
  { key: '/warning-rules', icon: <AlertOutlined />, label: '预警规则' },
];

export default function AdminLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { token } = theme.useToken();

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        theme="dark"
        width={220}
        style={{
          background: 'linear-gradient(180deg, #001529 0%, #0a2540 100%)',
        }}
      >
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: collapsed ? 'center' : 'flex-start',
            gap: 10,
            padding: collapsed ? '0 8px' : '0 16px',
          }}
        >
          <img
            src="/favicon.svg"
            alt="Atelier"
            width={28}
            height={28}
            style={{ flexShrink: 0 }}
          />
          {!collapsed && (
            <Typography.Title
              level={5}
              style={{ color: '#fff', margin: 0, whiteSpace: 'nowrap' }}
            >
              Atelier 数据工场
            </Typography.Title>
          )}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ background: 'transparent', border: 'none' }}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            padding: '0 24px',
            background: token.colorBgContainer,
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
            display: 'flex',
            alignItems: 'center',
          }}
        >
          <Typography.Text type="secondary">new-atelier 管理控制台</Typography.Text>
        </Header>
        <Content style={{ margin: 24 }}>
          <div
            style={{
              padding: 24,
              minHeight: 360,
              background: token.colorBgContainer,
              borderRadius: token.borderRadiusLG,
              boxShadow: '0 1px 2px rgba(0,0,0,0.03)',
            }}
          >
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  );
}
