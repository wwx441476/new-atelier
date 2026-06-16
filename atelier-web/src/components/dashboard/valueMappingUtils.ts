/** 字段原始值 → 展示名，如 dept_code: { "001": "销售部" } */
export type FieldValueMappings = Record<string, Record<string, string>>;

export function mapFieldValue(
  field: string | undefined,
  raw: unknown,
  mappings?: FieldValueMappings,
): string {
  if (raw == null || raw === '') {
    return '-';
  }
  const key = String(raw);
  if (!field || !mappings?.[field]) {
    return key;
  }
  return mappings[field][key] ?? key;
}

export function mergeFieldValueMappings(
  current: FieldValueMappings | undefined,
  field: string,
  entries: Record<string, string>,
): FieldValueMappings {
  return {
    ...(current ?? {}),
    [field]: {
      ...(current?.[field] ?? {}),
      ...entries,
    },
  };
}
