import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Input, Space, Table, Tag, Typography } from 'antd';
import { ExperimentOutlined } from '@ant-design/icons';
import { warningApi } from '../api/warning';
import type { SemanticRuleConfig, SemanticValidateResult } from '../api/types';
import {
  SEMANTIC_SAMPLE_PRESETS,
  collectSemanticFieldCodes,
  normalizeSemanticConfig,
} from '../utils/semanticRuleForm';

interface SemanticSampleTryPanelProps {
  semanticConfig?: SemanticRuleConfig;
  fieldLabels?: Record<string, string>;
}

export default function SemanticSampleTryPanel({
  semanticConfig,
  fieldLabels = {},
}: SemanticSampleTryPanelProps) {
  const [sampleRow, setSampleRow] = useState<Record<string, string>>({});
  const [trying, setTrying] = useState(false);
  const [result, setResult] = useState<SemanticValidateResult | null>(null);

  const fieldCodes = useMemo(
    () => collectSemanticFieldCodes(normalizeSemanticConfig(semanticConfig)),
    [semanticConfig],
  );

  useEffect(() => {
    setSampleRow((prev) => {
      const next: Record<string, string> = {};
      fieldCodes.forEach((code) => {
        next[code] = prev[code] ?? '';
      });
      return next;
    });
    setResult(null);
  }, [fieldCodes.join(',')]);

  const runSampleTry = async () => {
    const semantic = normalizeSemanticConfig(semanticConfig);
    const hasPolicy = semantic.semanticGroups?.some((g) =>
      g.checks?.some((c) => c.policy?.trim() && c.fieldCode),
    );
    if (!hasPolicy) {
      setResult({ valid: false, message: '请先完善字段与合规策略' });
      return;
    }
    setTrying(true);
    try {
      const response = await warningApi.validateSemantic(semantic, undefined, sampleRow);
      setResult(response);
    } catch (err) {
      setResult({
        valid: false,
        message: err instanceof Error ? err.message : '试跑失败',
      });
    } finally {
      setTrying(false);
    }
  };

  const applyPreset = (presetKey: keyof typeof SEMANTIC_SAMPLE_PRESETS) => {
    const preset = SEMANTIC_SAMPLE_PRESETS[presetKey];
    const next: Record<string, string> = { ...sampleRow };
    Object.entries(preset).forEach(([key, value]) => {
      if (fieldCodes.includes(key)) {
        next[key] = value;
      }
    });
    setSampleRow(next);
    setResult(null);
  };

  if (fieldCodes.length === 0) {
    return null;
  }

  const checkModeLabel = (mode?: string) => (mode === 'REQUIREMENT' ? '必须符合' : '违规检测');

  return (
    <div
      style={{
        marginTop: 16,
        padding: 12,
        border: '1px dashed #d9d9d9',
        borderRadius: 6,
        background: '#fafafa',
      }}
    >
      <Typography.Text strong>
        <ExperimentOutlined style={{ marginRight: 6 }} />
        样例试跑
      </Typography.Text>
      <Typography.Paragraph type="secondary" style={{ fontSize: 12, margin: '8px 0 12px' }}>
        输入各字段示例文本，试跑当前语义条件组（组内且、组间或），确认是否符合预期。
      </Typography.Paragraph>
      <Space direction="vertical" style={{ width: '100%' }} size="small">
        {fieldCodes.map((code) => (
          <div key={code}>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {fieldLabels[code] || code}
            </Typography.Text>
            <Input.TextArea
              rows={2}
              value={sampleRow[code] || ''}
              placeholder={`输入 ${fieldLabels[code] || code} 的样例文本`}
              onChange={(e) => {
                setSampleRow((prev) => ({ ...prev, [code]: e.target.value }));
                setResult(null);
              }}
            />
          </div>
        ))}
      </Space>
      <Space style={{ marginTop: 12 }} wrap>
        <Button type="primary" size="small" loading={trying} onClick={runSampleTry}>
          试跑样例
        </Button>
        {fieldCodes.includes('remark') && fieldCodes.includes('project_name') && (
          <>
            <Button size="small" onClick={() => applyPreset('remark_tobacco_tuition')}>
              填入：烟酒+学杂费
            </Button>
            <Button size="small" onClick={() => applyPreset('remark_clean_tuition')}>
              填入：正常+学杂费
            </Button>
          </>
        )}
      </Space>
      {result && (
        <div style={{ marginTop: 12 }}>
          <Alert
            type={result.sampleTriggered ? 'error' : 'success'}
            showIcon
            message={result.message || (result.sampleTriggered ? '将触发' : '不触发')}
          />
          {result.sampleChecks && result.sampleChecks.length > 0 && (
            <Table
              size="small"
              style={{ marginTop: 8 }}
              pagination={false}
              rowKey={(row) => row.fieldCode || ''}
              dataSource={result.sampleChecks}
              columns={[
                {
                  title: '字段',
                  dataIndex: 'fieldCode',
                  width: 100,
                  render: (code: string) => fieldLabels[code] || code,
                },
                {
                  title: '类型',
                  dataIndex: 'checkMode',
                  width: 90,
                  render: (mode: string) => checkModeLabel(mode),
                },
                {
                  title: '子条件',
                  dataIndex: 'subConditionMet',
                  width: 72,
                  render: (v: boolean) =>
                    v ? <Tag color="error">满足</Tag> : <Tag color="default">不满足</Tag>,
                },
                {
                  title: '判定层',
                  dataIndex: 'layer',
                  width: 72,
                },
                {
                  title: 'LLM',
                  dataIndex: 'llmInvoked',
                  width: 56,
                  render: (v: boolean) => (v ? '是' : '否'),
                },
                {
                  title: '说明',
                  dataIndex: 'reason',
                  ellipsis: true,
                  render: (v: string) => v || '-',
                },
              ]}
            />
          )}
        </div>
      )}
    </div>
  );
}
