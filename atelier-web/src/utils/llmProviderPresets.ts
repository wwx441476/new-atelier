export type LlmProviderId = 'openai' | 'dashscope' | 'kimi' | 'kimi-coding' | 'custom';
export type LlmProtocolId = 'openai' | 'anthropic';

export interface LlmProviderPreset {
  label: string;
  baseUrl: string;
  model: string;
  /** 默认协议；custom 由用户自选 */
  protocol: LlmProtocolId;
  apiKeyPlaceholder?: string;
}

export const LLM_PROVIDER_PRESETS: Record<LlmProviderId, LlmProviderPreset> = {
  openai: {
    label: 'OpenAI',
    baseUrl: 'https://api.openai.com/v1',
    model: 'gpt-4o-mini',
    protocol: 'openai',
  },
  dashscope: {
    label: '通义千问',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    model: 'qwen-turbo',
    protocol: 'openai',
  },
  kimi: {
    label: 'Kimi',
    baseUrl: 'https://api.moonshot.cn/v1',
    model: 'kimi-k2.6',
    protocol: 'openai',
    apiKeyPlaceholder: 'sk-…（开放平台或 Coding Plan 密钥）',
  },
  'kimi-coding': {
    label: 'Kimi Coding',
    baseUrl: 'https://api.kimi.com/coding',
    model: 'kimi-k2.6',
    protocol: 'anthropic',
    apiKeyPlaceholder: 'sk-kimi-…（Coding Plan 密钥，与 cc switch 相同）',
  },
  custom: {
    label: '自定义',
    baseUrl: '',
    model: '',
    protocol: 'anthropic',
  },
};

export const LLM_PROTOCOL_OPTIONS: { label: string; value: LlmProtocolId; hint: string }[] = [
  {
    value: 'anthropic',
    label: 'Anthropic Messages',
    hint: '与 CC Switch Claude 模式一致，请求 /v1/messages',
  },
  {
    value: 'openai',
    label: 'OpenAI Chat Completions',
    hint: '请求 /v1/chat/completions',
  },
];

export function resolveProtocol(
  protocol: string | undefined,
  provider: string | undefined,
  baseUrl?: string,
): LlmProtocolId {
  if (protocol === 'anthropic' || protocol === 'openai') {
    return protocol;
  }
  if (provider === 'kimi-coding' || isKimiCodingBaseUrl(baseUrl)) {
    return 'anthropic';
  }
  const preset = resolveLlmProviderPreset(provider);
  return preset.protocol;
}

export function isKimiCodingBaseUrl(baseUrl?: string): boolean {
  return !!baseUrl && baseUrl.toLowerCase().includes('kimi.com/coding');
}

export function resolveModelForBaseUrl(baseUrl: string | undefined, model: string | undefined): string | undefined {
  if (isKimiCodingBaseUrl(baseUrl) && !model) {
    return 'kimi-k2.6';
  }
  return model;
}

export function resolveLlmProviderPreset(provider?: string): LlmProviderPreset {
  const id = (provider || 'openai') as LlmProviderId;
  return LLM_PROVIDER_PRESETS[id] ?? LLM_PROVIDER_PRESETS.custom;
}
