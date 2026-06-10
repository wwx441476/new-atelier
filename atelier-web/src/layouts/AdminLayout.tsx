import { useState, type ReactNode } from 'react';
import { Button, Layout, Menu, Space, theme, Typography } from 'antd';
import { SettingOutlined } from '@ant-design/icons';
import OnboardingGuide, { OnboardingHeaderActions } from '../components/OnboardingGuide';
import ConfigBundleActions from '../components/ConfigBundleActions';
import CopilotDrawer from '../components/copilot/CopilotDrawer';
import { CopilotProvider } from '../components/copilot/CopilotContext';
import CopilotHeaderButton from '../components/copilot/CopilotHeaderButton';
import { useCopilot } from '../components/copilot/CopilotContext';
import '../components/copilot/CopilotDrawer.css';
import SemanticLlmSettingsModal from '../components/SemanticLlmSettingsModal';
import { ONBOARDING_STEPS } from '../guide/steps';
import { useOnboarding } from '../guide/OnboardingContext';
import {
  DatabaseOutlined,
  TableOutlined,
  PartitionOutlined,
  LineChartOutlined,
  AlertOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const { Header, Sider, Content } = Layout;

const menuIcons: Record<string, ReactNode> = {
  '/datasources': <DatabaseOutlined />,
  '/metadata': <TableOutlined />,
  '/dimensions': <PartitionOutlined />,
  '/metrics': <LineChartOutlined />,
  '/warning-rules': <AlertOutlined />,
};

function AdminLayoutInner() {
  const [collapsed, setCollapsed] = useState(false);
  const [llmSettingsOpen, setLlmSettingsOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { token } = theme.useToken();
  const { storage } = useOnboarding();
  const { open: copilotOpen } = useCopilot();

  const menuItems = ONBOARDING_STEPS.map((step) => ({
    key: step.path,
    icon: menuIcons[step.path],
    label: step.menuLabel,
    className: storage.guideActive && !storage.completedSteps.includes(step.id)
      ? `guide-menu-pending guide-menu-${step.id}`
      : `guide-menu-${step.id}`,
  }));

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
      <Layout className={copilotOpen ? 'admin-main-with-copilot' : undefined}>
        <Header
          style={{
            padding: '0 24px',
            background: token.colorBgContainer,
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Typography.Text type="secondary">new-atelier 管理控制台</Typography.Text>
          <Space size="middle">
            <CopilotHeaderButton />
            <Button icon={<SettingOutlined />} onClick={() => setLlmSettingsOpen(true)}>
              语义检测设置
            </Button>
            <ConfigBundleActions />
            <OnboardingHeaderActions />
          </Space>
        </Header>
        <OnboardingGuide />
        <SemanticLlmSettingsModal open={llmSettingsOpen} onClose={() => setLlmSettingsOpen(false)} />
        <CopilotDrawer />
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

export default function AdminLayout() {
  return (
    <CopilotProvider>
      <AdminLayoutInner />
    </CopilotProvider>
  );
}
