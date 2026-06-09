export type TimeGranularity = 'YEAR' | 'QUARTER' | 'MONTH';

export interface TimeFormatPreset {
  label: string;
  codeFormat: string;
  nameFormat: string;
}

export const TIME_FORMAT_PRESETS: Record<TimeGranularity, TimeFormatPreset[]> = {
  YEAR: [
    { label: '2024', codeFormat: 'YYYY', nameFormat: 'YYYY' },
    { label: '2024年', codeFormat: 'YYYY', nameFormat: 'YYYY年' },
    { label: 'FY2024', codeFormat: 'FYYYYY', nameFormat: 'FYYYYY' },
    { label: '2024财年', codeFormat: 'YYYY', nameFormat: 'YYYY财年' },
    { label: '编码24 / 名称2024年', codeFormat: 'YY', nameFormat: 'YYYY年' },
  ],
  QUARTER: [
    { label: '2024Q1', codeFormat: 'YYYYQN', nameFormat: 'YYYYQN' },
    { label: '2024-Q1', codeFormat: 'YYYY-QN', nameFormat: 'YYYY-QN' },
    { label: '2024年第1季度', codeFormat: 'YYYYQN', nameFormat: 'YYYY年第Q季度' },
  ],
  MONTH: [
    { label: '202401', codeFormat: 'YYYYMM', nameFormat: 'YYYYMM' },
    { label: '2024-01', codeFormat: 'YYYY-MM', nameFormat: 'YYYY-MM' },
    { label: '2024年1月', codeFormat: 'YYYYMM', nameFormat: 'YYYY年M月' },
    { label: '2024/01', codeFormat: 'YYYY/MM', nameFormat: 'YYYY年M月' },
  ],
};

export function formatTimeValue(template: string, year: number, month: number, quarter: number): string {
  return template
    .replace(/YYYY/g, String(year))
    .replace(/YY/g, String(year % 100).padStart(2, '0'))
    .replace(/MM/g, String(month).padStart(2, '0'))
    .replace(/QN/g, `Q${quarter}`)
    .replace(/M/g, String(month))
    .replace(/Q/g, String(quarter));
}

interface PreviewSlot {
  year: number;
  month: number;
  quarter: number;
}

function buildPreviewSlots(
  granularity: TimeGranularity,
  startYear: number,
  endYear: number,
  startMonth = 1,
  endMonth = 12,
): PreviewSlot[] {
  if (granularity === 'YEAR') {
    const slots: PreviewSlot[] = [];
    for (let year = startYear; year <= endYear; year += 1) {
      slots.push({ year, month: 1, quarter: 1 });
    }
    return slots;
  }
  if (granularity === 'QUARTER') {
    const slots: PreviewSlot[] = [];
    for (let year = startYear; year <= endYear; year += 1) {
      for (let quarter = 1; quarter <= 4; quarter += 1) {
        slots.push({ year, month: quarter * 3 - 2, quarter });
      }
    }
    return slots;
  }
  const slots: PreviewSlot[] = [];
  let year = startYear;
  let month = startMonth;
  while (year < endYear || (year === endYear && month <= endMonth)) {
    slots.push({ year, month, quarter: Math.floor((month - 1) / 3) + 1 });
    month += 1;
    if (month > 12) {
      month = 1;
      year += 1;
    }
  }
  return slots;
}

export function previewTimeValues(
  granularity: TimeGranularity,
  startYear: number,
  endYear: number,
  codeFormat: string,
  nameFormat: string,
  startMonth = 1,
  endMonth = 12,
  limit = 5,
): { code: string; name: string }[] {
  return buildPreviewSlots(granularity, startYear, endYear, startMonth, endMonth)
    .slice(0, limit)
    .map((slot) => ({
      code: formatTimeValue(codeFormat, slot.year, slot.month, slot.quarter),
      name: formatTimeValue(nameFormat, slot.year, slot.month, slot.quarter),
    }));
}

export function countTimeValues(
  granularity: TimeGranularity,
  startYear: number,
  endYear: number,
  startMonth = 1,
  endMonth = 12,
): number {
  return buildPreviewSlots(granularity, startYear, endYear, startMonth, endMonth).length;
}
