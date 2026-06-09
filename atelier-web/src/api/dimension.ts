import { deleteData, getData, postData } from './client';
import type { Dimension, DimensionValue, TimeValueGenerateRequest, TimeValueGenerateResult } from './types';

export const dimensionApi = {
  list: () => getData<Dimension[]>('/dimensions'),
  get: (id: string) => getData<Dimension>(`/dimensions/${id}`),
  save: (data: Dimension) => postData<Dimension>('/dimensions', data),
  delete: (id: string) => deleteData<void>(`/dimensions/${id}`),
  listValues: (id: string) => getData<DimensionValue[]>(`/dimensions/${id}/values`),
  saveValue: (id: string, data: DimensionValue) =>
    postData<DimensionValue>(`/dimensions/${id}/values`, data),
  deleteValue: (valueId: string) => deleteData<void>(`/dimensions/values/${valueId}`),
  generateTimeValues: (id: string, data: TimeValueGenerateRequest) =>
    postData<TimeValueGenerateResult>(`/dimensions/${id}/values/generate-time`, data),
};
