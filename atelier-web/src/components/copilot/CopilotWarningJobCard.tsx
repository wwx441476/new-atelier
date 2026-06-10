import { useEffect, useState } from 'react';
import { Button, Tag } from 'antd';
import type { CopilotWarningJobResult } from '../../api/types';
import { warningApi } from '../../api/warning';
import { useWarningJob } from '../../warning/WarningJobContext';
import { subscribeWarningJob } from '../../utils/warningJobEvents';
import { formatWarningJobSummary } from '../../utils/warningJobSummary';

interface CopilotWarningJobCardProps {
  data: CopilotWarningJobResult;
  planned?: boolean;
}

export default function CopilotWarningJobCard({ data, planned = false }: CopilotWarningJobCardProps) {
  const { openJobResult } = useWarningJob();
  const [status, setStatus] = useState(data.status);
  const [matchedCount, setMatchedCount] = useState<number | undefined>();
  const [total, setTotal] = useState<number | undefined>();
  const [pageIndex, setPageIndex] = useState(data.pageIndex);
  const [pageSize, setPageSize] = useState(data.pageSize);

  useEffect(() => {
    if (planned || !data.jobId) {
      return;
    }
    const es = subscribeWarningJob(data.jobId, {
      onProgress: (payload) => {
        if (payload.status) {
          setStatus(payload.status);
        }
      },
      onCompleted: (payload) => {
        setStatus('SUCCESS');
        setTotal(payload.total);
        setMatchedCount(payload.matchedCount);
        if (payload.pageIndex != null) {
          setPageIndex(payload.pageIndex);
        }
        if (payload.pageSize != null) {
          setPageSize(payload.pageSize);
        }
      },
      onFailed: () => {
        setStatus('FAILED');
      },
    });
    warningApi.getJob(data.jobId).then((job) => {
      if (job.status) {
        setStatus(job.status);
      }
      if (job.total != null) {
        setTotal(job.total);
      }
      if (job.matchedCount != null) {
        setMatchedCount(job.matchedCount);
      }
    });
    return () => es.close();
  }, [data.jobId, planned]);

  if (planned) {
    return (
      <div className="copilot-query-result planned">
        <div className="copilot-query-meta">
          仅规划 · 将异步执行预警规则 {data.ruleCode || data.ruleId || '—'}
        </div>
      </div>
    );
  }

  const statusColor =
    status === 'SUCCESS' ? 'success' : status === 'FAILED' ? 'error' : 'processing';

  return (
    <div className="copilot-query-result">
      <div className="copilot-query-meta" style={{ marginBottom: 8 }}>
        任务 {data.jobId} · <Tag color={statusColor}>{status}</Tag>
        {data.ruleName && <span> · {data.ruleName}</span>}
      </div>
      {status === 'SUCCESS' && (
        <div className="copilot-query-meta" style={{ marginBottom: 8 }}>
          {formatWarningJobSummary({ total, matchedCount, pageIndex, pageSize })}
        </div>
      )}
      <Button
        type="link"
        size="small"
        style={{ padding: 0 }}
        disabled={status !== 'SUCCESS'}
        onClick={() => openJobResult(data.jobId, { matchedOnly: true })}
      >
        查看命中数据
      </Button>
    </div>
  );
}
