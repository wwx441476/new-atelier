const KEYWORDS = new Set(['and', 'or', 'not', 'true', 'false', 'null']);
const IDENTIFIER = /\b([a-zA-Z_][a-zA-Z0-9_]*)\b/g;

export function normalizeWarningExpression(expression: string): string {
  return expression
    .trim()
    .replace(/或者/g, ' || ')
    .replace(/并且/g, ' && ')
    .replace(/或/g, ' || ')
    .replace(/且/g, ' && ')
    .replace(/\s+/g, ' ')
    .trim();
}

export function checkParenthesesBalance(expression: string): string | null {
  let depth = 0;
  for (const char of expression) {
    if (char === '(') {
      depth += 1;
    } else if (char === ')') {
      depth -= 1;
      if (depth < 0) {
        return '括号不匹配：存在多余的右括号 )';
      }
    }
  }
  if (depth > 0) {
    return `括号不匹配：存在 ${depth} 个未闭合的左括号 (`;
  }
  return null;
}

export function wrapExpressionInParens(expression: string): string {
  const trimmed = expression.trim();
  if (!trimmed) {
    return '(';
  }
  if (trimmed.startsWith('(') && trimmed.endsWith(')')) {
    return trimmed;
  }
  return `(${trimmed})`;
}

export function extractMetricVariables(expression: string): string[] {
  const normalized = normalizeWarningExpression(expression);
  const variables = new Set<string>();
  for (const match of normalized.matchAll(IDENTIFIER)) {
    const id = match[1];
    if (!KEYWORDS.has(id.toLowerCase())) {
      variables.add(id);
    }
  }
  return [...variables];
}

export interface ExpressionTemplate {
  label: string;
  build: (codes: string[]) => string | null;
}

export const EXPRESSION_TEMPLATES: ExpressionTemplate[] = [
  {
    label: '单指标过低',
    build: (codes) => (codes[0] ? `${codes[0]} < 500` : null),
  },
  {
    label: '单指标过高',
    build: (codes) => (codes[0] ? `${codes[0]} > 1000` : null),
  },
  {
    label: '两指标 OR',
    build: (codes) =>
      codes.length >= 2 ? `${codes[0]} < 500 或 ${codes[1]} > 100` : null,
  },
  {
    label: '两指标 AND',
    build: (codes) =>
      codes.length >= 2 ? `${codes[0]} < 500 且 ${codes[1]} > 0` : null,
  },
  {
    label: '( ) 或 ( )',
    build: (codes) =>
      codes.length >= 2 ? `(${codes[0]} < 500) 或 (${codes[1]} > 100)` : null,
  },
  {
    label: '( ) 且 ( )',
    build: (codes) =>
      codes.length >= 2 ? `(${codes[0]} < 500) 且 (${codes[1]} > 0)` : null,
  },
  {
    label: '混合分组',
    build: (codes) =>
      codes.length >= 2
        ? `(${codes[0]} < 500) 或 (${codes[1]} > 100 且 ${codes[0]} > 0)`
        : null,
  },
];

export function buildExpressionTemplates(metricCodes: string[]) {
  return EXPRESSION_TEMPLATES.map((template) => ({
    label: template.label,
    expression: template.build(metricCodes),
  })).filter((item) => item.expression);
}
