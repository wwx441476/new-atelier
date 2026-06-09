import { datasourceApi } from '../api/datasource';
import { dimensionApi } from '../api/dimension';
import { metadataApi } from '../api/metadata';
import { metricApi } from '../api/metric';
import { warningApi } from '../api/warning';
import type {
  DataSourceRequest,
  Dimension,
  DimensionValue,
  MetaTable,
  MetaTableField,
  MetricDefinition,
  TimeValueGenerateRequest,
  WarningRule,
} from '../api/types';

export const TUTORIAL = {
  datasourceId: 'ds-learn',
  fallbackDatasourceId: 'ds-demo',
  tableCode: 'orders',
  deptDimCode: 'learn_dept',
  revenueCode: 'learn_revenue',
  warningCode: 'learn_low_profit',
} as const;

export const H2_DEMO_JDBC_URL = 'jdbc:h2:mem:atelier;DB_CLOSE_DELAY=-1;MODE=MySQL';

export interface TutorialContext {
  datasourceId?: string;
  ordersTable?: MetaTable;
  ordersFields: MetaTableField[];
  deptDimension?: Dimension;
  revenueMetric?: MetricDefinition;
}

export type TutorialChain =
  | {
      kind: 'metadata-fields';
      tableId: string;
      fields: MetaTableField[];
      index: number;
    }
  | {
      kind: 'dimension-values';
      dimensionId: string;
      values: DimensionValue[];
      index: number;
    };

export type DemoFillOutcome =
  | { type: 'skip'; message: string }
  | { type: 'form'; values: object; hint: string }
  | { type: 'chain'; chain: TutorialChain };

export const ORDER_FIELDS: MetaTableField[] = [
  { fieldCode: 'dept_code', fieldName: '部门编码', fieldType: 'VARCHAR', sort: 1 },
  { fieldCode: 'fiscal_year', fieldName: '财年', fieldType: 'VARCHAR', sort: 2 },
  { fieldCode: 'amount', fieldName: '金额', fieldType: 'DECIMAL', sort: 3 },
  { fieldCode: 'cost_amount', fieldName: '成本', fieldType: 'DECIMAL', sort: 4 },
];

const DEPT_VALUES: DimensionValue[] = [
  { code: '001', name: '销售部', sort: 1 },
  { code: '002', name: '研发部', sort: 2 },
];

export async function loadTutorialContext(): Promise<TutorialContext> {
  const [datasources, tables, dimensions, metrics] = await Promise.all([
    datasourceApi.list(),
    metadataApi.listTables(),
    dimensionApi.list(),
    metricApi.listDefinitions(),
  ]);

  const datasource =
    datasources.find((item) => item.id === TUTORIAL.datasourceId) ??
    datasources.find((item) => item.id === TUTORIAL.fallbackDatasourceId) ??
    datasources[0];

  const ordersTable = tables.find(
    (item) =>
      item.tableCode === TUTORIAL.tableCode &&
      (!datasource || item.datasourceId === datasource.id),
  );

  let ordersFields: MetaTableField[] = [];
  if (ordersTable?.id) {
    ordersFields = await metadataApi.listFields(ordersTable.id);
  }

  const deptDimension =
    dimensions.find((item) => item.code === TUTORIAL.deptDimCode) ??
    dimensions.find((item) => item.code === 'dept');
  const revenueMetric =
    metrics.find((item) => item.code === TUTORIAL.revenueCode) ??
    metrics.find((item) => item.code === 'revenue');

  return {
    datasourceId: datasource?.id,
    ordersTable,
    ordersFields,
    deptDimension,
    revenueMetric,
  };
}

export function buildDatasourceDemo(): DataSourceRequest {
  return {
    id: TUTORIAL.datasourceId,
    name: '教程演示库',
    jdbcUrl: H2_DEMO_JDBC_URL,
    username: 'sa',
    password: '',
    dbType: 'H2',
    enabled: true,
  };
}

export async function prepareDatasourceDemo(): Promise<DemoFillOutcome> {
  const ctx = await loadTutorialContext();
  if (ctx.datasourceId) {
    return {
      type: 'skip',
      message: `已有数据源 ${ctx.datasourceId}，可直接进行下一步`,
    };
  }
  return {
    type: 'form',
    values: buildDatasourceDemo(),
    hint: '已填入 H2 演示库连接信息，保存后即可在元数据中选择该数据源',
  };
}

export async function prepareMetadataDemo(): Promise<DemoFillOutcome> {
  const ctx = await loadTutorialContext();
  if (!ctx.datasourceId) {
    return { type: 'skip', message: '请先完成数据源配置' };
  }
  if (ctx.ordersTable?.id) {
    const missing = ORDER_FIELDS.filter(
      (field) => !ctx.ordersFields.some((existing) => existing.fieldCode === field.fieldCode),
    );
    if (missing.length === 0) {
      return {
        type: 'skip',
        message: `元数据表 ${TUTORIAL.tableCode} 及字段已就绪`,
      };
    }
    return {
      type: 'chain',
      chain: {
        kind: 'metadata-fields',
        tableId: ctx.ordersTable.id,
        fields: missing,
        index: 0,
      },
    };
  }
  return {
    type: 'form',
    values: {
      tableCode: TUTORIAL.tableCode,
      tableName: '订单事实表（教程）',
      catalogCode: 'finance',
      datasourceId: ctx.datasourceId,
      comments: '教程演示：映射物理表 orders',
    },
    hint: '保存表后将继续引导添加字段',
  };
}

