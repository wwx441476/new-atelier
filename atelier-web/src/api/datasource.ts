import { deleteData, getData, postData } from './client';
import type { DataSourceRequest, DataSourceResponse, TestConnectionResult } from './types';

export const datasourceApi = {
  list: () => getData<DataSourceResponse[]>('/datasources'),
  get: (id: string) => getData<DataSourceResponse>(`/datasources/${id}`),
  save: (data: DataSourceRequest) => postData<DataSourceResponse>('/datasources', data),
  delete: (id: string) => deleteData<void>(`/datasources/${id}`),
  test: (data: DataSourceRequest) =>
    postData<TestConnectionResult>('/datasources/test', data),
};
