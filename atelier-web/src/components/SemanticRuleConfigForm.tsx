import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Form, Input, Radio, Select, Space, Tag, Typography, message } from 'antd';
import { metadataApi } from '../api/metadata';
import { settingsApi } from '../api/settings';
import { warningApi } from '../api/warning';
import type { MetaTable, MetaTableField } from '../api/types';

interface SemanticRuleConfigFormProps {
  llmSettingsOpen?: () => void;
}

const TEXT_FIELD_TYPES = ['VARCHAR', 'CHAR', 'TEXT', 'CLOB', 'STRING'];

/** 隐藏字段占位，支持 string[] 等非字符串值 */
function HiddenFieldHolder(_props: { value?: unknown; onChange?: (value: unknown) => void }) {
  return null;
}

export default function SemanticRuleConfigForm({ llmSettingsOpen }: SemanticRuleConfigFormProps) {
  const form = Form.useFormInstance();
  const metaTableId = Form.useWatch(['ruleConfig', 'semantic', 'metaTableId'], form) as
    | string
    | undefined;
  const [tables, setTables] = useState<MetaTable[]>([]);
  const [fields, setFields] = useState<MetaTableField[]>([]);
  const [llmConfigured, setLlmConfigured] = useState(false);
  const [validating, setValidating] = useState(false);
  const [validationMessage, setValidationMessage] = useState<string | null>(null);
  const [expanding, setExpanding] = useState(false);

  useEffect(() => {
    metadataApi.listTables().then(setTables);
    settingsApi.getSemanticLlm().then((c) => setLlmConfigured(c.enabled && c.apiKeyConfigured));
  }, []);

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

  const runValidate = useCallback(async () => {
    const semantic = form.getFieldValue(['ruleConfig', 'semantic']);
    if (!semantic?.policy?.trim()) {
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
  }, [form]);

  const handleExpandKeywords = async () => {
    const semantic = form.getFieldValue(['ruleConfig', 'semantic']);
    setExpanding(true);
    try {
      const result = await warningApi.expandKeywords(semantic);
      form.setFieldValue(['ruleConfig', 'semantic', 'expandedKeywords'], result.keywords);
      message.success(`已扩展 ${result.keywords.length} 个关键词`);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '扩展失败');
    } finally {
      setExpanding(false);
    }
  };

  return (
    <div>
      {!llmConfigured && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="LLM 未配置或未启用，混合模式将仅使用词库匹配。"
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
      <Form.Item
        name={['ruleConfig', 'semantic', 'fieldCode']}
        label="检测字段"
        rules={[{ required: true, message: '请选择文本字段' }]}
      >
        <Select
          placeholder="选择文本字段"
          options={textFieldOptions}
          showSearch
          optionFilterProp="label"
          disabled={!metaTableId}
        />
      </Form.Item>
      <Form.Item
        name={['ruleConfig', 'semantic', 'policy']}
        label="合规策略"
        rules={[{ required: true, message: '请描述禁止内容' }]}
      >
        <Input.TextArea
          rows={3}
          placeholder="如：备注中不得包含烟酒相关内容，包括茅台、五粮液等品牌…"
          onBlur={runValidate}
        />
      </Form.Item>
      <Form.Item name={['ruleConfig', 'semantic', 'hintKeywords']} label="示例词（可选）">
        <Select mode="tags" placeholder="输入后回车，如：烟、酒、茅台" tokenSeparators={[',', '，']} />
      </Form.Item>
      <Form.Item
        name={['ruleConfig', 'semantic', 'matchMode']}
        label="判定方式"
        initialValue="HYBRID"
      >
        <Radio.Group>
          <Radio value="HYBRID">混合（词库 + LLM）</Radio>
          <Radio value="KEYWORD">仅词库</Radio>
          <Radio value="LLM">仅 LLM</Radio>
        </Radio.Group>
      </Form.Item>
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
      <Form.Item name={['ruleConfig', 'semantic', 'expandedKeywords']} hidden>
        <HiddenFieldHolder />
      </Form.Item>
      <Form.Item noStyle shouldUpdate>
        {() => {
          const expanded = form.getFieldValue(['ruleConfig', 'semantic', 'expandedKeywords']) as
            | string[]
            | undefined;
          if (!expanded?.length) {
            return null;
          }
          return (
            <div style={{ marginBottom: 8 }}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                扩展词库：
              </Typography.Text>
              {expanded.slice(0, 12).map((k) => (
                <Tag key={k} style={{ marginTop: 4 }}>
                  {k}
                </Tag>
              ))}
              {expanded.length > 12 && <Tag>+{expanded.length - 12}</Tag>}
            </div>
          );
        }}
      </Form.Item>
    </div>
  );
}
