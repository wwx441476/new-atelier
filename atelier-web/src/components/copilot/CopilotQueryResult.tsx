import { useMemo, useState } from 'react';
import { datasourceApi } from '../../api/datasource';
import type { CopilotSqlQueryResult } from '../../api/types';

interface CopilotQueryResultProps {
  data: CopilotSqlQueryResult;
  planned?: boolean;
}

function formatCell(value: unknown) {
  if (value == null) {
    return '';
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

export default function CopilotQueryResult({ data, planned = false }: CopilotQueryResultProps) {
  const [result, setResult] = useState(data);
  const [loading, setLoading] = useState(false);

  const columns = useMemo(() => {
    const keys = result.rows?.length
      ? Object.keys(result.rows[0])
      : Object.keys(result.headers || {});
    return keys.map((key) => ({
      key,
      title: result.headers?.[key] || key,
    }));
  }, [result]);

  const loadPage = async (pageIndex: number, pageSize: number) => {
    if (!result.sql?.trim()) {
      return;
    }
    setLoading(true);
    try {
      const next = await datasourceApi.browseExecuteSql(result.datasourceId, {
        sql: result.sql,
        pageIndex,
        pageSize,
      });
      setResult({
        datasourceId: result.datasourceId,
        pageIndex,
        pageSize,
        total: next.total,
        rows: next.rows,
        headers: next.headers,
        sql: next.sql || result.sql,
      });
    } finally {
      setLoading(false);
    }
  };

  const totalPages = Math.max(1, Math.ceil(result.total / result.pageSize));

  if (planned) {
    return (
      <div className="copilot-query-result planned">
        {result.sql ? (
          <pre className="copilot-code-block copilot-query-sql">
            <code>{result.sql}</code>
          </pre>
        ) : (
          <div className="copilot-query-empty">未生成 SQL</div>
        )}
        <div className="copilot-query-meta">
          仅规划 · 数据源 {result.datasourceId || '—'} · 取消勾选「仅规划」后将执行并展示结果
        </div>
      </div>
    );
  }

  return (
    <div className={`copilot-query-result${loading ? ' loading' : ''}`}>
      {result.sql && (
        <pre className="copilot-code-block copilot-query-sql">
          <code>{result.sql}</code>
        </pre>
      )}
      <div className="copilot-query-meta">
        共 {result.total} 条 · 第 {result.pageIndex}/{totalPages} 页
      </div>
      <div className="copilot-table-wrap">
        <table className="copilot-table copilot-query-table">
          <thead>
            <tr>
              {columns.map((column) => (
                <th key={column.key}>{column.title}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {result.rows.length === 0 ? (
              <tr>
                <td colSpan={Math.max(columns.length, 1)} className="copilot-query-empty">
                  暂无数据
                </td>
              </tr>
            ) : (
              result.rows.map((row, rowIndex) => (
                <tr key={rowIndex}>
                  {columns.map((column) => (
                    <td key={column.key} title={formatCell(row[column.key])}>
                      {formatCell(row[column.key])}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      {result.total > result.pageSize && (
        <div className="copilot-query-pagination">
          <button
            type="button"
            className="copilot-query-page-btn"
            disabled={loading || result.pageIndex <= 1}
            onClick={() => loadPage(result.pageIndex - 1, result.pageSize)}
          >
            上一页
          </button>
          <button
            type="button"
            className="copilot-query-page-btn"
            disabled={loading || result.pageIndex >= totalPages}
            onClick={() => loadPage(result.pageIndex + 1, result.pageSize)}
          >
            下一页
          </button>
        </div>
      )}
    </div>
  );
}
