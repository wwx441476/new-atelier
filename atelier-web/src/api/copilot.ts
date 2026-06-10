import { postData } from './client';
import type { CopilotChatRequest, CopilotChatResponse } from './types';

export const copilotApi = {
  chat: (request: CopilotChatRequest) => postData<CopilotChatResponse>('/copilot/chat', request),
};
