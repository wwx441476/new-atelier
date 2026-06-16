import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Button, Space, Spin, Typography } from 'antd';
import { CloseOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { dashboardApi } from '../api/dashboard';
import type { DashboardScreen } from '../api/types';
import DashboardCanvas from '../components/dashboard/DashboardCanvas';
import DashboardThemeSelector from '../components/dashboard/DashboardThemeSelector';
import type { DashboardThemeId } from '../components/dashboard/dashboardThemes';
import { resolveDashboardTheme } from '../components/dashboard/dashboardThemes';
import '../components/dashboard/dashboard.css';

const DEFAULT_LAYOUT = {
  width: 1920,
  height: 1080,
  gridCols: 24,
  rowHeight: 30,
  theme: 'tech-blue' as DashboardThemeId,
};

export default function DashboardPresentPage() {
  const { code } = useParams<{ code: string }>();
  const navigate = useNavigate();
  const rootRef = useRef<HTMLDivElement>(null);
  const [loading, setLoading] = useState(true);
  const [screen, setScreen] = useState<DashboardScreen | null>(null);
  const [scale, setScale] = useState(1);
  const [refreshKey, setRefreshKey] = useState(0);
  const [viewTheme, setViewTheme] = useState<DashboardThemeId | undefined>();

  const load = useCallback(async () => {
    if (!code) {
      return;
    }
    setLoading(true);
    try {
      const data = await dashboardApi.getByCode(code);
      setScreen({
        ...data,
        layout: { ...DEFAULT_LAYOUT, ...data.layout },
        widgets: data.widgets ?? [],
      });
      setViewTheme(undefined);
    } finally {
      setLoading(false);
    }
  }, [code]);

  useEffect(() => {
    void load();
  }, [load]);

  const layout = useMemo(() => {
    const base = screen?.layout ?? DEFAULT_LAYOUT;
    if (viewTheme) {
      return { ...base, theme: viewTheme };
    }
    return base;
  }, [screen, viewTheme]);

  const theme = resolveDashboardTheme(layout.theme);

  useEffect(() => {
    const updateScale = () => {
      const vw = window.innerWidth;
      const vh = window.innerHeight;
      const w = layout.width ?? 1920;
      const h = layout.height ?? 1080;
      setScale(Math.min(vw / w, vh / h, 1));
    };
    updateScale();
    window.addEventListener('resize', updateScale);
    return () => window.removeEventListener('resize', updateScale);
  }, [layout]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      setRefreshKey((k) => k + 1);
    }, 30000);
    return () => window.clearInterval(timer);
  }, []);

  if (loading) {
    return (
      <div className="dashboard-present-root">
        <Spin size="large" />
      </div>
    );
  }

  if (!screen) {
    return (
      <div className="dashboard-present-root">
        <Typography.Text type="secondary">大屏不存在</Typography.Text>
      </div>
    );
  }

  const width = layout.width ?? 1920;
  const height = layout.height ?? 1080;

  return (
    <div
      className="dashboard-present-root"
      ref={rootRef}
      data-theme={theme.id}
      style={{ background: theme.canvasBackground }}
    >
      <div className="dashboard-present-toolbar">
        <Space>
          <DashboardThemeSelector
            size="small"
            value={layout.theme}
            onChange={setViewTheme}
          />
          <Button icon={<ReloadOutlined />} onClick={() => setRefreshKey((k) => k + 1)}>
            刷新
          </Button>
          <Button icon={<CloseOutlined />} onClick={() => navigate('/dashboards')}>
            退出
          </Button>
        </Space>
      </div>
      <div
        className="dashboard-present-scale"
        style={{
          transform: `scale(${scale})`,
          width,
          height,
        }}
      >
        <DashboardCanvas
          layout={layout}
          widgets={screen.widgets ?? []}
          preview
          refreshKey={refreshKey}
        />
      </div>
    </div>
  );
}
