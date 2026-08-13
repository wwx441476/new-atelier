import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Tag,
  message,
} from 'antd';
import { ApiOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { settingsApi } from '../api/settings';
import type { SemanticLlmProfileRequest, SemanticLlmProfileResponse } from '../api/types';
import {
  LLM_PROTOCOL_OPTIONS,
  LLM_PROVIDER_PRESETS,
  isKimiCodingBaseUrl,
  resolveLlmProviderPreset,
  resolveModelForBaseUrl,
  resolveProtocol,
  type LlmProtocolId,
  type LlmProviderId,
} from '../utils/llmProviderPresets';
import { notifySemanticLlmUpdated } from '../utils/semanticLlmEvents';

interface SemanticLlmSettingsModalProps {
  open: boolean;
  onClose: () => void;
  onSaved?: () => void;
}

interface ProfileFormValues extends SemanticLlmProfileRequest {
  provider?: LlmProviderId;
  protocol?: LlmProtocolId;
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

function createEmptyProfile(): ProfileFormValues {
  return {
    name: '新配置',
    enabled: true,
    provider: 'custom',
    protocol: 'anthropic',
    model: '',
    baseUrl: '',
    timeoutSeconds: 180,
  };
}

function toFormValues(profile: SemanticLlmProfileResponse): ProfileFormValues {
  const provider = inferProvider(profile.baseUrl, profile.provider);
  const preset = resolveLlmProviderPreset(provider);
  return {
    id: profile.id,
    name: profile.name,
    enabled: profile.enabled,
    provider,
    protocol: resolveProtocol(profile.protocol, provider, profile.baseUrl),
    model: resolveModelForBaseUrl(profile.baseUrl, profile.model) || preset.model,
    baseUrl: profile.baseUrl || preset.baseUrl,
    timeoutSeconds: profile.timeoutSeconds ?? 180,
  };
}

export default function SemanticLlmSettingsModal({ open, onClose, onSaved }: SemanticLlmSettingsModalProps) {
  const [form] = Form.useForm<ProfileFormValues>();
  const [loading, setLoading] = useState(false);
  const [testing, setTesting] = useState(false);
  const [profiles, setProfiles] = useState<SemanticLlmProfileResponse[]>([]);
  const [activeProfileId, setActiveProfileId] = useState<string>();
  const [selectedProfileId, setSelectedProfileId] = useState<string>();
  const [apiKeyConfigured, setApiKeyConfigured] = useState(false);
  const selectedProvider = Form.useWatch('provider', form) as LlmProviderId | undefined;
  const selectedProtocol = Form.useWatch('protocol', form) as LlmProtocolId | undefined;
  const baseUrl = Form.useWatch('baseUrl', form) as string | undefined;

  const selectedProfile = useMemo(
    () => profiles.find((profile) => profile.id === selectedProfileId),
    [profiles, selectedProfileId],
  );

  useEffect(() => {
    if (!open) {
      return;
    }
    settingsApi
      .getLlmProfiles()
      .then((data) => {
        setProfiles(data.profiles);
        setActiveProfileId(data.activeProfileId);
        const nextSelected = data.activeProfileId || data.profiles[0]?.id;
        setSelectedProfileId(nextSelected);
        const profile = data.profiles.find((item) => item.id === nextSelected);
        if (profile) {
          setApiKeyConfigured(profile.apiKeyConfigured);
          form.setFieldsValue(toFormValues(profile));
        }
      })
      .catch((err) => {
        message.error(err instanceof Error ? err.message : '加载 LLM 配置失败');
      });
  }, [open, form]);

  const handleSelectProfile = (profileId: string) => {
    const profile = profiles.find((item) => item.id === profileId);
    if (!profile) {
      return;
    }
    setSelectedProfileId(profileId);
    setApiKeyConfigured(profile.apiKeyConfigured);
    form.setFieldsValue(toFormValues(profile));
  };

  const handleProviderChange = (provider: LlmProviderId) => {
    const preset = LLM_PROVIDER_PRESETS[provider];
    if (provider === 'custom') {
      form.setFieldsValue({ protocol: preset.protocol });
      return;
    }
    form.setFieldsValue({
      model: preset.model,
      baseUrl: preset.baseUrl,
      protocol: preset.protocol,
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

  const normalizeProfileId = (id?: string) => (id?.startsWith('draft-') ? undefined : id);

  const normalizeProfile = (values: ProfileFormValues): SemanticLlmProfileRequest => {
    const provider = inferProvider(values.baseUrl, values.provider);
    const protocol = resolveProtocol(values.protocol, provider, values.baseUrl);
    return {
      id: normalizeProfileId(values.id),
      name: values.name?.trim() || '未命名',
      enabled: values.enabled,
      provider: provider === 'kimi-coding' ? 'kimi-coding' : values.provider,
      protocol,
      apiKey: values.apiKey,
      model: resolveModelForBaseUrl(values.baseUrl, values.model) || values.model,
      baseUrl: values.baseUrl,
      timeoutSeconds: values.timeoutSeconds,
    };
  };

  const buildSavePayload = async () => {
    const current = normalizeProfile(await form.validateFields());
    const mergedProfiles = profiles.map((profile) => {
      if (profile.id === current.id || profile.id === form.getFieldValue('id')) {
        return current;
      }
      return {
        id: normalizeProfileId(profile.id),
        name: profile.name,
        enabled: profile.enabled,
        provider: profile.provider,
        protocol: profile.protocol,
        model: profile.model,
        baseUrl: profile.baseUrl,
        timeoutSeconds: profile.timeoutSeconds,
      } satisfies SemanticLlmProfileRequest;
    });
    const hasCurrent = mergedProfiles.some(
      (profile) => profile.id && profile.id === current.id,
    );
    const nextProfiles = hasCurrent ? mergedProfiles : [...mergedProfiles, current];
    const nextActiveId = activeProfileId?.startsWith('draft-') ? undefined : activeProfileId;
    return {
      activeProfileId: nextActiveId || current.id,
      profiles: nextProfiles,
    };
  };

  const handleSave = async () => {
    setLoading(true);
    try {
      const payload = await buildSavePayload();
      const saved = await settingsApi.saveLlmProfiles(payload);
      setProfiles(saved.profiles);
      setActiveProfileId(saved.activeProfileId);
      message.success('LLM 配置已保存');
      notifySemanticLlmUpdated();
      onSaved?.();
      onClose();
    } finally {
      setLoading(false);
    }
  };

  const handleTest = async () => {
    const values = normalizeProfile(form.getFieldsValue());
    setTesting(true);
    try {
      const result = await settingsApi.testLlmProfile(values);
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

  const handleAddProfile = () => {
    const draft = createEmptyProfile();
    const tempId = `draft-${Date.now()}`;
    const nextProfile: SemanticLlmProfileResponse = {
      id: tempId,
      name: draft.name || '新配置',
      enabled: true,
      provider: draft.provider,
      protocol: draft.protocol,
      model: draft.model,
      baseUrl: draft.baseUrl,
      timeoutSeconds: draft.timeoutSeconds,
      apiKeyConfigured: false,
    };
    setProfiles((prev) => [...prev, nextProfile]);
    setSelectedProfileId(tempId);
    setApiKeyConfigured(false);
    form.setFieldsValue({ ...draft, id: tempId });
  };

  const handleDeleteProfile = () => {
    if (!selectedProfileId || profiles.length <= 1) {
      message.warning('至少保留一套 LLM 配置');
      return;
    }
    const nextProfiles = profiles.filter((profile) => profile.id !== selectedProfileId);
    setProfiles(nextProfiles);
    const nextActive = activeProfileId === selectedProfileId
      ? nextProfiles[0]?.id
      : activeProfileId;
    setActiveProfileId(nextActive);
    handleSelectProfile(nextActive || nextProfiles[0].id);
  };

  const preset = resolveLlmProviderPreset(selectedProvider);
  const isKimiCoding = selectedProvider === 'kimi-coding' || isKimiCodingBaseUrl(baseUrl);

  return (
    <Modal
      title="语义检测设置"
      open={open}
      onCancel={onClose}
      width={760}
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
        message="可配置多套 LLM。自定义网关请按 CC Switch 选择协议（Anthropic / OpenAI）。Read timed out 多为超时过短，OCR/长文建议 ≥180 秒；一般不必改流式。"
      />
      <div style={{ display: 'flex', gap: 16, minHeight: 360 }}>
        <div style={{ width: 220, flexShrink: 0 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
            <strong>配置列表</strong>
            <Button type="link" size="small" icon={<PlusOutlined />} onClick={handleAddProfile}>
              添加
            </Button>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {profiles.map((profile) => (
              <button
                key={profile.id}
                type="button"
                onClick={() => handleSelectProfile(profile.id)}
                style={{
                  textAlign: 'left',
                  border:
                    profile.id === selectedProfileId
                      ? '1px solid #1677ff'
                      : '1px solid #d9d9d9',
                  borderRadius: 8,
                  padding: '10px 12px',
                  background: profile.id === selectedProfileId ? '#f0f7ff' : '#fff',
                  cursor: 'pointer',
                }}
              >
                <div style={{ fontWeight: 600 }}>{profile.name}</div>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                  {profile.model || '未设置模型'}
                </div>
                <div style={{ marginTop: 6, display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                  {profile.id === activeProfileId && <Tag color="blue">工作区默认</Tag>}
                  {!profile.enabled && <Tag>未启用</Tag>}
                </div>
              </button>
            ))}
          </div>
        </div>
        <div style={{ flex: 1 }}>
          {isKimiCoding && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
              message="Kimi Coding Plan 与 cc switch 相同：API 地址 https://api.kimi.com/coding，模型 kimi-k2.6。"
            />
          )}
          <Form
            form={form}
            layout="vertical"
            initialValues={{
              provider: 'custom',
              protocol: 'anthropic',
              timeoutSeconds: 180,
              enabled: true,
            }}
          >
            <Form.Item name="id" hidden>
              <Input />
            </Form.Item>
            <Form.Item
              name="name"
              label="配置名称"
              rules={[{ required: true, message: '请输入配置名称' }]}
            >
              <Input placeholder="如：用友 AI Token、Kimi Coding" />
            </Form.Item>
            <Form.Item label="工作区默认">
              <Switch
                checked={selectedProfileId === activeProfileId}
                onChange={(checked) => {
                  if (checked && selectedProfileId) {
                    setActiveProfileId(selectedProfileId);
                  }
                }}
              />
              <span style={{ marginLeft: 8, color: '#8c8c8c', fontSize: 12 }}>
                语义合规规则默认使用此配置
              </span>
            </Form.Item>
            <Form.Item name="enabled" label="启用 LLM" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="provider" label="服务商">
              <Select options={PROVIDER_OPTIONS} onChange={handleProviderChange} />
            </Form.Item>
            <Form.Item
              name="protocol"
              label="协议类型"
              extra={
                LLM_PROTOCOL_OPTIONS.find((item) => item.value === selectedProtocol)?.hint ||
                '与 CC Switch 顶部 Anthropic / OpenAI 切换对应'
              }
              rules={[{ required: true, message: '请选择协议' }]}
            >
              <Select
                options={LLM_PROTOCOL_OPTIONS.map((item) => ({
                  label: item.label,
                  value: item.value,
                }))}
                disabled={selectedProvider !== 'custom' && selectedProvider !== undefined}
              />
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
              <Input placeholder={preset.model || '如 kimi-k2.7-code'} />
            </Form.Item>
            <Form.Item
              name="baseUrl"
              label="API 地址"
              extra={
                selectedProtocol === 'anthropic'
                  ? '示例：https://aitoken.yonyougov.top（将请求 /v1/messages）'
                  : '示例：https://aitoken.yonyougov.top/v1（将请求 /v1/chat/completions）'
              }
            >
              <Input placeholder={preset.baseUrl || 'https://aitoken.yonyougov.top'} onBlur={handleBaseUrlBlur} />
            </Form.Item>
            <Form.Item
              name="timeoutSeconds"
              label="超时（秒）"
              extra="Read timed out 时优先增大此项；OCR/长文档建议 180–300"
            >
              <InputNumber min={5} max={600} style={{ width: '100%' }} />
            </Form.Item>
          </Form>
          {selectedProfile && (
            <Button
              danger
              type="text"
              icon={<DeleteOutlined />}
              onClick={handleDeleteProfile}
              disabled={profiles.length <= 1}
            >
              删除当前配置
            </Button>
          )}
        </div>
      </div>
    </Modal>
  );
}
