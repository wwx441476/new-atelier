import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Input, Space, Tag, Typography } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { warningApi } from '../api/warning';
import type { ExpressionValidateResult, MetricDefinition } from '../api/types';
import {
  buildExpressionTemplates,
  checkParenthesesBalance,
  extractMetricVariables,
  normalizeWarningExpression,
  wrapExpressionInParens,
} from '../utils/warningExpression';

interface WarningExpressionFieldProps {
  value?: string;
  onChange?: (value: string) => void;
  metricCodes?: string[];
  metrics?: MetricDefinition[];
}

export default function WarningExpressionField({
  value = '',
  onChange,
  metricCodes = [],
  metrics = [],
}: WarningExpressionFieldProps) {
  const [validating, setValidating] = useState(false);
  const [result, setResult] = useState<ExpressionValidateResult | null>(null);
  const timerRef = useRef<number>();

  const metricNameByCode = Object.fromEntries(metrics.map((m) => [m.code, m.name]));

  const runValidate = useCallback(async (expression: string, codes: string[]) => {
    if (!expression?.trim()) {
      setResult(null);
      return;
    }
    setValidating(true);
    try {
      const validation = await warningApi.validateExpression(expression, codes);
      setResult(validation);
    } catch (err) {
      setResult({
        valid: false,
        message: err instanceof Error ? err.message : '校验失败',
        usedVariables: extractMetricVariables(expression),
        unknownVariables: [],
        unusedMetrics: [],
      });
    } finally {
      setValidating(false);
    }
  }, []);

  useEffect(() => {
    window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => {
      runValidate(value, metricCodes);
    }, 400);
    return () => window.clearTimeout(timerRef.current);
  }, [value, metricCodes, runValidate]);

  const insertSnippet = (snippet: string) => {
    const next = value?.trim() ? `${value.trim()} ${snippet}` : snippet;
    onChange?.(next);
  };

  const applyTemplate = (expression: string) => {
    onChange?.(expression);
  };

  const templates = buildExpressionTemplates(metricCodes);
  const localVariables = extractMetricVariables(value);
  const normalized = normalizeWarningExpression(value);
  const parenError = value?.trim() ? checkParenthesesBalance(value) : null;

  return (
    <div>
      <Input.TextArea
        rows={3}
        value={value}
        onChange={(e) => onChange?.(e.target.value)}
        placeholder="如 (profit < 500) 或 (cost > 100)"
      />

      <div style={{ marginTop: 8 }}>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          运算符与分组：
        </Typography.Text>
        <div style={{ marginTop: 4 }}>
          <Space size={[4, 4]} wrap>
            <Tag style={{ cursor: 'pointer' }} onClick={() => insertSnippet('(')}>
              (
            </Tag>
            <Tag style={{ cursor: 'pointer' }} onClick={() => insertSnippet(')')}>
              )
            </Tag>
            <Tag style={{ cursor: 'pointer' }} onClick={() => insertSnippet('||')}>
              或 ||
            </Tag>
            <Tag style={{ cursor: 'pointer' }} onClick={() => insertSnippet('&&')}>
              且 &&
            </Tag>
            {value?.trim() && (
              <Tag
                color="purple"
                style={{ cursor: 'pointer' }}
                onClick={() => onChange?.(wrapExpressionInParens(value))}
              >
                整体加括号
              </Tag>
            )}
          </Space>
        </div>
      </div>

      {metricCodes.length > 0 && (
        <div style={{ marginTop: 8 }}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            点击插入指标变量：
          </Typography.Text>
          <div style={{ marginTop: 4 }}>
            <Space size={[4, 4]} wrap>
              {metricCodes.map((code) => (
                <Tag
                  key={code}
                  color="blue"
                  style={{ cursor: 'pointer', marginInlineEnd: 0 }}
                  onClick={() => insertSnippet(code)}
                >
                  {metricNameByCode[code] ? `${metricNameByCode[code]} (${code})` : code}
                </Tag>
              ))}
            </Space>
          </div>
        </div>
      )}

      {templates.length > 0 && (
        <div style={{ marginTop: 8 }}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            快捷模板：
          </Typography.Text>
          <div style={{ marginTop: 4 }}>
            <Space size={[4, 4]} wrap>
              {templates.map((item) => (
                <Button
                  key={item.label}
                  size="small"
                  onClick={() => item.expression && applyTemplate(item.expression)}
                >
                  {item.label}
                </Button>
              ))}
            </Space>
          </div>
        </div>
      )}

      <Typography.Paragraph type="secondary" style={{ fontSize: 12, margin: '8px 0 0' }}>
        变量名使用指标 <Typography.Text code>code</Typography.Text>；支持{' '}
        <Typography.Text code>{'>'}</Typography.Text>{' '}
        <Typography.Text code>{'<'}</Typography.Text>{' '}
        <Typography.Text code>{'>='}</Typography.Text>{' '}
        <Typography.Text code>{'<='}</Typography.Text>{' '}
        <Typography.Text code>==</Typography.Text>；逻辑可用中文{' '}
        <Typography.Text code>或/且</Typography.Text> 或{' '}
        <Typography.Text code>||/&&</Typography.Text>；用{' '}
        <Typography.Text code>( )</Typography.Text> 分组，如{' '}
        <Typography.Text code>(profit &lt; 500) 或 (cost &gt; 100)</Typography.Text>
      </Typography.Paragraph>

      {parenError && (
        <Alert
          type="error"
          showIcon
          style={{ marginTop: 8 }}
          message="括号不匹配"
          description={parenError}
        />
      )}

      {value?.trim() && (
        <div style={{ marginTop: 8 }}>
          {validating && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              正在校验…
            </Typography.Text>
          )}
          {!validating && !parenError && result && (
            <Alert
              type={result.valid ? 'success' : 'error'}
              showIcon
              icon={result.valid ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
              message={result.valid ? '表达式可用' : '表达式需要修正'}
              description={
                <div style={{ fontSize: 13 }}>
                  <div>{result.message}</div>
                  {normalized && normalized !== value.trim() && (
                    <div style={{ marginTop: 4 }}>
                      标准化后：<Typography.Text code>{result.normalizedExpression || normalized}</Typography.Text>
                    </div>
                  )}
                  {localVariables.length > 0 && (
                    <div style={{ marginTop: 4 }}>
                      引用变量：{localVariables.map((v) => (
                        <Tag
                          key={v}
                          color={metricCodes.includes(v) ? 'processing' : 'error'}
                          style={{ marginInlineEnd: 4 }}
                        >
                          {v}
                        </Tag>
                      ))}
                    </div>
                  )}
                  {result.valid && result.sampleTriggered != null && (
                    <div style={{ marginTop: 4, color: '#666' }}>
                      <ThunderboltOutlined /> 样例试算（各指标值=0）：{' '}
                      {result.sampleTriggered ? '触发预警' : '未触发'}
                    </div>
                  )}
                </div>
              }
            />
          )}
        </div>
      )}
    </div>
  );
}

export async function validateWarningExpressionField(
  expression: string,
  metricCodes: string[],
): Promise<void> {
  if (!expression?.trim()) {
    throw new Error('请输入预警表达式');
  }
  const parenError = checkParenthesesBalance(expression);
  if (parenError) {
    throw new Error(parenError);
  }
  const result = await warningApi.validateExpression(expression, metricCodes);
  if (!result.valid) {
    throw new Error(result.message || '表达式校验未通过');
  }
}
