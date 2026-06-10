import { deleteData, getData, postData } from './client';
import type {
  DataSourceRequest,
  DataSourceResponse,
  DbBrowseExecuteRequest,
  DbBrowseQueryRequest,
  DbCreateTableRequest,
  DbColumnInfo,
  DbSchemaInfo,
  DbTableInfo,
  QueryResult,
  SqlExecuteResult,
  TestConnectionResult,
} from './types';

export const datasourceApi = {
  list: () => getData<DataSourceResponse[]>('/datasources'),
  get: (id: string) => getData<DataSourceResponse>(`/datasources/${id}`),
  save: (data: DataSourceRequest) => postData<DataSourceResponse>('/datasources', data),
  delete: (id: string) => deleteData<void>(`/datasources/${id}`),
  test: (data: DataSourceRequest) =>
    postData<TestConnectionResult>('/datasources/test', data),
  browseSchemas: (id: string) => getData<DbSchemaInfo[]>(`/datasources/${id}/browse/schemas`),
  browseTables: (id: string, schema?: string) =>
    getData<DbTableInfo[]>(`/datasources/${id}/browse/tables`, schema ? { schema } : undefined),
  browseColumns: (id: string, table: string, schema?: string) =>
    getData<DbColumnInfo[]>(`/datasources/${id}/browse/tables/${table}/columns`, schema ? { schema } : undefined),
  browsePreview: (id: string, table: string, schema?: string, pageIndex = 1, pageSize = 20) =>
    getData<QueryResult>(`/datasources/${id}/browse/tables/${table}/preview`, {
      ...(schema ? { schema } : {}),
      pageIndex,
      pageSize,
    }),
  browseExecuteSql: (id: string, data: DbBrowseQueryRequest) =>
    postData<QueryResult>(`/datasources/${id}/browse/query`, data),
  browseExecuteWriteSql: (id: string, data: DbBrowseExecuteRequest) =>
    postData<SqlExecuteResult>(`/datasources/${id}/browse/execute`, data),
  browseCreateTable: (id: string, data: DbCreateTableRequest) =>
    postData<SqlExecuteResult>(`/datasources/${id}/browse/tables/create`, data),
  browseTableQuery: (id: string, table: string, schema: string | undefined, data: DbBrowseQueryRequest) =>
    postData<QueryResult>(
      `/datasources/${id}/browse/tables/${table}/query${schema ? `?schema=${encodeURIComponent(schema)}` : ''}`,
      data,
    ),
};
