import client from './client';
import type {
  ApiResponse,
  CopilotChatRequest,
  CopilotChatResponse,
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
};
