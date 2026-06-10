import type { SqlExecuteResult } from '../../api/types';

interface CopilotWriteResultProps {
  data: SqlExecuteResult;
  planned?: boolean;
}

export default function CopilotWriteResult({ data, planned = false }: CopilotWriteResultProps) {
  return (
    <div className={`copilot-write-result${planned ? ' planned' : ''}`}>
      {data.sql && (
        <pre className="copilot-code-block copilot-query-sql">
          <code>{data.sql}</code>
        </pre>
      )}
      <div className="copilot-query-meta">
        {planned ? (
          <>仅规划 · 取消勾选「仅规划」后将执行</>
        ) : (
          <>
            {data.statementType}
            {data.affectedRows > 0 ? ` · 影响 ${data.affectedRows} 行` : ''}
          </>
        )}
      </div>
    </div>
  );
}
