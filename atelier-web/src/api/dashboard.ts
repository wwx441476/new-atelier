import client, { deleteData, getData, postData } from './client';
import type {
  ApiResponse,
  DashboardGenerateRequest,
  DashboardGenerateResponse,
  DashboardScreen,
} from './types';

export const dashboardApi = {
  list: () => getData<DashboardScreen[]>('/dashboards'),
  getById: (id: string) => getData<DashboardScreen>(`/dashboards/${id}`),
  getByCode: (code: string) => getData<DashboardScreen>(`/dashboards/by-code/${code}`),
  save: (data: DashboardScreen) => postData<DashboardScreen>('/dashboards', data),
  delete: (code: string) => deleteData<void>(`/dashboards/${code}`),
  generate: async (request: DashboardGenerateRequest) => {
    const hasImages = request.images && request.images.length > 0;
    const res = await client.post<ApiResponse<DashboardGenerateResponse>>(
      '/dashboards/generate',
      request,
      { timeout: hasImages ? 120000 : 90000 },
    );
    return res.data.data;
  },
};
