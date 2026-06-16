export type DashboardThemeId = 'tech-blue' | 'aurora' | 'light' | 'emerald';

export interface DashboardTheme {
  id: DashboardThemeId;
  name: string;
  canvasBackground: string;
  canvasPattern?: string;
  widgetBackground: string;
  widgetBorder: string;
  widgetShadow: string;
  headerBackground: string;
  textPrimary: string;
  textSecondary: string;
  accent: string;
  accentSoft: string;
  warning: string;
  titleColor: string;
  titleGlow: string;
  chartColors: string[];
  chartText: string;
  chartGrid: string;
  tableHeaderBg: string;
  tableRowHover: string;
  tableBorder: string;
  kpiNumberColor: string;
  selectedBorder: string;
}

export const DASHBOARD_THEMES: Record<DashboardThemeId, DashboardTheme> = {
  'tech-blue': {
    id: 'tech-blue',
    name: '科技蓝',
    canvasBackground:
      'radial-gradient(ellipse 120% 80% at 50% -10%, #1a3a6b 0%, #0a1628 45%, #060d18 100%)',
    canvasPattern:
      'linear-gradient(rgba(64,169,255,0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(64,169,255,0.03) 1px, transparent 1px)',
    widgetBackground: 'linear-gradient(145deg, rgba(16,42,82,0.92) 0%, rgba(8,24,48,0.88) 100%)',
    widgetBorder: 'rgba(64, 169, 255, 0.22)',
    widgetShadow: '0 4px 24px rgba(0, 0, 0, 0.35), inset 0 1px 0 rgba(255,255,255,0.06)',
    headerBackground: 'linear-gradient(90deg, rgba(22,119,255,0.12) 0%, transparent 100%)',
    textPrimary: 'rgba(255, 255, 255, 0.92)',
    textSecondary: 'rgba(255, 255, 255, 0.58)',
    accent: '#40a9ff',
    accentSoft: 'rgba(64, 169, 255, 0.18)',
    warning: '#ff7875',
    titleColor: '#69b1ff',
    titleGlow: '0 0 24px rgba(105, 177, 255, 0.45)',
    chartColors: ['#1677ff', '#36cfc9', '#597ef7', '#9254de', '#ffc53d'],
    chartText: 'rgba(255,255,255,0.72)',
    chartGrid: 'rgba(255,255,255,0.08)',
    tableHeaderBg: 'rgba(22, 119, 255, 0.12)',
    tableRowHover: 'rgba(255, 255, 255, 0.05)',
    tableBorder: 'rgba(255, 255, 255, 0.06)',
    kpiNumberColor: 'linear-gradient(180deg, #91d5ff 0%, #1677ff 100%)',
    selectedBorder: '#1677ff',
  },
  aurora: {
    id: 'aurora',
    name: '极光紫',
    canvasBackground:
      'radial-gradient(ellipse 100% 70% at 20% 0%, #3d1f6e 0%, #1a1035 40%, #0d0820 100%)',
    canvasPattern:
      'linear-gradient(rgba(146,84,222,0.04) 1px, transparent 1px), linear-gradient(90deg, rgba(146,84,222,0.04) 1px, transparent 1px)',
    widgetBackground: 'linear-gradient(145deg, rgba(45,27,78,0.9) 0%, rgba(26,16,53,0.88) 100%)',
    widgetBorder: 'rgba(177, 127, 255, 0.25)',
    widgetShadow: '0 4px 28px rgba(60, 20, 100, 0.4), inset 0 1px 0 rgba(255,255,255,0.05)',
    headerBackground: 'linear-gradient(90deg, rgba(146,84,222,0.15) 0%, transparent 100%)',
    textPrimary: 'rgba(255, 255, 255, 0.92)',
    textSecondary: 'rgba(255, 255, 255, 0.58)',
    accent: '#b37feb',
    accentSoft: 'rgba(179, 127, 235, 0.2)',
    warning: '#ff85c0',
    titleColor: '#d3adf7',
    titleGlow: '0 0 28px rgba(211, 173, 247, 0.4)',
    chartColors: ['#9254de', '#36cfc9', '#ff85c0', '#597ef7', '#ffc53d'],
    chartText: 'rgba(255,255,255,0.72)',
    chartGrid: 'rgba(255,255,255,0.08)',
    tableHeaderBg: 'rgba(146, 84, 222, 0.15)',
    tableRowHover: 'rgba(255, 255, 255, 0.05)',
    tableBorder: 'rgba(255, 255, 255, 0.06)',
    kpiNumberColor: 'linear-gradient(180deg, #efdbff 0%, #9254de 100%)',
    selectedBorder: '#9254de',
  },
  light: {
    id: 'light',
    name: '简约白',
    canvasBackground: 'linear-gradient(180deg, #f5f7fb 0%, #eef1f6 100%)',
    canvasPattern:
      'linear-gradient(rgba(0,0,0,0.025) 1px, transparent 1px), linear-gradient(90deg, rgba(0,0,0,0.025) 1px, transparent 1px)',
    widgetBackground: 'linear-gradient(180deg, #ffffff 0%, #fafbfc 100%)',
    widgetBorder: 'rgba(0, 0, 0, 0.06)',
    widgetShadow: '0 2px 12px rgba(0, 0, 0, 0.06), 0 1px 3px rgba(0,0,0,0.04)',
    headerBackground: 'linear-gradient(90deg, rgba(22,119,255,0.06) 0%, transparent 100%)',
    textPrimary: 'rgba(0, 0, 0, 0.88)',
    textSecondary: 'rgba(0, 0, 0, 0.45)',
    accent: '#1677ff',
    accentSoft: 'rgba(22, 119, 255, 0.08)',
    warning: '#ff4d4f',
    titleColor: '#1677ff',
    titleGlow: 'none',
    chartColors: ['#1677ff', '#13c2c2', '#722ed1', '#fa8c16', '#eb2f96'],
    chartText: 'rgba(0,0,0,0.65)',
    chartGrid: 'rgba(0,0,0,0.06)',
    tableHeaderBg: 'rgba(22, 119, 255, 0.06)',
    tableRowHover: 'rgba(22, 119, 255, 0.04)',
    tableBorder: 'rgba(0, 0, 0, 0.06)',
    kpiNumberColor: 'linear-gradient(180deg, #4096ff 0%, #0958d9 100%)',
    selectedBorder: '#1677ff',
  },
  emerald: {
    id: 'emerald',
    name: '墨绿金',
    canvasBackground:
      'radial-gradient(ellipse 90% 60% at 50% 0%, #1a4035 0%, #0f241e 50%, #081612 100%)',
    canvasPattern:
      'linear-gradient(rgba(82,196,26,0.04) 1px, transparent 1px), linear-gradient(90deg, rgba(82,196,26,0.04) 1px, transparent 1px)',
    widgetBackground: 'linear-gradient(145deg, rgba(20,52,44,0.92) 0%, rgba(12,32,26,0.9) 100%)',
    widgetBorder: 'rgba(115, 209, 61, 0.22)',
    widgetShadow: '0 4px 24px rgba(0, 0, 0, 0.35), inset 0 1px 0 rgba(255,255,255,0.05)',
    headerBackground: 'linear-gradient(90deg, rgba(82,196,26,0.12) 0%, transparent 100%)',
    textPrimary: 'rgba(255, 255, 255, 0.92)',
    textSecondary: 'rgba(255, 255, 255, 0.58)',
    accent: '#73d13d',
    accentSoft: 'rgba(115, 209, 61, 0.18)',
    warning: '#ffa940',
    titleColor: '#95de64',
    titleGlow: '0 0 24px rgba(149, 222, 100, 0.35)',
    chartColors: ['#52c41a', '#faad14', '#36cfc9', '#73d13d', '#ffc53d'],
    chartText: 'rgba(255,255,255,0.72)',
    chartGrid: 'rgba(255,255,255,0.08)',
    tableHeaderBg: 'rgba(82, 196, 26, 0.12)',
    tableRowHover: 'rgba(255, 255, 255, 0.05)',
    tableBorder: 'rgba(255, 255, 255, 0.06)',
    kpiNumberColor: 'linear-gradient(180deg, #d9f7be 0%, #52c41a 100%)',
    selectedBorder: '#52c41a',
  },
};

