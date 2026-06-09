import { deleteData, getData, postData } from './client';
import type {
  MetricDefinition,
  MetricQueryRequest,
  QueryResult,
  SqlPreviewResult,
} from './types';

export const metricApi = {
  listDefinitions: () => getData<MetricDefinition[]>('/metrics/definitions'),
  getDefinition: (code: string) => getData<MetricDefinition>(`/metrics/definitions/${code}`),
  saveDefinition: (data: MetricDefinition) =>
    postData<MetricDefinition>('/metrics/definitions', data),
  deleteDefinition: (code: string) => deleteData<void>(`/metrics/definitions/${code}`),
  previewSql: (code: string) => getData<SqlPreviewResult>(`/metrics/${code}/sql`),
  previewQuerySql: (data: MetricQueryRequest) =>
    postData<SqlPreviewResult>('/metrics/sql/preview', data),
  query: (data: MetricQueryRequest) => postData<QueryResult>('/metrics/query', data),
};
