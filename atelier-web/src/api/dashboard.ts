import { deleteData, getData, postData } from './client';
import type { DashboardScreen } from './types';

export const dashboardApi = {
  list: () => getData<DashboardScreen[]>('/dashboards'),
  getById: (id: string) => getData<DashboardScreen>(`/dashboards/${id}`),
  getByCode: (code: string) => getData<DashboardScreen>(`/dashboards/by-code/${code}`),
  save: (data: DashboardScreen) => postData<DashboardScreen>('/dashboards', data),
  delete: (code: string) => deleteData<void>(`/dashboards/${code}`),
};