export const DASHBOARD_THEME_LIST = Object.values(DASHBOARD_THEMES);

export function resolveDashboardTheme(themeId?: string): DashboardTheme {
  if (themeId && themeId in DASHBOARD_THEMES) {
    return DASHBOARD_THEMES[themeId as DashboardThemeId];
  }
  return DASHBOARD_THEMES['tech-blue'];
}

export function applyThemeCssVars(theme: DashboardTheme, el: HTMLElement): void {
  el.style.setProperty('--db-canvas-bg', theme.canvasBackground);
  el.style.setProperty('--db-widget-bg', theme.widgetBackground);
  el.style.setProperty('--db-widget-border', theme.widgetBorder);
  el.style.setProperty('--db-widget-shadow', theme.widgetShadow);
  el.style.setProperty('--db-header-bg', theme.headerBackground);
  el.style.setProperty('--db-text-primary', theme.textPrimary);
  el.style.setProperty('--db-text-secondary', theme.textSecondary);
  el.style.setProperty('--db-accent', theme.accent);
  el.style.setProperty('--db-accent-soft', theme.accentSoft);
  el.style.setProperty('--db-warning', theme.warning);
  el.style.setProperty('--db-title-color', theme.titleColor);
  el.style.setProperty('--db-title-glow', theme.titleGlow);
  el.style.setProperty('--db-kpi-number', theme.kpiNumberColor);
  el.style.setProperty('--db-table-header', theme.tableHeaderBg);
  el.style.setProperty('--db-table-hover', theme.tableRowHover);
  el.style.setProperty('--db-table-border', theme.tableBorder);
  el.style.setProperty('--db-selected-border', theme.selectedBorder);
}
