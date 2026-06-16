import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Space,
  Spin,
  Typography,
  message,
} from 'antd';
import {
  ArrowLeftOutlined,
  FullscreenOutlined,
  ReloadOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { dashboardApi } from '../api/dashboard';
import { datasourceApi } from '../api/datasource';
import { metricApi } from '../api/metric';
import { warningApi } from '../api/warning';
import { dimensionApi } from '../api/dimension';
import type { DashboardScreen, DashboardWidget, DashboardWidgetType } from '../api/types';
import DashboardCanvas from '../components/dashboard/DashboardCanvas';
import DashboardWidgetConfigPanel from '../components/dashboard/DashboardWidgetConfigPanel';
import DashboardWidgetPalette from '../components/dashboard/DashboardWidgetPalette';
import DashboardThemeSelector from '../components/dashboard/DashboardThemeSelector';
import type { DashboardThemeId } from '../components/dashboard/dashboardThemes';
import { createWidget, nextWidgetY } from '../components/dashboard/dashboardUtils';
import '../components/dashboard/dashboard.css';

const DEFAULT_LAYOUT = {
  width: 1920,
  height: 1080,
  gridCols: 24,
  rowHeight: 30,
  theme: 'tech-blue' as DashboardThemeId,
};

export default function DashboardDesignerPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [screen, setScreen] = useState<DashboardScreen | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [metrics, setMetrics] = useState<Awaited<ReturnType<typeof metricApi.listDefinitions>>>([]);
  const [rules, setRules] = useState<Awaited<ReturnType<typeof warningApi.list>>>([]);
  const [datasources, setDatasources] = useState<Awaited<ReturnType<typeof datasourceApi.list>>>([]);
  const [dimensions, setDimensions] = useState<Awaited<ReturnType<typeof dimensionApi.list>>>([]);

  const load = useCallback(async () => {
    if (!id) {
      return;
    }
    setLoading(true);
    try {
      const [dashboard, metricList, ruleList, datasourceList, dimensionList] = await Promise.all([
        dashboardApi.getById(id),
        metricApi.listDefinitions(),
        warningApi.list(),
        datasourceApi.list(),
        dimensionApi.list(),
      ]);
      setScreen({
        ...dashboard,
        layout: { ...DEFAULT_LAYOUT, ...dashboard.layout },
        widgets: dashboard.widgets ?? [],
      });
      setMetrics(metricList);
      setRules(ruleList);
      setDatasources(datasourceList);
      setDimensions(dimensionList);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  const selectedWidget = useMemo(
    () => screen?.widgets?.find((w) => w.id === selectedId) ?? null,
    [screen, selectedId],
  );

  const updateWidgets = (widgets: DashboardWidget[]) => {
    setScreen((prev) => (prev ? { ...prev, widgets } : prev));
  };

  const handleAddWidget = (type: DashboardWidgetType) => {
    if (!screen) {
      return;
    }
    const widget = createWidget(type, nextWidgetY(screen.widgets ?? []));
    const widgets = [...(screen.widgets ?? []), widget];
    updateWidgets(widgets);
    setSelectedId(widget.id);
  };

  const handleSave = async () => {
    if (!screen) {
      return;
    }
    setSaving(true);
    try {
      const saved = await dashboardApi.save(screen);
      setScreen(saved);
      message.success('大屏已保存');
    } finally {
      setSaving(false);
    }
  };

  const patchScreen = (partial: Partial<DashboardScreen>) => {
    setScreen((prev) => (prev ? { ...prev, ...partial } : prev));
  };

  const patchLayout = (partial: NonNullable<DashboardScreen['layout']>) => {
    setScreen((prev) =>
      prev ? { ...prev, layout: { ...prev.layout, ...partial } } : prev,
    );
  };

  if (loading || !screen) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div className="dashboard-page">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/dashboards')}>
            返回列表
          </Button>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {screen.name}
          </Typography.Title>
          <Typography.Text type="secondary">({screen.code})</Typography.Text>
        </Space>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => setRefreshKey((k) => k + 1)}>
            刷新数据
          </Button>
          <Button
            icon={<FullscreenOutlined />}
            onClick={() => window.open(`/screen/${screen.code}`, '_blank')}
          >
            全屏演示
          </Button>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => void handleSave()}>
            保存
          </Button>
        </Space>
      </div>

      <Form layout="inline" size="small" style={{ marginTop: 8 }}>
        <Form.Item label="名称">
          <Input
            value={screen.name}
            onChange={(e) => patchScreen({ name: e.target.value })}
            style={{ width: 160 }}
          />
        </Form.Item>
        <Form.Item label="画布宽">
          <InputNumber
            min={800}
            max={3840}
            value={screen.layout?.width}
            onChange={(v) => patchLayout({ width: v ?? 1920 })}
          />
        </Form.Item>
        <Form.Item label="画布高">
          <InputNumber
            min={600}
            max={2160}
            value={screen.layout?.height}
            onChange={(v) => patchLayout({ height: v ?? 1080 })}
          />
        </Form.Item>
        <Form.Item label="主题">
          <DashboardThemeSelector
            value={screen.layout?.theme}
            onChange={(themeId) => patchLayout({ theme: themeId })}
          />
        </Form.Item>
        <Form.Item label="背景色">
          <Input
            placeholder="留空使用主题背景"
            value={screen.layout?.backgroundColor ?? ''}
            onChange={(e) =>
              patchLayout({ backgroundColor: e.target.value.trim() || undefined })
            }
            style={{ width: 120 }}
          />
        </Form.Item>
      </Form>

      <div className="dashboard-designer">
        <DashboardWidgetPalette onAdd={handleAddWidget} />
        <div className="dashboard-canvas-wrap">
          <DashboardCanvas
            layout={screen.layout ?? DEFAULT_LAYOUT}
            widgets={screen.widgets ?? []}
            selectedId={selectedId}
            editable
            preview
            refreshKey={refreshKey}
            onSelect={setSelectedId}
            onLayoutChange={updateWidgets}
          />
        </div>
        <DashboardWidgetConfigPanel
          widget={selectedWidget}
          metrics={metrics}
          rules={rules}
          datasources={datasources}
          dimensions={dimensions}
          onChange={(widget) => {
            updateWidgets(
              (screen.widgets ?? []).map((w) => (w.id === widget.id ? widget : w)),
            );
          }}
          onDelete={(widgetId) => {
            updateWidgets((screen.widgets ?? []).filter((w) => w.id !== widgetId));
            if (selectedId === widgetId) {
              setSelectedId(null);
            }
          }}
        />
      </div>
    </div>
  );
}
