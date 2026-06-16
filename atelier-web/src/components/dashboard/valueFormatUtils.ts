export interface ValueFormatOptions {
  /** 模板，{value} 为格式化后的数值，如 "{value}美元" */
  valueFormat?: string;
  valuePrefix?: string;
  valueSuffix?: string;
  decimalPlaces?: number;
  useGrouping?: boolean;
}

function formatNumber(raw: unknown, options?: ValueFormatOptions): string {
  if (raw == null || raw === '' || raw === '-') {
    return '-';
  }
  const num = Number(raw);
  if (Number.isNaN(num)) {
    return String(raw);
  }
  const decimals = options?.decimalPlaces;
  const grouped = options?.useGrouping !== false;
  if (decimals != null && decimals >= 0) {
    return num.toLocaleString(undefined, {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
      useGrouping: grouped,
    });
  }
  if (Number.isInteger(num)) {
    return grouped ? num.toLocaleString() : String(num);
  }
  return grouped
    ? num.toLocaleString(undefined, { maximumFractionDigits: 2 })
    : String(num);
}

export function formatDisplayValue(raw: unknown, options?: ValueFormatOptions): string {
  if (raw == null || raw === '') {
    return '-';
  }
  const formattedNumber = formatNumber(raw, options);
  if (formattedNumber === '-') {
    return formattedNumber;
  }

  if (options?.valueFormat?.includes('{value}')) {
    return options.valueFormat.replace(/\{value\}/g, formattedNumber);
  }

  const prefix = options?.valuePrefix ?? '';
  const suffix = options?.valueSuffix ?? '';
  if (prefix || suffix) {
    return `${prefix}${formattedNumber}${suffix}`;
  }

  return formattedNumber;
}

export const VALUE_FORMAT_PRESETS: { label: string; valueFormat?: string }[] = [
  { label: '无格式' },
  { label: '{value}美元', valueFormat: '{value}美元' },
  { label: '{value}元', valueFormat: '{value}元' },
  { label: '¥{value}', valueFormat: '¥{value}' },
  { label: '{value}%', valueFormat: '{value}%' },
];
