import { getData, postData, putData } from './client';
import type { SemanticLlmConfigRequest, SemanticLlmConfigResponse } from './types';

export const settingsApi = {
  getSemanticLlm: () => getData<SemanticLlmConfigResponse>('/settings/semantic-llm'),
  saveSemanticLlm: (data: SemanticLlmConfigRequest) =>
    putData<SemanticLlmConfigResponse>('/settings/semantic-llm', data),
  testSemanticLlm: (data: SemanticLlmConfigRequest) =>
    postData<{ success: boolean; message: string }>('/settings/semantic-llm/test', data),
};
