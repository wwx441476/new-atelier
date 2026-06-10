import type { CopilotWarningHitResult as CopilotWarningHitResultType } from '../../api/types';
import { formatWarningJobSummary } from '../../utils/warningJobSummary';
import WarningHitTable from '../WarningHitTable';

interface CopilotWarningHitResultProps {
  data: CopilotWarningHitResultType;
  planned?: boolean;
}

export default function CopilotWarningHitResult({ data, planned = false }: CopilotWarningHitResultProps) {
  if (planned) {
    return (
      <div className="copilot-query-result planned">
        <div className="copilot-query-meta">仅规划 · 将获取任务 {data.jobId || '—'} 的命中行</div>
      </div>
    );
  }

  return (
    <div className="copilot-query-result">
      <div className="copilot-query-meta" style={{ marginBottom: 8 }}>
        {formatWarningJobSummary({
          total: data.total,
          matchedCount: data.pageMatchedCount,
          pageIndex: data.pageIndex,
          pageSize: data.pageSize,
        })}
        {data.matchedRows.length > 0 && (
          <span> · 下方展示 {data.matchedRows.length} 条命中行</span>
        )}
      </div>
      <WarningHitTable rows={data.matchedRows} headers={data.headers} />
    </div>
  );
}
