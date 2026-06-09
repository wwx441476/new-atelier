import { deleteData, getData, postData } from './client';
import type { WarningRule, WarningRulePreviewResult } from './types';

export const warningApi = {
  list: () => getData<WarningRule[]>('/warning/rules'),
  get: (id: string) => getData<WarningRule>(`/warning/rules/${id}`),
  save: (data: WarningRule) => postData<WarningRule>('/warning/rules', data),
  delete: (id: string) => deleteData<void>(`/warning/rules/${id}`),
  evaluate: (expression: string, metricValues: Record<string, unknown>) =>
    postData<{ triggered: boolean }>('/warning/rules/evaluate', { expression, metricValues }),
  previewRule: (id: string, pageIndex = 1, pageSize = 20) =>
    getData<WarningRulePreviewResult>(`/warning/rules/${id}/preview`, { pageIndex, pageSize }),
};
