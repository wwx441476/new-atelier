import type { FilterGroupDto, MetricQueryRequest } from '../api/types';

export type FilterConditionForm = {
  field?: string;
  operator?: string;
  values?: string | string[];
};

export type FilterGroupForm = {
  conditions?: FilterConditionForm[];
};

export type FilterQuery = Pick<MetricQueryRequest, 'filters' | 'filterGroups'>;

export function normalizeFilterValues(values?: string | string[]): string[] {
  if (!values) {
    return [];
  }
  if (Array.isArray(values)) {
    return values.map((v) => String(v).trim()).filter(Boolean);
  }
  return values
    .split(',')
    .map((v) => v.trim())
    .filter(Boolean);
}

/** 将表单条件组转为 API 过滤参数：单组用 filters，多组用 filterGroups（组内且、组间或） */
export function buildFilterRequest(filterGroups: FilterGroupForm[]): FilterQuery {
  const groups: FilterGroupDto[] = (filterGroups || [])
    .map((group) => ({
      conditions: (group.conditions || [])
        .filter((c) => c.field && normalizeFilterValues(c.values).length > 0)
        .map((c) => ({
          field: c.field!,
          operator: c.operator || 'IN',
          values: normalizeFilterValues(c.values),
        })),
    }))
    .filter((group) => group.conditions.length > 0);

  if (groups.length === 0) {
    return {};
  }
  if (groups.length === 1) {
    return { filters: groups[0].conditions };
  }
  return { filterGroups: groups };
}

export function hasActiveFilterQuery(query: FilterQuery): boolean {
  return (query.filters?.length ?? 0) > 0 || (query.filterGroups?.length ?? 0) > 0;
}

export function createDefaultFilterGroup(field = ''): FilterGroupForm {
  return {
    conditions: [{ field, operator: 'IN', values: [] }],
  };
}
