import { useEffect, useState } from 'react';
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Switch, message } from 'antd';
import { ApiOutlined } from '@ant-design/icons';
import { settingsApi } from '../api/settings';
import type { SemanticLlmConfigRequest } from '../api/types';
import {
  LLM_PROVIDER_PRESETS,
  isKimiCodingBaseUrl,
  resolveLlmProviderPreset,
  resolveModelForBaseUrl,
  type LlmProviderId,
} from '../utils/llmProviderPresets';

interface SemanticLlmSettingsModalProps {
  open: boolean;
  onClose: () => void;
}

const PROVIDER_OPTIONS = (Object.keys(LLM_PROVIDER_PRESETS) as LlmProviderId[]).map((id) => ({
  label: LLM_PROVIDER_PRESETS[id].label,
  value: id,
}));

function inferProvider(baseUrl?: string, provider?: string): LlmProviderId {
  if (provider === 'kimi-coding' || isKimiCodingBaseUrl(baseUrl)) {
    return 'kimi-coding';
  }
  if (provider && provider in LLM_PROVIDER_PRESETS) {
    return provider as LlmProviderId;
  }
  return 'openai';
}

export default function SemanticLlmSettingsModal({ open, onClose }: SemanticLlmSettingsModalProps) {
  const [form] = Form.useForm<SemanticLlmConfigRequest>();
  const [loading, setLoading] = useState(false);
  const [testing, setTesting] = useState(false);
  const [apiKeyConfigured, setApiKeyConfigured] = useState(false);
  const selectedProvider = Form.useWatch('provider', form) as LlmProviderId | undefined;
  const baseUrl = Form.useWatch('baseUrl', form) as string | undefined;

  useEffect(() => {
    if (!open) {
      return;
    }
    settingsApi.getSemanticLlm().then((config) => {
      setApiKeyConfigured(config.apiKeyConfigured);
      const provider = inferProvider(config.baseUrl, config.provider);
      const preset = resolveLlmProviderPreset(provider);
      form.setFieldsValue({
        enabled: config.enabled,
        provider,
        model: resolveModelForBaseUrl(config.baseUrl, config.model) || preset.model,
        baseUrl: config.baseUrl || preset.baseUrl,
        timeoutSeconds: config.timeoutSeconds ?? 30,
      });
    });
  }, [open, form]);

  const handleProviderChange = (provider: LlmProviderId) => {
    const preset = LLM_PROVIDER_PRESETS[provider];
    if (provider === 'custom') {
      return;
    }
    form.setFieldsValue({
      model: preset.model,
      baseUrl: preset.baseUrl,
    });
  };

  const handleBaseUrlBlur = () => {
    const values = form.getFieldsValue();
    if (!isKimiCodingBaseUrl(values.baseUrl)) {
      return;
    }
    if (values.provider !== 'kimi-coding') {
      form.setFieldValue('provider', 'kimi-coding');
    }
    const model = resolveModelForBaseUrl(values.baseUrl, values.model);
    if (model && model !== values.model) {
      form.setFieldValue('model', model);
    }
  };

  const normalizePayload = (values: SemanticLlmConfigRequest): SemanticLlmConfigRequest => {
    const provider = inferProvider(values.baseUrl, values.provider);
    return {
      ...values,
      provider: provider === 'kimi-coding' ? 'kimi-coding' : values.provider,
      model: resolveModelForBaseUrl(values.baseUrl, values.model) || values.model,
    };
  };

  const handleSave = async () => {
    const values = normalizePayload(await form.validateFields());
    setLoading(true);
    try {
      await settingsApi.saveSemanticLlm(values);
      message.success('语义检测 LLM 配置已保存');
      onClose();
    } finally {
      setLoading(false);
    }
  };

  const handleTest = async () => {
    const values = normalizePayload(form.getFieldsValue());
    setTesting(true);
    try {
      const result = await settingsApi.testSemanticLlm(values);
      if (result.success) {
        message.success(result.message || '连接成功');
      } else {
        message.error(result.message || '连接失败');
      }
    } catch (err) {
      message.error(err instanceof Error ? err.message : '连接失败');
    } finally {
      setTesting(false);
    }
  };

  const preset = resolveLlmProviderPreset(selectedProvider);
  const isKimiCoding = selectedProvider === 'kimi-coding' || isKimiCodingBaseUrl(baseUrl);

  return (
    <Modal
      title="语义检测设置"
      open={open}
      onCancel={onClose}
      width={560}
      footer={
        <Space>
          <Button icon={<ApiOutlined />} loading={testing} onClick={handleTest}>
            测试连接
          </Button>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={loading} onClick={handleSave}>
            保存
          </Button>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="用于语义合规规则的关键词扩展与 LLM 判定。API Key 不会明文展示，留空表示不修改。"
      />
      {isKimiCoding && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="Kimi Coding Plan 与 cc switch 相同：API 地址 https://api.kimi.com/coding，模型 kimi-k2.6，走 Anthropic 协议。"
        />
      )}
      <Form form={form} layout="vertical" initialValues={{ provider: 'openai', timeoutSeconds: 30 }}>
        <Form.Item name="enabled" label="启用 LLM" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="provider" label="服务商">
          <Select options={PROVIDER_OPTIONS} onChange={handleProviderChange} />
        </Form.Item>
        <Form.Item name="apiKey" label="API Key">
          <Input.Password
            placeholder={
              apiKeyConfigured
                ? '留空保持不变'
                : preset.apiKeyPlaceholder || '请输入 API Key'
            }
          />
        </Form.Item>
        <Form.Item name="model" label="模型">
          <Input placeholder={preset.model || '模型名称'} />
        </Form.Item>
        <Form.Item name="baseUrl" label="API 地址">
          <Input placeholder={preset.baseUrl || 'https://…'} onBlur={handleBaseUrlBlur} />
        </Form.Item>
        <Form.Item name="timeoutSeconds" label="超时（秒）">
          <InputNumber min={5} max={120} style={{ width: '100%' }} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
