import { deleteData, getData, postData } from './client';
import type {
  ExpressionValidateResult,
  SemanticRuleConfig,
  SemanticValidateResult,
  WarningRule,
  WarningRulePreviewRequest,
  WarningRulePreviewResult,
} from './types';

export const warningApi = {
  list: () => getData<WarningRule[]>('/warning/rules'),
  get: (id: string) => getData<WarningRule>(`/warning/rules/${id}`),
  save: (data: WarningRule) => postData<WarningRule>('/warning/rules', data),
  delete: (id: string) => deleteData<void>(`/warning/rules/${id}`),
  validateExpression: (expression: string, metricCodes: string[]) =>
    postData<ExpressionValidateResult>('/warning/rules/validate-expression', {
      expression,
      metricCodes,
    }),
  validateSemantic: (
    semanticConfig: SemanticRuleConfig,
    sampleText?: string,
    sampleRow?: Record<string, unknown>,
  ) =>
    postData<SemanticValidateResult>('/warning/rules/validate-semantic', {
      semanticConfig,
      sampleText,
      sampleRow,
    }),
  expandKeywords: (semanticConfig: SemanticRuleConfig) =>
    postData<{ expandedByField: Record<string, string[]> }>(
      '/warning/rules/expand-keywords',
      { semanticConfig },
    ),
  evaluate: (expression: string, metricValues: Record<string, unknown>) =>
    postData<{ triggered: boolean }>('/warning/rules/evaluate', { expression, metricValues }),
  previewRule: (id: string, request: WarningRulePreviewRequest = {}) =>
    postData<WarningRulePreviewResult>(`/warning/rules/${id}/preview`, {
      pageIndex: request.pageIndex ?? 1,
      pageSize: request.pageSize ?? 20,
      filters: request.filters,
      filterGroups: request.filterGroups,
    }),
};
