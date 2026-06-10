export type LlmProviderId = 'openai' | 'dashscope' | 'kimi' | 'kimi-coding' | 'custom';

export interface LlmProviderPreset {
  label: string;
  baseUrl: string;
  model: string;
  apiKeyPlaceholder?: string;
}

export const LLM_PROVIDER_PRESETS: Record<LlmProviderId, LlmProviderPreset> = {
  openai: {
    label: 'OpenAI',
    baseUrl: 'https://api.openai.com/v1',
    model: 'gpt-4o-mini',
  },
  dashscope: {
    label: '通义千问',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    model: 'qwen-turbo',
  },
  kimi: {
    label: 'Kimi',
    baseUrl: 'https://api.moonshot.cn/v1',
    model: 'kimi-k2.6',
    apiKeyPlaceholder: 'sk-…（开放平台或 Coding Plan 密钥）',
  },
  'kimi-coding': {
    label: 'Kimi Coding',
    baseUrl: 'https://api.kimi.com/coding',
    model: 'kimi-k2.6',
    apiKeyPlaceholder: 'sk-kimi-…（Coding Plan 密钥，与 cc switch 相同）',
  },
  custom: {
    label: '自定义',
    baseUrl: '',
    model: '',
  },
};

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
