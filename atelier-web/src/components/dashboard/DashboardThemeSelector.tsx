import { Select } from 'antd';
import type { DashboardThemeId } from './dashboardThemes';
import { DASHBOARD_THEME_LIST } from './dashboardThemes';

interface DashboardThemeSelectorProps {
  value?: string | null;
  onChange: (themeId: DashboardThemeId) => void;
  size?: 'small' | 'middle';
}

const THEME_SWATCH: Record<DashboardThemeId, string> = {
  'tech-blue': 'linear-gradient(135deg, #1677ff, #0a1628)',
  aurora: 'linear-gradient(135deg, #9254de, #1a1035)',
  light: 'linear-gradient(135deg, #ffffff, #eef1f6)',
  emerald: 'linear-gradient(135deg, #52c41a, #0f241e)',
};

export default function DashboardThemeSelector({
  value,
  onChange,
  size = 'middle',
}: DashboardThemeSelectorProps) {
  return (
    <Select
      size={size}
      value={(value ?? 'tech-blue') as DashboardThemeId}
      onChange={onChange}
      style={{ minWidth: 130 }}
      options={DASHBOARD_THEME_LIST.map((theme) => ({
        label: (
          <span>
            <span
              className="dashboard-theme-preview"
              style={{ background: THEME_SWATCH[theme.id] }}
            />
            {theme.name}
          </span>
        ),
        value: theme.id,
      }))}
    />
  );
}