export function buildMetadataFieldDemo(field: MetaTableField): MetaTableField {
  return { ...field };
}

export async function prepareDimensionsDemo(): Promise<DemoFillOutcome> {
  const ctx = await loadTutorialContext();
  if (!ctx.datasourceId) {
    return { type: 'skip', message: '请先完成数据源与元数据配置' };
  }
  if (ctx.deptDimension?.id) {
    const values = await dimensionApi.listValues(ctx.deptDimension.id);
    const missing = DEPT_VALUES.filter(
      (item) => !values.some((existing) => existing.code === item.code),
    );
    if (missing.length === 0) {
      return { type: 'skip', message: '教程维度及维度值已就绪' };
    }
    return {
      type: 'chain',
      chain: {
        kind: 'dimension-values',
        dimensionId: ctx.deptDimension.id,
        values: missing,
        index: 0,
      },
    };
  }
  return {
    type: 'form',
    values: {
      catalogCode: 'finance',
      code: TUTORIAL.deptDimCode,
      name: '部门（教程）',
      type: 'LIST',
      valueSource: 'MANUAL',
      datasourceId: ctx.datasourceId,
      comments: '教程演示维度，保存后继续添加维度值',
    },
    hint: '保存维度后将继续引导添加部门维度值',
  };
}

export function buildDimensionValueDemo(value: DimensionValue): DimensionValue {
  return { ...value };
}

export async function prepareMetricsDemo(): Promise<DemoFillOutcome> {
  const ctx = await loadTutorialContext();
  if (!ctx.datasourceId) {
    return { type: 'skip', message: '请先完成前置步骤' };
  }
  if (ctx.revenueMetric) {
    return { type: 'skip', message: '教程指标 learn_revenue 已存在' };
  }
  if (!ctx.deptDimension) {
    return { type: 'skip', message: '请先创建教程维度 learn_dept' };
  }
  const deptCode = ctx.deptDimension.code;
  return {
    type: 'form',
    values: {
      code: TUTORIAL.revenueCode,
      name: '教程营业收入',
      catalogCode: 'finance',
      type: 'TABLE',
      datasourceId: ctx.datasourceId,
      modelCode: 'learn_model',
      tableCode: TUTORIAL.tableCode,
      fieldCode: 'amount',
      aggregation: 'SUM',
      alias: TUTORIAL.revenueCode,
      description: '教程演示指标：按部门汇总订单金额',
      dimensions: [
        {
          dimensionCode: deptCode,
          fieldCode: 'dept_code',
          fieldName: '部门',
          sort: 1,
        },
      ],
    } satisfies MetricDefinition,
    hint: '已绑定教程维度 learn_dept，保存后可 SQL 预览验证',
  };
}

export async function prepareWarningDemo(): Promise<DemoFillOutcome> {
  const ctx = await loadTutorialContext();
  const [rules] = await Promise.all([warningApi.list()]);
  if (rules.some((item) => item.code === TUTORIAL.warningCode || item.code === 'low_profit')) {
    return { type: 'skip', message: '教程预警规则已存在' };
  }
  const metricCode = ctx.revenueMetric?.code;
  if (!metricCode) {
    return { type: 'skip', message: '请先创建教程指标 learn_revenue' };
  }
  const values: WarningRule = {
    name: '教程营收过低预警',
    code: TUTORIAL.warningCode,
    catalogCode: 'finance',
    metricCodes: [metricCode],
    expression: `${metricCode} < 1500`,
    warningLevel: 2,
    enabled: true,
    comments: '教程演示：当营收低于 1500 时触发预警',
  };
  return {
    type: 'form',
    values,
    hint: '保存后可使用数据预览验证预警命中情况',
  };
}

export async function prepareDemoForStep(stepId: string): Promise<DemoFillOutcome> {
  switch (stepId) {
    case 'datasources':
      return prepareDatasourceDemo();
    case 'metadata':
      return prepareMetadataDemo();
    case 'dimensions':
      return prepareDimensionsDemo();
    case 'metrics':
      return prepareMetricsDemo();
    case 'warning-rules':
      return prepareWarningDemo();
    default:
      return { type: 'skip', message: '未知步骤' };
  }
}

export function getYearGenerateDemo(): TimeValueGenerateRequest {
  const year = new Date().getFullYear();
  return {
    granularity: 'YEAR',
    startYear: year - 1,
    endYear: year + 1,
    codeFormat: 'YYYY',
    nameFormat: 'YYYY年',
    skipExisting: true,
  };
}
