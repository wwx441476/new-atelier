import { getData, postData, putData } from './client';
import type {
  SemanticLlmConfigRequest,
  SemanticLlmConfigResponse,
  SemanticLlmProfileRequest,
  SemanticLlmProfilesResponse,
  SemanticLlmProfilesSaveRequest,
} from './types';

export const settingsApi = {
  getSemanticLlm: () => getData<SemanticLlmConfigResponse>('/settings/semantic-llm'),
  saveSemanticLlm: (data: SemanticLlmConfigRequest) =>
    putData<SemanticLlmConfigResponse>('/settings/semantic-llm', data),
  testSemanticLlm: (data: SemanticLlmConfigRequest) =>
    postData<{ success: boolean; message: string }>('/settings/semantic-llm/test', data),
  getLlmProfiles: () => getData<SemanticLlmProfilesResponse>('/settings/llm-profiles'),
  saveLlmProfiles: (data: SemanticLlmProfilesSaveRequest) =>
    putData<SemanticLlmProfilesResponse>('/settings/llm-profiles', data),
  testLlmProfile: (data: SemanticLlmProfileRequest) =>
    postData<{ success: boolean; message: string }>('/settings/llm-profiles/test', data),
};
