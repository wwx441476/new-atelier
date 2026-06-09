export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface DataSourceRequest {
  id: string;
  name: string;
  jdbcUrl: string;
  username: string;
  password?: string;
  dbType: string;
  enabled?: boolean;
}

export interface DataSourceResponse {
  id: string;
  name: string;
  jdbcUrl: string;
  username: string;
  dbType: string;
  enabled: boolean;
}

export interface TestConnectionResult {
  success: boolean;
  message: string;
}

export interface MetaTable {
  id?: string;
  catalogCode?: string;
  tableCode: string;
  tableName: string;
  datasourceId: string;
  comments?: string;
  fields?: MetaTableField[];
}

export interface MetaTableField {
  id?: string;
  tableId?: string;
  fieldCode: string;
  fieldName: string;
  fieldType: string;
  fieldLength?: number;
  fieldPrecision?: number;
  nullable?: boolean;
  sort?: number;
}

export type DimensionType = 'LIST' | 'TREE' | 'TIME_DIM';

export interface Dimension {
  id?: string;
  catalogCode?: string;
  code: string;
  name: string;
  type: DimensionType;
  datasourceId: string;
  metaTableId?: string;
  comments?: string;
}

export interface DimensionValue {
  id?: string;
  dimensionId?: string;
  code: string;
  name: string;
  parentCode?: string;
  sort?: number;
}

export type MetricType = 'TABLE' | 'SQL' | 'COMPOSITE';
export type AggregationType = 'NONE' | 'SUM' | 'COUNT' | 'AVG' | 'MAX' | 'MIN';

export interface DimensionBinding {
  dimensionCode: string;
  fieldCode: string;
  fieldName?: string;
  sort?: number;
}

export interface MetricDefinition {
  code: string;
  name: string;
  catalogCode?: string;
  type: MetricType;
  datasourceId: string;
  modelCode?: string;
  tableCode?: string;
  fieldCode?: string;
  fieldName?: string;
  expression?: string;
  datasetSql?: string;
  formula?: string;
  aggregation?: AggregationType;
  alias?: string;
  format?: string;
  unit?: string;
  description?: string;
  dimensions?: DimensionBinding[];
  displayFields?: string[];
}

export interface MetricQueryRequest {
  metricCodes: string[];
  filters?: Array<{
    field: string;
    operator: string;
    values: string[];
  }>;
  pageIndex?: number;
  pageSize?: number;
}

export interface QueryResult {
  total: number;
  rows: Record<string, unknown>[];
  headers?: Record<string, string>;
}

export interface SqlPreviewResult {
  sql: string;
  datasourceId: string;
  columns: string[];
}

export interface WarningRule {
  id?: string;
  catalogCode?: string;
  code?: string;
  name: string;
  metricCodes: string[];
  expression: string;
  enabled?: boolean;
  warningLevel?: number;
  notifyConfig?: string;
  comments?: string;
}

export interface WarningRulePreviewResult {
  ruleId: string;
  ruleName: string;
  expression: string;
  total: number;
  matchedCount: number;
  rows: Record<string, unknown>[];
  headers?: Record<string, string>;
}
