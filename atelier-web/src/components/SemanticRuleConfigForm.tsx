import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Form, Select, Space, Typography, message } from 'antd';
import { metadataApi } from '../api/metadata';
import { settingsApi } from '../api/settings';
import { warningApi } from '../api/warning';
import type { MetaTable } from '../api/types';
import SemanticCheckGroupsForm from './SemanticCheckGroupsForm';
import SemanticSampleTryPanel from './SemanticSampleTryPanel';
import { normalizeSemanticConfig } from '../utils/semanticRuleForm';
import { SEMANTIC_LLM_UPDATED_EVENT } from '../utils/semanticLlmEvents';

interface SemanticRuleConfigFormProps {
  llmSettingsOpen?: () => void;
  /** 规则编辑弹窗打开时为 true，用于重新拉取 LLM 配置 */
  configActive?: boolean;
}

const TEXT_FIELD_TYPES = ['VARCHAR', 'CHAR', 'TEXT', 'CLOB', 'STRING'];

export default function SemanticRuleConfigForm({
  llmSettingsOpen,
  configActive,
}: SemanticRuleConfigFormProps) {
  const form = Form.useFormInstance();
  const metaTableId = Form.useWatch(['ruleConfig', 'semantic', 'metaTableId'], form) as
    | string
    | undefined;
  const semanticConfig = Form.useWatch(['ruleConfig', 'semantic'], form);
  const [tables, setTables] = useState<MetaTable[]>([]);
  const [fields, setFields] = useState<{ fieldCode: string; fieldName?: string; fieldType?: string }[]>(
    [],
  );
  const [llmConfigured, setLlmConfigured] = useState(false);
  const [llmSummary, setLlmSummary] = useState<string | null>(null);
  const [validating, setValidating] = useState(false);
  const [validationMessage, setValidationMessage] = useState<string | null>(null);
  const [expanding, setExpanding] = useState(false);

  const refreshLlmStatus = useCallback(() => {
    settingsApi
      .getSemanticLlm()
      .then((c) => {
        const configured = Boolean(c.enabled && c.apiKeyConfigured);
        setLlmConfigured(configured);
        if (configured && c.provider && c.model) {
          setLlmSummary(`${c.provider} / ${c.model}`);
        } else {
          setLlmSummary(null);
        }
      })
      .catch(() => {
        setLlmConfigured(false);
        setLlmSummary(null);
      });
  }, []);

  useEffect(() => {
    metadataApi.listTables().then(setTables);
    refreshLlmStatus();
  }, [refreshLlmStatus]);

  useEffect(() => {
    const handleLlmUpdated = () => refreshLlmStatus();
    window.addEventListener(SEMANTIC_LLM_UPDATED_EVENT, handleLlmUpdated);
    return () => window.removeEventListener(SEMANTIC_LLM_UPDATED_EVENT, handleLlmUpdated);
  }, [refreshLlmStatus]);

  useEffect(() => {
    if (configActive) {
      refreshLlmStatus();
    }
  }, [configActive, refreshLlmStatus]);

  useEffect(() => {
    if (!metaTableId) {
      setFields([]);
      return;
    }
    metadataApi.listFields(metaTableId).then(setFields);
  }, [metaTableId]);

  const textFieldOptions = useMemo(
    () =>
      fields
        .filter((f) => {
          const type = (f.fieldType || '').toUpperCase();
          return TEXT_FIELD_TYPES.some((t) => type.includes(t)) || !f.fieldType;
        })
        .map((f) => ({
          label: f.fieldName ? `${f.fieldName} (${f.fieldCode})` : f.fieldCode,
          value: f.fieldCode,
        })),
    [fields],
  );

  const tableOptions = tables
    .filter((t) => t.id)
    .map((t) => ({
      label: t.tableName ? `${t.tableName} (${t.tableCode})` : t.tableCode,
      value: t.id!,
    }));

  const fieldLabelMap = useMemo(() => {
    const map: Record<string, string> = {};
    textFieldOptions.forEach((opt) => {
      map[opt.value] = opt.label;
    });
    return map;
  }, [textFieldOptions]);

  const runValidate = async () => {
    const semantic = normalizeSemanticConfig(form.getFieldValue(['ruleConfig', 'semantic']));
    if (!semantic.semanticGroups?.some((g) => g.checks?.some((c) => c.policy?.trim()))) {
      setValidationMessage(null);
      return;
    }
    setValidating(true);
    try {
      const result = await warningApi.validateSemantic(semantic);
      setValidationMessage(result.message || (result.valid ? '语义配置有效' : '配置无效'));
    } catch (err) {
      setValidationMessage(err instanceof Error ? err.message : '校验失败');
    } finally {
      setValidating(false);
    }
  };

  const handleExpandKeywords = async () => {
    const semantic = normalizeSemanticConfig(form.getFieldValue(['ruleConfig', 'semantic']));
    setExpanding(true);
    try {
      const result = await warningApi.expandKeywords(semantic);
      const groups = semantic.semanticGroups || [];
      groups.forEach((group, groupIndex) => {
        group.checks?.forEach((check, checkIndex) => {
          const fieldCode = check.fieldCode;
          if (!fieldCode || !result.expandedByField[fieldCode]) {
            return;
          }
          form.setFieldValue(
            ['ruleConfig', 'semantic', 'semanticGroups', groupIndex, 'checks', checkIndex, 'expandedKeywords'],
            result.expandedByField[fieldCode],
          );
        });
      });
      const total = Object.values(result.expandedByField).reduce((sum, list) => sum + list.length, 0);
      message.success(`已扩展 ${total} 个关键词`);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '扩展失败');
    } finally {
      setExpanding(false);
    }
  };

  return (
    <div>
      {llmConfigured ? (
        <Alert
          type="success"
          showIcon
          style={{ marginBottom: 12 }}
          message={
            llmSummary
              ? `LLM 已启用（${llmSummary}），混合模式将使用词库 + 大模型判定。`
              : 'LLM 已启用，混合模式将使用词库 + 大模型判定。'
          }
        />
      ) : (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="LLM 未配置或未启用，混合模式将仅使用词库匹配。保存设置后本页会自动刷新状态。"
          action={
            llmSettingsOpen ? (
              <Button size="small" type="link" onClick={llmSettingsOpen}>
                去配置
              </Button>
            ) : undefined
          }
        />
      )}
      <Form.Item
        name={['ruleConfig', 'semantic', 'metaTableId']}
        label="元数据表"
        rules={[{ required: true, message: '请选择元数据表' }]}
      >
        <Select placeholder="选择表" options={tableOptions} showSearch optionFilterProp="label" />
      </Form.Item>
      <SemanticCheckGroupsForm textFieldOptions={textFieldOptions} metaTableId={metaTableId} />
      <Space style={{ marginBottom: 12 }}>
        <Button size="small" loading={expanding} onClick={handleExpandKeywords}>
          扩展词库
        </Button>
        <Button size="small" loading={validating} onClick={runValidate}>
          校验配置
        </Button>
      </Space>
      {validationMessage && (
        <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 8 }}>
          {validationMessage}
        </Typography.Text>
      )}
      <SemanticSampleTryPanel semanticConfig={semanticConfig} fieldLabels={fieldLabelMap} />
    </div>
  );
}
