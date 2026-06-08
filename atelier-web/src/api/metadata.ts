import { deleteData, getData, postData } from './client';
import type { MetaTable, MetaTableField, QueryResult } from './types';

export const metadataApi = {
  listTables: (datasourceId?: string) =>
    getData<MetaTable[]>('/metadata/tables', datasourceId ? { datasourceId } : undefined),
  getTable: (id: string) => getData<MetaTable>(`/metadata/tables/${id}`),
  saveTable: (data: MetaTable) => postData<MetaTable>('/metadata/tables', data),
  deleteTable: (id: string) => deleteData<void>(`/metadata/tables/${id}`),
  listFields: (tableId: string) =>
    getData<MetaTableField[]>(`/metadata/tables/${tableId}/fields`),
  saveField: (tableId: string, data: MetaTableField) =>
    postData<MetaTableField>(`/metadata/tables/${tableId}/fields`, data),
  deleteField: (fieldId: string) => deleteData<void>(`/metadata/fields/${fieldId}`),
  discover: (datasourceId: string) =>
    postData<MetaTable[]>(`/metadata/discover?datasourceId=${datasourceId}`),
  previewTable: (tableId: string, pageIndex = 1, pageSize = 20) =>
    getData<QueryResult>(`/metadata/tables/${tableId}/preview`, { pageIndex, pageSize }),
};
