import { useEffect, useMemo, useRef } from 'react';
import GridLayout, { type Layout } from 'react-grid-layout';
import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';
import type { DashboardLayoutConfig, DashboardWidget } from '../../api/types';
import DashboardWidgetRenderer from './DashboardWidgetRenderer';
import { applyThemeCssVars, resolveDashboardTheme } from './dashboardThemes';
import { widgetShellClass } from './dashboardUtils';

interface DashboardCanvasProps {
  layout: DashboardLayoutConfig;
  widgets: DashboardWidget[];
  selectedId?: string | null;
  editable?: boolean;
  preview?: boolean;
  refreshKey?: number;
  onSelect?: (id: string | null) => void;
  onLayoutChange?: (widgets: DashboardWidget[]) => void;
}

export default function DashboardCanvas({
  layout,
  widgets,
  selectedId,
  editable = false,
  preview = false,
  refreshKey = 0,
  onSelect,
  onLayoutChange,
}: DashboardCanvasProps) {
  const canvasRef = useRef<HTMLDivElement>(null);
  const theme = resolveDashboardTheme(layout.theme);
  const gridCols = layout.gridCols ?? 24;
  const rowHeight = layout.rowHeight ?? 30;
  const canvasWidth = layout.width ?? 1920;
  const canvasHeight = layout.height ?? 1080;

  useEffect(() => {
    if (canvasRef.current) {
      applyThemeCssVars(theme, canvasRef.current);
    }
  }, [theme]);

  const gridLayout: Layout[] = useMemo(
    () =>
      widgets.map((w) => ({
        i: w.id,
        x: w.x,
        y: w.y,
        w: w.w,
        h: w.h,
        minW: 2,
        minH: 2,
      })),
    [widgets],
  );

  const handleLayoutChange = (nextLayout: Layout[]) => {
    if (!onLayoutChange) {
      return;
    }
    const byId = new Map(nextLayout.map((item) => [item.i, item]));
    onLayoutChange(
      widgets.map((widget) => {
        const item = byId.get(widget.id);
        if (!item) {
          return widget;
        }
        return {
          ...widget,
          x: item.x,
          y: item.y,
          w: item.w,
          h: item.h,
        };
      }),
    );
  };

  return (
    <div
      ref={canvasRef}
      className="dashboard-canvas"
      data-theme={theme.id}
      style={{
        width: canvasWidth,
        height: canvasHeight,
        ...(layout.backgroundColor ? { background: layout.backgroundColor } : {}),
      }}
      onClick={() => onSelect?.(null)}
    >
      <GridLayout
        className="layout"
        layout={gridLayout}
        cols={gridCols}
        rowHeight={rowHeight}
        width={canvasWidth}
        margin={[10, 10]}
        containerPadding={[12, 12]}
        isDraggable={editable}
        isResizable={editable}
        compactType={null}
        preventCollision
        onLayoutChange={handleLayoutChange}
      >
        {widgets.map((widget) => {
          const shellClass = widgetShellClass(widget.type);
          return (
            <div
              key={widget.id}
              onClick={(e) => {
                e.stopPropagation();
                onSelect?.(widget.id);
              }}
            >
              <div
                className={`dashboard-widget-shell${shellClass ? ` ${shellClass}` : ''}${
                  selectedId === widget.id ? ' selected' : ''
                }`}
              >
                {widget.type !== 'TITLE' && (
                  <div className="dashboard-widget-header">{widget.title}</div>
                )}
                <div className="dashboard-widget-body">
                  <DashboardWidgetRenderer
                    widget={widget}
                    theme={theme}
                    preview={preview || editable}
                    refreshKey={refreshKey}
                  />
                </div>
              </div>
            </div>
          );
        })}
      </GridLayout>
    </div>
  );
}
