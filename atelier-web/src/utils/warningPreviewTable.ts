import type { WarningRuleType } from '../api/types';

const DIAGNOSTIC_KEYS = new Set([
  '_triggered',
  '_metricTriggered',
  '_semanticTriggered',
  '_matchReason',
  '_matchLayer',
  '_llmInvoked',
]);

const PER_FIELD_PREFIXES = [
  '_semanticCheck.',
  '_matchReason.',
  '_matchLayer.',
  '_llmInvoked.',
] as const;

const HEADER_LABELS: Record<string, string> = {
  _matchReason: '命中原因',
  _matchLayer: '判定层',
  _llmInvoked: 'LLM',
  _metricTriggered: '指标触发',
  _semanticTriggered: '语义触发',
  _triggered: '是否触发',
};

export interface WarningPreviewColumnOptions {
  ruleType?: WarningRuleType | string;
  metricCodes?: string[];
  keywordOnly?: boolean;
  rows?: Record<string, unknown>[];
}

export function isWarningDataColumn(key: string, metricCodes: string[] = []): boolean {
  if (DIAGNOSTIC_KEYS.has(key)) {
    return false;
  }
  if (PER_FIELD_PREFIXES.some((prefix) => key.startsWith(prefix))) {
    return false;
  }
  if (metricCodes.includes(key)) {
    return false;
  }
  return true;
}

function countSemanticFields(rowKeys: string[]): number {
  return rowKeys.filter((key) => key.startsWith('_semanticCheck.')).length;
}

function shouldShowLlmColumn(rowKeys: string[], rows: Record<string, unknown>[]): boolean {
  if (!rowKeys.includes('_llmInvoked')) {
    return false;
  }
  if (rows.some((row) => row._llmInvoked === true)) {
    return true;
  }
  return rows.some((row) =>
    Object.keys(row).some((key) => key.startsWith('_llmInvoked.') && row[key] === true),
  );
}

export function buildWarningPreviewColumnKeys(
  rowKeys: string[],
  options: WarningPreviewColumnOptions = {},
): string[] {
  const metricCodes = options.metricCodes || [];
  const ruleType = (options.ruleType || 'METRIC').toString().toUpperCase();
  const rows = options.rows || [];
  const keywordOnly = options.keywordOnly !== false;
  const showLlm = !keywordOnly || shouldShowLlmColumn(rowKeys, rows);
  const multiSemantic = countSemanticFields(rowKeys) > 1;

  const dataKeys = rowKeys.filter((key) => isWarningDataColumn(key, metricCodes));
  const metricKeys = metricCodes.filter((code) => rowKeys.includes(code));
  const diagnosticKeys: string[] = [];

  if (ruleType === 'COMPOSITE') {
    if (rowKeys.includes('_metricTriggered')) {
      diagnosticKeys.push('_metricTriggered');
    }
    if (rowKeys.includes('_semanticTriggered')) {
      diagnosticKeys.push('_semanticTriggered');
    }
  }

  if (multiSemantic) {
    rowKeys
      .filter((key) => key.startsWith('_matchReason.'))
      .forEach((key) => diagnosticKeys.push(key));
  } else if (rowKeys.includes('_matchReason')) {
    diagnosticKeys.push('_matchReason');
  }

  if (rowKeys.includes('_matchLayer')) {
    diagnosticKeys.push('_matchLayer');
  }

  if (showLlm && rowKeys.includes('_llmInvoked')) {
    diagnosticKeys.push('_llmInvoked');
  }

  if (rowKeys.includes('_triggered')) {
    diagnosticKeys.push('_triggered');
  }

  return [...dataKeys, ...metricKeys, ...diagnosticKeys];
}

export function getWarningPreviewHeader(
  key: string,
  headers?: Record<string, string>,
): string {
  if (HEADER_LABELS[key]) {
    return HEADER_LABELS[key];
  }
  if (key.startsWith('_matchReason.')) {
    const field = key.slice('_matchReason.'.length);
    return `原因·${headers?.[key]?.replace(/^原因·/, '') || field}`;
  }
  return headers?.[key] || key;
}

export function inferWarningRuleType(rowKeys: string[]): WarningRuleType {
  if (rowKeys.includes('_metricTriggered')) {
    return 'COMPOSITE';
  }
  if (
    rowKeys.includes('_semanticTriggered') ||
    rowKeys.some((key) => key.startsWith('_semanticCheck.'))
  ) {
    return 'SEMANTIC';
  }
  return 'METRIC';
}

export function isWarningTriggerColumn(key: string): boolean {
  return (
    key === '_triggered' ||
    key === '_metricTriggered' ||
    key === '_semanticTriggered' ||
    key.startsWith('_semanticCheck.')
  );
}

export function isWarningLlmColumn(key: string): boolean {
  return key === '_llmInvoked' || key.startsWith('_llmInvoked.');
}

export function isWarningReasonColumn(key: string): boolean {
  return key === '_matchReason' || key.startsWith('_matchReason.');
}
