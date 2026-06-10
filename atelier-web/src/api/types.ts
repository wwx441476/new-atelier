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
  connectionProperties?: Record<string, string>;
}

export interface DataSourceResponse {
  id: string;
  name: string;
  jdbcUrl: string;
  username: string;
  dbType: string;
  enabled: boolean;
  connectionProperties?: Record<string, string>;
}

export interface TestConnectionResult {
  success: boolean;
  message: string;
}

export interface DbSchemaInfo {
  name: string;
  catalog?: string;
}

export interface DbTableInfo {
  schema?: string;
  name: string;
  type?: string;
  remarks?: string;
}

export interface DbColumnInfo {
  name: string;
  typeName?: string;
  columnSize?: number;
  decimalDigits?: number;
  nullable?: boolean;
  remarks?: string;
  ordinalPosition?: number;
}

export interface DbBrowseQueryRequest {
  sql?: string;
  filters?: FilterConditionDto[];
  filterGroups?: FilterGroupDto[];
  pageIndex?: number;
  pageSize?: number;
}

export interface MetaTable {
  id?: string;
  catalogCode?: string;
  tableCode: string;
  tableName: string;
  datasourceId: string;
  schemaCode?: string;
  comments?: string;
  fields?: MetaTableField[];
}

export interface MetaTableDdlResult {
  ddl: string;
  alterDdl?: string;
  missingFieldCodes?: string[];
  syncNeeded?: boolean;
  tableExists: boolean;
  datasourceId: string;
  tableCode: string;
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
export type DimensionValueSource = 'MANUAL' | 'TABLE';

export interface DimensionField {
  id?: string;
  dimensionId?: string;
  fieldCode: string;
  fieldName?: string;
  fieldType?: string;
  codeField?: boolean;
  nameField?: boolean;
  parentField?: boolean;
  sort?: number;
}

export interface Dimension {
  id?: string;
  catalogCode?: string;
  code: string;
  name: string;
  type: DimensionType;
  datasourceId: string;
  metaTableId?: string;
  valueSource?: DimensionValueSource;
  comments?: string;
  fields?: DimensionField[];
}

export type TimeGranularity = 'YEAR' | 'QUARTER' | 'MONTH';

export interface TimeValueGenerateRequest {
  granularity: TimeGranularity;
  startYear: number;
  endYear: number;
  startMonth?: number;
  endMonth?: number;
  codeFormat: string;
  nameFormat: string;
  skipExisting?: boolean;
}

export interface TimeValueGenerateResult {
  generated: number;
  skipped: number;
  values: DimensionValue[];
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

export interface FilterConditionDto {
  field: string;
  operator: string;
  values: string[];
}

export interface FilterGroupDto {
  conditions: FilterConditionDto[];
}

export interface MetricQueryRequest {
  metricCodes: string[];
  filters?: FilterConditionDto[];
  filterGroups?: FilterGroupDto[];
  pageIndex?: number;
  pageSize?: number;
}

export interface QueryResult {
  total: number;
  rows: Record<string, unknown>[];
  headers?: Record<string, string>;
  sql?: string;
}

export interface SqlPreviewResult {
  sql: string;
  datasourceId: string;
  columns: string[] | Record<string, string>;
}

export interface ExpressionValidateResult {
  valid: boolean;
  normalizedExpression?: string;
  usedVariables?: string[];
  unknownVariables?: string[];
  unusedMetrics?: string[];
  message?: string;
  sampleEvaluated?: boolean;
  sampleTriggered?: boolean;
}

export interface WarningRule {
  id?: string;
  catalogCode?: string;
  code?: string;
  name: string;
  ruleType?: WarningRuleType;
  metricCodes?: string[];
  expression?: string;
  ruleConfig?: CompositeRuleConfig;
  enabled?: boolean;
  warningLevel?: number;
  notifyConfig?: string;
  comments?: string;
}

export type WarningRuleType = 'METRIC' | 'SEMANTIC' | 'COMPOSITE';

export type SemanticCheckMode = 'VIOLATION' | 'REQUIREMENT';

export interface SemanticFieldCheck {
  fieldCode?: string;
  checkMode?: SemanticCheckMode;
  policy?: string;
  hintKeywords?: string[];
  matchMode?: 'KEYWORD' | 'LLM' | 'HYBRID';
  expandedKeywords?: string[];
}

export interface SemanticCheckGroup {
  checks?: SemanticFieldCheck[];
}

export interface SemanticRuleConfig {
  metaTableId?: string;
  semanticGroups?: SemanticCheckGroup[];
  /** @deprecated 兼容旧配置 */
  fieldCode?: string;
  policy?: string;
  hintKeywords?: string[];
  matchMode?: 'KEYWORD' | 'LLM' | 'HYBRID';
  expandedKeywords?: string[];
}

export interface CompositeRuleConfig {
  triggerLogic?: 'AND' | 'OR';
  semantic?: SemanticRuleConfig;
}

export interface SemanticSampleCheckResult {
  fieldCode?: string;
  checkMode?: SemanticCheckMode;
  subConditionMet?: boolean;
  reason?: string;
  layer?: string;
  llmInvoked?: boolean;
}

export interface SemanticValidateResult {
  valid: boolean;
  message?: string;
  sampleTriggered?: boolean;
  sampleMatchReason?: string;
  sampleMatchLayer?: string;
  sampleChecks?: SemanticSampleCheckResult[];
}

export interface SemanticLlmConfigResponse {
  enabled: boolean;
  provider?: string;
  model?: string;
  baseUrl?: string;
  timeoutSeconds?: number;
  apiKeyConfigured: boolean;
}

export interface SemanticLlmConfigRequest {
  enabled?: boolean;
  provider?: string;
  apiKey?: string;
  model?: string;
  baseUrl?: string;
  timeoutSeconds?: number;
}

export interface WarningRulePreviewRequest {
  pageIndex?: number;
  pageSize?: number;
  filters?: FilterConditionDto[];
  filterGroups?: FilterGroupDto[];
  /** 为 true 时仅用词库预览，不调用 LLM（默认 true） */
  keywordOnly?: boolean;
}

export interface WarningRulePreviewResult {
  ruleId: string;
  ruleName: string;
  expression: string;
  sql?: string;
  total: number;
  matchedCount: number;
  rows: Record<string, unknown>[];
  headers?: Record<string, string>;
}

export interface ConfigDataSource {
  id: string;
  name: string;
  jdbcUrl: string;
  username: string;
  password?: string;
  dbType: string;
  enabled?: boolean;
  connectionProperties?: Record<string, string>;
}

export interface AtelierConfigBundle {
  version?: string;
  exportedAt?: string;
  datasources?: ConfigDataSource[];
  metadataTables?: Array<{ table: MetaTable; fields?: MetaTableField[] }>;
  dimensions?: Array<{
    dimension: Dimension;
    fields?: DimensionField[];
    values?: DimensionValue[];
  }>;
  metrics?: MetricDefinition[];
  warningRules?: WarningRule[];
}

export interface ConfigImportOptions {
  importDatasources?: boolean;
  importMetadata?: boolean;
  importDimensions?: boolean;
  importMetrics?: boolean;
  importWarningRules?: boolean;
}

export interface ConfigImportResult {
  imported?: Record<string, number>;
  skipped?: Record<string, number>;
  message?: string;
}
