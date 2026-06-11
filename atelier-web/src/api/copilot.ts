import client from './client';
import type { ApiResponse, CopilotChatRequest, CopilotChatResponse } from './types';

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
};
