import client, { deleteData, getData, postData } from './client';
import type {
  ApiResponse,
  CopilotActivePlan,
  CopilotChatRequest,
  CopilotChatResponse,
  CopilotPlaybook,
  CopilotTranscribeRequest,
  CopilotTranscribeResponse,
} from './types';

export const copilotApi = {
  chat: async (request: CopilotChatRequest) => {
    const hasImages = request.messages.some(
      (message) => message.role === 'user' && message.images && message.images.length > 0,
    );
    const res = await client.post<ApiResponse<CopilotChatResponse>>('/copilot/chat', request, {
      timeout: hasImages ? 120000 : 60000,
    });
    return res.data.data;
  },
  transcribe: async (request: CopilotTranscribeRequest) => {
    const res = await client.post<ApiResponse<CopilotTranscribeResponse>>(
      '/copilot/transcribe',
      request,
      { timeout: 90000 },
    );
    return res.data.data;
  },
  listPlaybooks: () => getData<CopilotPlaybook[]>('/copilot/playbooks'),
  savePlaybook: (data: CopilotPlaybook) => postData<CopilotPlaybook>('/copilot/playbooks', data),
  savePlaybookFromPlan: (data: {
    code?: string;
    name: string;
    description?: string;
    triggerKeywords?: string[];
    plan: CopilotActivePlan;
  }) => postData<CopilotPlaybook>('/copilot/playbooks/from-plan', data),
  activatePlaybook: (id: string) => postData<CopilotActivePlan>(`/copilot/playbooks/${id}/activate`),
  deletePlaybook: (code: string) => deleteData<void>(`/copilot/playbooks/${code}`),
};
