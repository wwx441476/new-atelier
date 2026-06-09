import type { DimensionBinding, MetricDefinition } from '../api/types';

/** 取多指标公共维度（按 fieldCode 交集，顺序沿用首个指标） */
export function resolveCommonDimensions(
  metricCodes: string[],
  metrics: MetricDefinition[],
): DimensionBinding[] {
  const selected = metricCodes
    .map((code) => metrics.find((m) => m.code === code))
    .filter((m): m is MetricDefinition => Boolean(m));
  if (selected.length === 0) {
    return [];
  }

  let common = new Set(
    (selected[0].dimensions || []).map((d) => d.fieldCode),
  );
  for (let i = 1; i < selected.length; i++) {
    const fieldCodes = new Set(
      (selected[i].dimensions || []).map((d) => d.fieldCode),
    );
    common = new Set([...common].filter((code) => fieldCodes.has(code)));
  }

  return (selected[0].dimensions || [])
    .filter((d) => common.has(d.fieldCode))
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0));
}

/** 维度列展示：有名称时显示「名称 (编码)」，否则仅编码 */
export function formatDimensionDisplayValue(
  columnKey: string,
  value: unknown,
  nameByColumn: Record<string, Record<string, string>>,
): string {
  if (value == null || value === '') {
    return '-';
  }
  const code = String(value);
  const name = nameByColumn[columnKey]?.[code];
  if (name && name !== code) {
    return `${name} (${code})`;
  }
  return code;
}
