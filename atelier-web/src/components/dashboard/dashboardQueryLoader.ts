import { datasourceApi } from '../../api/datasource';
import type { DashboardWidgetDataSource, QueryResult } from '../../api/types';

export async function loadDashboardQueryData(
  dataSource: DashboardWidgetDataSource | undefined,
): Promise<QueryResult> {
  if (!dataSource?.datasourceId) {
    throw new Error('请选择数据源');
  }
  const pageSize = dataSource.pageSize ?? 20;
  const mode = dataSource.queryMode ?? 'SQL';

  if (mode === 'TABLE') {
    if (!dataSource.tableName) {
      throw new Error('请选择数据表');
    }
    return datasourceApi.browsePreview(
      dataSource.datasourceId,
      dataSource.tableName,
      dataSource.schema,
      1,
      pageSize,
    );
  }

  const sql = dataSource.sql?.trim();
  if (!sql) {
    throw new Error('请填写 SELECT 语句');
  }
  return datasourceApi.browseExecuteSql(dataSource.datasourceId, {
    sql,
    pageIndex: 1,
    pageSize,
  });
}
