import { Button, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { DashboardWidgetType } from '../../api/types';
import { WIDGET_TYPE_LABELS } from './dashboardUtils';

const PALETTE_GROUPS: { title: string; types: DashboardWidgetType[] }[] = [
  {
    title: '布局',
    types: ['TITLE'],
  },
  {
    title: '指标',
    types: ['METRIC_VALUE', 'METRIC_CHART', 'METRIC_TABLE'],
  },
  {
    title: '数据库查询',
    types: ['SQL_VALUE', 'SQL_CHART', 'SQL_TABLE'],
  },
  {
    title: '预警',
    types: ['WARNING_STAT', 'WARNING_TABLE'],
  },
];

interface DashboardWidgetPaletteProps {
  onAdd: (type: DashboardWidgetType) => void;
}

export default function DashboardWidgetPalette({ onAdd }: DashboardWidgetPaletteProps) {
  return (
    <div className="dashboard-palette">
      <Typography.Text strong>组件库</Typography.Text>
      <Typography.Paragraph
        type="secondary"
        style={{ fontSize: 12, marginTop: 4, marginBottom: 12 }}
      >
        点击添加到画布，可拖拽调整位置与大小
      </Typography.Paragraph>
      {PALETTE_GROUPS.map((group) => (
        <div key={group.title} style={{ marginBottom: 12 }}>
          <Typography.Text
            type="secondary"
            style={{ fontSize: 11, display: 'block', marginBottom: 6 }}
          >
            {group.title}
          </Typography.Text>
          {group.types.map((type) => (
            <Button
              key={type}
              block
              className="dashboard-palette-item"
              icon={<PlusOutlined />}
              onClick={() => onAdd(type)}
            >
              {WIDGET_TYPE_LABELS[type]}
            </Button>
          ))}
        </div>
      ))}
    </div>
  );
}
